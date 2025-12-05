import { writable, derived, get } from 'svelte/store';
import type {
    Session,
    ServiceToDashboardMessage,
    LogEntry,
    ActionMessage
} from '../types/protocol';

// Connection state
export const connected = writable(false);
export const connecting = writable(false);
export const error = writable<string | null>(null);

// Sessions
export const sessions = writable<Map<string, Session>>(new Map());

// Derived store for session list
export const sessionList = derived(sessions, ($sessions) =>
    Array.from($sessions.values())
);

// Active session count
export const activeSessionCount = derived(sessions, ($sessions) => {
    let count = 0;
    $sessions.forEach(session => {
        if (!session.ended_at) count++;
    });
    return count;
});

// WebSocket instance
let ws: WebSocket | null = null;
let reconnectTimeout: ReturnType<typeof setTimeout> | null = null;
const MAX_RECONNECT_DELAY = 30000;
let reconnectDelay = 1000;

// Pending action callbacks
const pendingActions = new Map<string, {
    resolve: (result: { success: boolean; message?: string; data?: Record<string, unknown> }) => void;
    reject: (error: Error) => void;
}>();

export function connect(url?: string) {
    if (get(connected) || get(connecting)) return;

    connecting.set(true);
    error.set(null);

    const wsUrl = url || `ws://${location.host}/ws/dashboard`;

    try {
        ws = new WebSocket(wsUrl);

        ws.onopen = () => {
            connected.set(true);
            connecting.set(false);
            error.set(null);
            reconnectDelay = 1000; // Reset reconnect delay on successful connection
        };

        ws.onmessage = (event) => {
            try {
                const message = JSON.parse(event.data) as ServiceToDashboardMessage;
                handleMessage(message);
            } catch (e) {
                console.error('Failed to parse message:', e);
            }
        };

        ws.onclose = () => {
            connected.set(false);
            connecting.set(false);
            ws = null;
            scheduleReconnect();
        };

        ws.onerror = (e) => {
            console.error('WebSocket error:', e);
            error.set('Connection error');
            connecting.set(false);
        };
    } catch (e) {
        connecting.set(false);
        error.set('Failed to connect');
        scheduleReconnect();
    }
}

export function disconnect() {
    if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
        reconnectTimeout = null;
    }
    if (ws) {
        ws.close();
        ws = null;
    }
    connected.set(false);
    connecting.set(false);
}

function scheduleReconnect() {
    if (reconnectTimeout) return;

    reconnectTimeout = setTimeout(() => {
        reconnectTimeout = null;
        connect();
        reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY);
    }, reconnectDelay);
}

function handleMessage(message: ServiceToDashboardMessage) {
    switch (message.type) {
        case 'SYNC':
            handleSync(message.payload.sessions);
            break;
        case 'SESSION_STARTED':
            handleSessionStarted(message.payload.session);
            break;
        case 'SESSION_DATA':
            handleSessionData(message.payload.session_id, message.payload.data);
            break;
        case 'SESSION_LOG':
            handleSessionLog(message.payload.session_id, message.payload.log);
            break;
        case 'SESSION_ENDED':
            handleSessionEnded(message.payload.session_id);
            break;
        case 'ACTION_RESULT':
            handleActionResult(message.payload);
            break;
    }
}

function handleSync(sessionsList: Session[]) {
    const newSessions = new Map<string, Session>();
    for (const session of sessionsList) {
        if (!session.recent_logs) {
            session.recent_logs = [];
        }
        newSessions.set(session.session_id, session);
    }
    sessions.set(newSessions);
}

function handleSessionStarted(session: Session) {
    if (!session.recent_logs) {
        session.recent_logs = [];
    }
    sessions.update(map => {
        map.set(session.session_id, session);
        return new Map(map);
    });
}

function handleSessionData(sessionId: string, data: Record<string, unknown>) {
    sessions.update(map => {
        const session = map.get(sessionId);
        if (session) {
            session.latest_data = {
                ...session.latest_data,
                ...data
            };
            map.set(sessionId, { ...session });
        }
        return new Map(map);
    });
}

function handleSessionLog(sessionId: string, log: LogEntry) {
    sessions.update(map => {
        const session = map.get(sessionId);
        if (session) {
            const logs = [...session.recent_logs, log];
            // Keep last 1000 logs
            if (logs.length > 1000) {
                logs.splice(0, logs.length - 1000);
            }
            session.recent_logs = logs;
            map.set(sessionId, { ...session });
        }
        return new Map(map);
    });
}

function handleSessionEnded(sessionId: string) {
    sessions.update(map => {
        const session = map.get(sessionId);
        if (session) {
            session.ended_at = new Date().toISOString();
            map.set(sessionId, { ...session });
        }
        return new Map(map);
    });
}

function handleActionResult(payload: {
    session_id: string;
    action_id: string;
    success: boolean;
    message?: string;
    data?: Record<string, unknown>;
}) {
    const pending = pendingActions.get(payload.action_id);
    if (pending) {
        pending.resolve({
            success: payload.success,
            message: payload.message,
            data: payload.data
        });
        pendingActions.delete(payload.action_id);
    }
}

export async function sendAction(
    sessionId: string,
    action: string,
    args?: Record<string, unknown>
): Promise<{ success: boolean; message?: string; data?: Record<string, unknown> }> {
    return new Promise((resolve, reject) => {
        if (!ws || ws.readyState !== WebSocket.OPEN) {
            reject(new Error('Not connected'));
            return;
        }

        const actionId = crypto.randomUUID();

        const message: ActionMessage = {
            type: 'ACTION',
            timestamp: new Date().toISOString(),
            payload: {
                session_id: sessionId,
                action_id: actionId,
                action,
                args
            }
        };

        // Set timeout for action
        const timeout = setTimeout(() => {
            pendingActions.delete(actionId);
            reject(new Error('Action timed out'));
        }, 30000);

        pendingActions.set(actionId, {
            resolve: (result) => {
                clearTimeout(timeout);
                resolve(result);
            },
            reject: (err) => {
                clearTimeout(timeout);
                reject(err);
            }
        });

        ws.send(JSON.stringify(message));
    });
}

// Remove ended sessions after a delay
export function removeSession(sessionId: string) {
    sessions.update(map => {
        map.delete(sessionId);
        return new Map(map);
    });
}
