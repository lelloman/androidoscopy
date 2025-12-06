//! WebSocket handlers for app and dashboard connections.
//!
//! # Endpoints
//!
//! - `/ws/app` - Android app connections (handled by [`handle_app_ws`])
//! - `/ws/dashboard` - Web dashboard connections (handled by [`handle_dashboard_ws`])
//!
//! # Connection Lifecycle
//!
//! ## App Connection (`/ws/app`)
//! 1. Client connects
//! 2. Client sends REGISTER message
//! 3. Server responds with REGISTERED (includes session_id)
//! 4. Server notifies dashboards with SESSION_STARTED
//! 5. Client can now send DATA/LOG messages
//! 6. On disconnect, server sends SESSION_ENDED to dashboards
//!
//! ## Dashboard Connection (`/ws/dashboard`)
//! 1. Client connects
//! 2. Server immediately sends SYNC with all active sessions
//! 3. Server forwards SESSION_DATA/SESSION_LOG from apps
//! 4. Client can send ACTION messages to specific sessions

use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        State,
    },
    response::IntoResponse,
};
use chrono::Utc;
use futures::{SinkExt, StreamExt};
use tokio::sync::mpsc;
use tracing::{error, info, warn};

use crate::protocol::{
    ActionResultToDashboardPayload, AppMessage, DashboardToServiceMessage, LogEntry,
    RegisteredPayload, ServiceToAppMessage, ServiceToDashboardMessage, SessionDataPayload,
    SessionEndedPayload, SessionLogPayload, SessionStartedPayload, SyncPayload,
};
use crate::state::AppState;

/// Handles WebSocket upgrade for app connections.
pub async fn handle_app_ws(
    ws: WebSocketUpgrade,
    State(state): State<AppState>,
) -> impl IntoResponse {
    ws.on_upgrade(|socket| handle_app_connection(socket, state))
}

pub async fn handle_dashboard_ws(
    ws: WebSocketUpgrade,
    State(state): State<AppState>,
) -> impl IntoResponse {
    ws.on_upgrade(|socket| handle_dashboard_connection(socket, state))
}

async fn handle_app_connection(socket: WebSocket, state: AppState) {
    info!("App WebSocket connection established");

    let (mut sender, mut receiver) = socket.split();

    // Create channel for sending messages to this app
    let (tx, mut rx) = mpsc::channel::<ServiceToAppMessage>(32);

    // Wait for REGISTER message
    let session_id = loop {
        match receiver.next().await {
            Some(Ok(Message::Text(text))) => {
                match serde_json::from_str::<AppMessage>(&text) {
                    Ok(AppMessage::Register { payload, .. }) => {
                        // Validate the message
                        let mut manager = state.session_manager.lock().await;
                        let (session_id, resumed) = manager.create_session(payload, tx.clone());

                        // Send REGISTERED response
                        let response = ServiceToAppMessage::Registered {
                            timestamp: Utc::now(),
                            payload: RegisteredPayload {
                                session_id: session_id.clone(),
                            },
                        };

                        if let Ok(json) = serde_json::to_string(&response) {
                            if sender.send(Message::Text(json.into())).await.is_err() {
                                error!("Failed to send REGISTERED response");
                                return;
                            }
                        }

                        // Notify dashboards about session
                        if let Some(session) = manager.get_session(&session_id) {
                            if resumed {
                                // Send SESSION_RESUMED for reconnecting devices
                                let msg = ServiceToDashboardMessage::SessionResumed {
                                    payload: SessionStartedPayload {
                                        session: session.to_session_info(),
                                    },
                                };
                                broadcast_to_dashboards(&manager, msg).await;
                                info!("App resumed session_id: {}", session_id);
                            } else {
                                // Send SESSION_STARTED for new devices
                                let msg = ServiceToDashboardMessage::SessionStarted {
                                    payload: SessionStartedPayload {
                                        session: session.to_session_info(),
                                    },
                                };
                                broadcast_to_dashboards(&manager, msg).await;
                                info!("App registered with session_id: {}", session_id);
                            }
                        }

                        break session_id;
                    }
                    Ok(_) => {
                        warn!("Expected REGISTER message, got different message type");
                    }
                    Err(e) => {
                        warn!("Failed to parse message: {}", e);
                    }
                }
            }
            Some(Ok(Message::Close(_))) | None => {
                info!("App disconnected before registering");
                return;
            }
            Some(Ok(_)) => {
                // Ignore binary, ping, pong
            }
            Some(Err(e)) => {
                error!("WebSocket error: {}", e);
                return;
            }
        }
    };

    // Spawn task to forward messages from rx to WebSocket
    let session_id_clone = session_id.clone();
    let forward_task = tokio::spawn(async move {
        while let Some(msg) = rx.recv().await {
            if let Ok(json) = serde_json::to_string(&msg) {
                if sender.send(Message::Text(json.into())).await.is_err() {
                    break;
                }
            }
        }
    });

    // Main message loop
    while let Some(result) = receiver.next().await {
        match result {
            Ok(Message::Text(text)) => {
                match serde_json::from_str::<AppMessage>(&text) {
                    Ok(msg) => {
                        // Validate message
                        if let Err(e) = msg.validate() {
                            warn!("Message validation failed: {}", e);
                            continue;
                        }

                        match msg {
                            AppMessage::Data {
                                session_id: msg_session_id,
                                timestamp,
                                payload,
                            } => {
                                if msg_session_id != session_id_clone {
                                    warn!("Session ID mismatch in DATA message");
                                    continue;
                                }

                                let mut manager = state.session_manager.lock().await;
                                manager.add_data(&session_id_clone, timestamp, payload.clone());

                                // Forward to dashboards
                                let msg = ServiceToDashboardMessage::SessionData {
                                    timestamp,
                                    payload: SessionDataPayload {
                                        session_id: session_id_clone.clone(),
                                        data: payload,
                                    },
                                };
                                broadcast_to_dashboards(&manager, msg).await;
                            }
                            AppMessage::Log {
                                session_id: msg_session_id,
                                timestamp,
                                payload,
                            } => {
                                if msg_session_id != session_id_clone {
                                    warn!("Session ID mismatch in LOG message");
                                    continue;
                                }

                                let mut manager = state.session_manager.lock().await;
                                manager.add_log(&session_id_clone, timestamp, payload.clone());

                                // Forward to dashboards
                                let log_entry = LogEntry {
                                    timestamp,
                                    level: payload.level,
                                    tag: payload.tag,
                                    message: payload.message,
                                    throwable: payload.throwable,
                                };
                                let msg = ServiceToDashboardMessage::SessionLog {
                                    timestamp,
                                    payload: SessionLogPayload {
                                        session_id: session_id_clone.clone(),
                                        log: log_entry,
                                    },
                                };
                                broadcast_to_dashboards(&manager, msg).await;
                            }
                            AppMessage::ActionResult {
                                session_id: msg_session_id,
                                timestamp,
                                payload,
                            } => {
                                if msg_session_id != session_id_clone {
                                    warn!("Session ID mismatch in ACTION_RESULT message");
                                    continue;
                                }

                                let manager = state.session_manager.lock().await;

                                // Forward to dashboards
                                let msg = ServiceToDashboardMessage::ActionResult {
                                    timestamp,
                                    payload: ActionResultToDashboardPayload {
                                        session_id: session_id_clone.clone(),
                                        action_id: payload.action_id,
                                        success: payload.success,
                                        message: payload.message,
                                        data: payload.data,
                                    },
                                };
                                broadcast_to_dashboards(&manager, msg).await;
                            }
                            AppMessage::Register { .. } => {
                                warn!("Received duplicate REGISTER message");
                            }
                        }
                    }
                    Err(e) => {
                        warn!("Failed to parse app message: {}", e);
                    }
                }
            }
            Ok(Message::Close(_)) => {
                info!("App sent close frame");
                break;
            }
            Ok(_) => {
                // Ignore binary, ping, pong
            }
            Err(e) => {
                error!("WebSocket error: {}", e);
                break;
            }
        }
    }

    // Clean up
    forward_task.abort();

    // Mark session as ended
    {
        let mut manager = state.session_manager.lock().await;
        manager.end_session(&session_id);

        // Notify dashboards
        let msg = ServiceToDashboardMessage::SessionEnded {
            timestamp: Utc::now(),
            payload: SessionEndedPayload {
                session_id: session_id.clone(),
            },
        };
        broadcast_to_dashboards(&manager, msg).await;
    }

    info!("App disconnected, session {} ended", session_id);
}

async fn handle_dashboard_connection(socket: WebSocket, state: AppState) {
    info!("Dashboard WebSocket connection established");

    let (mut sender, mut receiver) = socket.split();

    // Create channel for sending messages to this dashboard
    let (tx, mut rx) = mpsc::channel::<ServiceToDashboardMessage>(64);

    // Register dashboard sender
    {
        let mut manager = state.session_manager.lock().await;
        manager.add_dashboard_sender(tx.clone());

        // Send SYNC with all active sessions
        let sessions: Vec<_> = manager.get_active_sessions().iter().map(|s| s.to_session_info()).collect();
        let sync_msg = ServiceToDashboardMessage::Sync {
            payload: SyncPayload { sessions },
        };

        if let Ok(json) = serde_json::to_string(&sync_msg) {
            if sender.send(Message::Text(json.into())).await.is_err() {
                error!("Failed to send SYNC message");
                manager.remove_dashboard_sender(&tx);
                return;
            }
        }
    }

    // Spawn task to forward messages from rx to WebSocket
    let forward_task = tokio::spawn(async move {
        while let Some(msg) = rx.recv().await {
            if let Ok(json) = serde_json::to_string(&msg) {
                if sender.send(Message::Text(json.into())).await.is_err() {
                    break;
                }
            }
        }
    });

    // Main message loop - handle incoming ACTION messages
    while let Some(result) = receiver.next().await {
        match result {
            Ok(Message::Text(text)) => {
                match serde_json::from_str::<DashboardToServiceMessage>(&text) {
                    Ok(DashboardToServiceMessage::Action { payload }) => {
                        // Forward ACTION to the appropriate app
                        let mut manager = state.session_manager.lock().await;
                        let session_id = &payload.session_id;

                        // Handle network_clear action - also clear server-side accumulated requests
                        if payload.action == "network_clear" {
                            if let Some(session) = manager.get_session_mut(session_id) {
                                session.clear_network_requests();
                            }
                        }

                        if let Some(session) = manager.get_session(session_id) {
                            if let Some(ref app_sender) = session.app_sender {
                                let action_payload = crate::protocol::ActionPayload {
                                    action_id: payload.action_id,
                                    action: payload.action,
                                    args: payload.args,
                                };
                                let action_msg = ServiceToAppMessage::Action {
                                    timestamp: Utc::now(),
                                    session_id: session_id.clone(),
                                    payload: action_payload,
                                };

                                if app_sender.send(action_msg).await.is_err() {
                                    warn!("Failed to send ACTION to app for session {}", session_id);
                                }
                            } else {
                                warn!("Session {} has no active app connection", session_id);
                            }
                        } else {
                            warn!("Session {} not found for ACTION", session_id);
                        }
                    }
                    Err(e) => {
                        warn!("Failed to parse dashboard message: {}", e);
                    }
                }
            }
            Ok(Message::Close(_)) => {
                info!("Dashboard sent close frame");
                break;
            }
            Ok(_) => {
                // Ignore binary, ping, pong
            }
            Err(e) => {
                error!("WebSocket error: {}", e);
                break;
            }
        }
    }

    // Clean up
    forward_task.abort();

    {
        let mut manager = state.session_manager.lock().await;
        manager.remove_dashboard_sender(&tx);
    }

    info!("Dashboard disconnected");
}

async fn broadcast_to_dashboards(
    manager: &crate::session::SessionManager,
    msg: ServiceToDashboardMessage,
) {
    for sender in manager.get_dashboard_senders() {
        if sender.send(msg.clone()).await.is_err() {
            // Sender is disconnected, will be cleaned up later
        }
    }
}
