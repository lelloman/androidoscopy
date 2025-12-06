// Device info from Android app
export interface DeviceInfo {
    device_id: string;
    manufacturer: string;
    model: string;
    android_version: string;
    api_level: number;
    is_emulator: boolean;
}

// Session data
export interface Session {
    session_id: string;
    app_name: string;
    package_name: string;
    version_name: string;
    device: DeviceInfo;
    dashboard: DashboardSchema;
    started_at: string;
    ended_at?: string;
    latest_data?: Record<string, unknown>;
    recent_logs: LogEntry[];
}

// Dashboard schema types
export interface DashboardSchema {
    sections: Section[];
}

export interface Section {
    id: string;
    title: string;
    layout?: 'row' | 'grid' | 'stack';
    columns?: number;
    collapsible?: boolean;
    collapsed_default?: boolean;
    widgets?: Widget[];
    widget?: Widget;
}

export type Widget =
    | NumberWidget
    | TextWidget
    | GaugeWidget
    | BadgeWidget
    | TableWidget
    | ButtonWidget
    | LogViewerWidget
    | ChartWidget;

export interface BaseWidget {
    type: string;
    label?: string;
    visible_when?: VisibleWhen;
}

export interface NumberWidget extends BaseWidget {
    type: 'number';
    label: string;
    data_path: string;
    format: 'number' | 'bytes' | 'percent' | 'duration';
}

export interface TextWidget extends BaseWidget {
    type: 'text';
    label: string;
    data_path: string;
}

export interface GaugeWidget extends BaseWidget {
    type: 'gauge';
    label: string;
    value_path: string;
    max_path: string;
    format: 'number' | 'bytes' | 'percent';
    thresholds?: Threshold[];
}

export interface Threshold {
    value: number;
    style: 'success' | 'warning' | 'danger';
}

export interface BadgeWidget extends BaseWidget {
    type: 'badge';
    label: string;
    data_path: string;
    variants: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'muted'>;
}

export interface TableWidget extends BaseWidget {
    type: 'table';
    data_path: string;
    columns: TableColumn[];
    row_actions?: RowAction[];
}

export interface TableColumn {
    key: string;
    label: string;
    format?: 'number' | 'bytes' | 'percent' | 'text' | 'duration';
}

export interface RowAction {
    id: string;
    label: string;
    args?: Record<string, string>;
}

export interface ButtonWidget extends BaseWidget {
    type: 'button';
    label: string;
    action: string;
    style?: 'primary' | 'secondary' | 'danger';
    args_dialog?: ArgsDialog;
    result_display?: ResultDisplay;
}

export interface ArgsDialog {
    title: string;
    fields: DialogField[];
}

export type DialogField =
    | TextDialogField
    | NumberDialogField
    | SelectDialogField
    | CheckboxDialogField;

export interface BaseDialogField {
    type: string;
    key: string;
    label: string;
}

export interface TextDialogField extends BaseDialogField {
    type: 'text';
    default?: string;
}

export interface NumberDialogField extends BaseDialogField {
    type: 'number';
    default?: number;
    min?: number;
    max?: number;
}

export interface SelectDialogField extends BaseDialogField {
    type: 'select';
    options: { value: string; label: string }[];
}

export interface CheckboxDialogField extends BaseDialogField {
    type: 'checkbox';
    default?: boolean;
}

export interface ResultDisplay {
    type: 'toast' | 'inline' | 'none';
}

export type LogLevel = 'VERBOSE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';

export interface LogViewerWidget extends BaseWidget {
    type: 'log_viewer';
    default_level?: LogLevel;
}

export interface ChartWidget extends BaseWidget {
    type: 'chart';
    label: string;
    data_path: string;
    format?: 'number' | 'bytes' | 'percent';
    max_points?: number;
    color?: string;
}

export interface VisibleWhen {
    path: string;
    operator: 'eq' | 'neq' | 'gt' | 'gte' | 'lt' | 'lte' | 'exists';
    value?: unknown;
}

// Log entry
export interface LogEntry {
    timestamp: string;
    level: 'VERBOSE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';
    tag?: string;
    message: string;
    throwable?: string;
}

// Messages from service to dashboard
export type ServiceToDashboardMessage =
    | SyncMessage
    | SessionStartedMessage
    | SessionResumedMessage
    | SessionDataMessage
    | SessionLogMessage
    | SessionEndedMessage
    | ActionResultMessage;

export interface SyncMessage {
    type: 'SYNC';
    timestamp: string;
    payload: {
        sessions: Session[];
    };
}

export interface SessionStartedMessage {
    type: 'SESSION_STARTED';
    timestamp: string;
    payload: {
        session: Session;
    };
}

export interface SessionResumedMessage {
    type: 'SESSION_RESUMED';
    timestamp: string;
    payload: {
        session: Session;
    };
}

export interface SessionDataMessage {
    type: 'SESSION_DATA';
    timestamp: string;
    payload: {
        session_id: string;
        data: Record<string, unknown>;
    };
}

export interface SessionLogMessage {
    type: 'SESSION_LOG';
    timestamp: string;
    payload: {
        session_id: string;
        log: LogEntry;
    };
}

export interface SessionEndedMessage {
    type: 'SESSION_ENDED';
    timestamp: string;
    payload: {
        session_id: string;
    };
}

export interface ActionResultMessage {
    type: 'ACTION_RESULT';
    timestamp: string;
    payload: {
        session_id: string;
        action_id: string;
        success: boolean;
        message?: string;
        data?: Record<string, unknown>;
    };
}

// Messages from dashboard to service
export interface ActionMessage {
    type: 'ACTION';
    timestamp: string;
    payload: {
        session_id: string;
        action_id: string;
        action: string;
        args?: Record<string, unknown>;
    };
}
