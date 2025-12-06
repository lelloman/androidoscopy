//! Protocol definitions for Androidoscopy WebSocket communication.
//!
//! This module defines the JSON message types exchanged between:
//! - Android apps and the service (via `/ws/app`)
//! - Web dashboards and the service (via `/ws/dashboard`)
//!
//! # Message Flow
//!
//! ```text
//! App → Service:
//!   REGISTER → REGISTERED (response)
//!   DATA → (forwarded to dashboards as SESSION_DATA)
//!   LOG → (forwarded to dashboards as SESSION_LOG)
//!   ACTION_RESULT → (forwarded to dashboards)
//!
//! Service → App:
//!   REGISTERED (in response to REGISTER)
//!   ACTION (forwarded from dashboard)
//!   ERROR (on protocol errors)
//!
//! Dashboard ↔ Service:
//!   ← SYNC (on connect, lists active sessions)
//!   ← SESSION_STARTED (when app registers)
//!   ← SESSION_DATA (when app sends data)
//!   ← SESSION_LOG (when app sends log)
//!   ← SESSION_ENDED (when app disconnects)
//!   → ACTION (sent to specific session)
//!   ← ACTION_RESULT (response from app)
//! ```

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use serde_json::Value;

// === App → Service Messages ===

/// Messages sent from Android apps to the service.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum AppMessage {
    #[serde(rename = "REGISTER")]
    Register {
        timestamp: DateTime<Utc>,
        payload: RegisterPayload,
    },
    #[serde(rename = "DATA")]
    Data {
        timestamp: DateTime<Utc>,
        session_id: String,
        payload: Value,
    },
    #[serde(rename = "LOG")]
    Log {
        timestamp: DateTime<Utc>,
        session_id: String,
        payload: LogPayload,
    },
    #[serde(rename = "ACTION_RESULT")]
    ActionResult {
        timestamp: DateTime<Utc>,
        session_id: String,
        payload: ActionResultPayload,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RegisterPayload {
    pub protocol_version: String,
    pub app_name: String,
    pub package_name: String,
    pub version_name: String,
    pub device: DeviceInfo,
    pub dashboard: Value,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceInfo {
    pub device_id: String,
    pub manufacturer: String,
    pub model: String,
    pub android_version: String,
    pub api_level: i32,
    pub is_emulator: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LogPayload {
    pub level: LogLevel,
    pub tag: Option<String>,
    pub message: String,
    pub throwable: Option<String>,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "UPPERCASE")]
pub enum LogLevel {
    Verbose,
    Debug,
    Info,
    Warn,
    Error,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActionResultPayload {
    pub action_id: String,
    pub success: bool,
    pub message: Option<String>,
    pub data: Option<Value>,
}

// === Service → App Messages ===

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum ServiceToAppMessage {
    #[serde(rename = "REGISTERED")]
    Registered {
        timestamp: DateTime<Utc>,
        payload: RegisteredPayload,
    },
    #[serde(rename = "ACTION")]
    Action {
        timestamp: DateTime<Utc>,
        session_id: String,
        payload: ActionPayload,
    },
    #[serde(rename = "ERROR")]
    Error {
        timestamp: DateTime<Utc>,
        payload: ErrorPayload,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RegisteredPayload {
    pub session_id: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActionPayload {
    pub action_id: String,
    pub action: String,
    pub args: Option<Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ErrorPayload {
    pub code: String,
    pub message: String,
}

// === Dashboard ↔ Service Messages ===

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum ServiceToDashboardMessage {
    #[serde(rename = "SYNC")]
    Sync { payload: SyncPayload },
    #[serde(rename = "SESSION_STARTED")]
    SessionStarted { payload: SessionStartedPayload },
    #[serde(rename = "SESSION_RESUMED")]
    SessionResumed { payload: SessionStartedPayload },
    #[serde(rename = "SESSION_DATA")]
    SessionData {
        timestamp: DateTime<Utc>,
        payload: SessionDataPayload,
    },
    #[serde(rename = "SESSION_LOG")]
    SessionLog {
        timestamp: DateTime<Utc>,
        payload: SessionLogPayload,
    },
    #[serde(rename = "SESSION_ENDED")]
    SessionEnded {
        timestamp: DateTime<Utc>,
        payload: SessionEndedPayload,
    },
    #[serde(rename = "ACTION_RESULT")]
    ActionResult {
        timestamp: DateTime<Utc>,
        payload: ActionResultToDashboardPayload,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SessionStartedPayload {
    pub session: SessionInfo,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SessionDataPayload {
    pub session_id: String,
    pub data: Value,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SessionLogPayload {
    pub session_id: String,
    pub log: LogEntry,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SessionEndedPayload {
    pub session_id: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActionResultToDashboardPayload {
    pub session_id: String,
    pub action_id: String,
    pub success: bool,
    pub message: Option<String>,
    pub data: Option<Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncPayload {
    pub sessions: Vec<SessionInfo>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SessionInfo {
    pub session_id: String,
    pub app_name: String,
    pub package_name: String,
    pub version_name: String,
    pub device: DeviceInfo,
    pub dashboard: Value,
    pub started_at: DateTime<Utc>,
    pub latest_data: Option<Value>,
    pub recent_logs: Vec<LogEntry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LogEntry {
    pub timestamp: DateTime<Utc>,
    pub level: LogLevel,
    pub tag: Option<String>,
    pub message: String,
    pub throwable: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum DashboardToServiceMessage {
    #[serde(rename = "ACTION")]
    Action {
        payload: DashboardActionPayload,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DashboardActionPayload {
    pub session_id: String,
    pub action_id: String,
    pub action: String,
    pub args: Option<Value>,
}

// === Message Size Limits ===

pub const MAX_MESSAGE_SIZE: usize = 1024 * 1024; // 1 MB
pub const MAX_LOG_MESSAGE_SIZE: usize = 64 * 1024; // 64 KB
pub const MAX_LOG_THROWABLE_SIZE: usize = 256 * 1024; // 256 KB

// === Validation ===

impl AppMessage {
    pub fn validate(&self) -> Result<(), ValidationError> {
        match self {
            AppMessage::Log { payload, .. } => {
                if payload.message.len() > MAX_LOG_MESSAGE_SIZE {
                    return Err(ValidationError::LogMessageTooLarge);
                }
                if let Some(ref throwable) = payload.throwable {
                    if throwable.len() > MAX_LOG_THROWABLE_SIZE {
                        return Err(ValidationError::LogThrowableTooLarge);
                    }
                }
                Ok(())
            }
            _ => Ok(()),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ValidationError {
    LogMessageTooLarge,
    LogThrowableTooLarge,
}

impl std::fmt::Display for ValidationError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ValidationError::LogMessageTooLarge => {
                write!(f, "Log message exceeds maximum size of {} bytes", MAX_LOG_MESSAGE_SIZE)
            }
            ValidationError::LogThrowableTooLarge => {
                write!(f, "Log throwable exceeds maximum size of {} bytes", MAX_LOG_THROWABLE_SIZE)
            }
        }
    }
}

impl std::error::Error for ValidationError {}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn test_parse_register_message() {
        let json = json!({
            "type": "REGISTER",
            "timestamp": "2024-12-02T14:30:00.000Z",
            "payload": {
                "protocol_version": "1.0",
                "app_name": "TestApp",
                "package_name": "com.test.app",
                "version_name": "1.0.0",
                "device": {
                    "device_id": "abc123",
                    "manufacturer": "Google",
                    "model": "Pixel 5",
                    "android_version": "13",
                    "api_level": 33,
                    "is_emulator": false
                },
                "dashboard": {
                    "sections": []
                }
            }
        });

        let msg: AppMessage = serde_json::from_value(json).unwrap();
        match msg {
            AppMessage::Register { payload, .. } => {
                assert_eq!(payload.app_name, "TestApp");
                assert_eq!(payload.package_name, "com.test.app");
            }
            _ => panic!("Expected Register message"),
        }
    }

    #[test]
    fn test_parse_data_message() {
        let json = json!({
            "type": "DATA",
            "timestamp": "2024-12-02T14:30:00.000Z",
            "session_id": "session-123",
            "payload": {
                "memory": {
                    "heap_used_bytes": 1000000,
                    "heap_max_bytes": 5000000
                }
            }
        });

        let msg: AppMessage = serde_json::from_value(json).unwrap();
        match msg {
            AppMessage::Data { session_id, payload, .. } => {
                assert_eq!(session_id, "session-123");
                assert!(payload.get("memory").is_some());
            }
            _ => panic!("Expected Data message"),
        }
    }

    #[test]
    fn test_parse_log_message() {
        let json = json!({
            "type": "LOG",
            "timestamp": "2024-12-02T14:30:00.000Z",
            "session_id": "session-123",
            "payload": {
                "level": "ERROR",
                "tag": "NetworkClient",
                "message": "Connection failed",
                "throwable": "java.io.IOException"
            }
        });

        let msg: AppMessage = serde_json::from_value(json).unwrap();
        match msg {
            AppMessage::Log { payload, .. } => {
                assert_eq!(payload.level, LogLevel::Error);
                assert_eq!(payload.tag, Some("NetworkClient".to_string()));
            }
            _ => panic!("Expected Log message"),
        }
    }

    #[test]
    fn test_validate_log_message_too_large() {
        let large_message = "x".repeat(MAX_LOG_MESSAGE_SIZE + 1);
        let msg = AppMessage::Log {
            timestamp: Utc::now(),
            session_id: "session-123".to_string(),
            payload: LogPayload {
                level: LogLevel::Error,
                tag: None,
                message: large_message,
                throwable: None,
            },
        };

        assert_eq!(msg.validate(), Err(ValidationError::LogMessageTooLarge));
    }

    #[test]
    fn test_validate_log_throwable_too_large() {
        let large_throwable = "x".repeat(MAX_LOG_THROWABLE_SIZE + 1);
        let msg = AppMessage::Log {
            timestamp: Utc::now(),
            session_id: "session-123".to_string(),
            payload: LogPayload {
                level: LogLevel::Error,
                tag: None,
                message: "Error".to_string(),
                throwable: Some(large_throwable),
            },
        };

        assert_eq!(msg.validate(), Err(ValidationError::LogThrowableTooLarge));
    }

    #[test]
    fn test_serialize_registered_message() {
        let msg = ServiceToAppMessage::Registered {
            timestamp: Utc::now(),
            payload: RegisteredPayload {
                session_id: "session-456".to_string(),
            },
        };

        let json = serde_json::to_value(&msg).unwrap();
        assert_eq!(json["type"], "REGISTERED");
        assert_eq!(json["payload"]["session_id"], "session-456");
    }

    #[test]
    fn test_serialize_sync_message() {
        let msg = ServiceToDashboardMessage::Sync {
            payload: SyncPayload {
                sessions: vec![],
            },
        };

        let json = serde_json::to_value(&msg).unwrap();
        assert_eq!(json["type"], "SYNC");
        assert!(json["payload"]["sessions"].as_array().unwrap().is_empty());
    }

    #[test]
    fn test_parse_dashboard_action() {
        let json = json!({
            "type": "ACTION",
            "payload": {
                "session_id": "session-123",
                "action_id": "act-456",
                "action": "clear_cache",
                "args": {
                    "cache_type": "image"
                }
            }
        });

        let msg: DashboardToServiceMessage = serde_json::from_value(json).unwrap();
        match msg {
            DashboardToServiceMessage::Action { payload } => {
                assert_eq!(payload.session_id, "session-123");
                assert_eq!(payload.action, "clear_cache");
            }
        }
    }
}
