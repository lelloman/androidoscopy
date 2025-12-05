//! Mock Dashboard client for E2E testing.
//!
//! This module simulates the behavior of the web dashboard
//! for testing the server without a real browser.

use futures_util::{SinkExt, StreamExt};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::sync::Arc;
use tokio::sync::{mpsc, Mutex};
use tokio_tungstenite::{connect_async, tungstenite::Message};

/// Messages sent from the dashboard to the server.
#[derive(Debug, Clone, Serialize)]
#[serde(tag = "type")]
pub enum DashboardMessage {
    #[serde(rename = "ACTION")]
    Action {
        session_id: String,
        payload: ActionPayload,
    },
}

#[derive(Debug, Clone, Serialize)]
pub struct ActionPayload {
    pub action_id: String,
    pub action: String,
    pub args: Option<Value>,
}

/// Messages received from the server.
#[derive(Debug, Clone, Deserialize)]
#[serde(tag = "type")]
pub enum ServerMessage {
    #[serde(rename = "SYNC")]
    Sync { payload: SyncPayload },
    #[serde(rename = "SESSION_STARTED")]
    SessionStarted { payload: SessionInfo },
    #[serde(rename = "SESSION_DATA")]
    SessionData {
        session_id: String,
        payload: Value,
        #[allow(dead_code)]
        timestamp: String,
    },
    #[serde(rename = "SESSION_LOG")]
    SessionLog {
        session_id: String,
        payload: LogPayload,
        #[allow(dead_code)]
        timestamp: String,
    },
    #[serde(rename = "SESSION_ENDED")]
    SessionEnded {
        session_id: String,
        #[allow(dead_code)]
        timestamp: String,
    },
    #[serde(rename = "ACTION_RESULT")]
    ActionResult {
        session_id: String,
        payload: ActionResultPayload,
    },
}

#[derive(Debug, Clone, Deserialize)]
pub struct SyncPayload {
    pub sessions: Vec<SessionInfo>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct SessionInfo {
    pub session_id: String,
    pub app_name: String,
    pub package_name: String,
    #[allow(dead_code)]
    pub version_name: String,
    #[allow(dead_code)]
    pub device: DeviceInfo,
    #[allow(dead_code)]
    pub dashboard: Value,
    #[allow(dead_code)]
    pub started_at: String,
    #[allow(dead_code)]
    pub latest_data: Option<Value>,
    #[allow(dead_code)]
    pub recent_logs: Vec<LogEntry>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct DeviceInfo {
    #[allow(dead_code)]
    pub device_id: String,
    #[allow(dead_code)]
    pub manufacturer: String,
    #[allow(dead_code)]
    pub model: String,
    #[allow(dead_code)]
    pub android_version: String,
    #[allow(dead_code)]
    pub api_level: i32,
    #[allow(dead_code)]
    pub is_emulator: bool,
}

#[derive(Debug, Clone, Deserialize)]
pub struct LogPayload {
    pub level: String,
    pub tag: Option<String>,
    pub message: String,
    #[allow(dead_code)]
    pub throwable: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct LogEntry {
    #[allow(dead_code)]
    pub timestamp: String,
    pub level: String,
    pub tag: Option<String>,
    pub message: String,
    #[allow(dead_code)]
    pub throwable: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ActionResultPayload {
    pub action_id: String,
    pub success: bool,
    pub message: Option<String>,
    #[allow(dead_code)]
    pub data: Option<Value>,
}

/// A mock dashboard client that simulates the web dashboard behavior.
pub struct MockDashboardClient {
    sender: mpsc::Sender<DashboardMessage>,
    received_messages: Arc<Mutex<Vec<ServerMessage>>>,
    connected: Arc<Mutex<bool>>,
}

impl MockDashboardClient {
    /// Creates a new mock dashboard client and connects to the server.
    pub async fn connect(
        server_url: &str,
    ) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        let (ws_stream, _) = connect_async(server_url).await?;
        let (mut write, mut read) = ws_stream.split();

        let (tx, mut rx) = mpsc::channel::<DashboardMessage>(100);
        let received_messages: Arc<Mutex<Vec<ServerMessage>>> = Arc::new(Mutex::new(Vec::new()));
        let connected = Arc::new(Mutex::new(true));

        let received_clone = received_messages.clone();
        let connected_clone = connected.clone();

        // Spawn reader task
        tokio::spawn(async move {
            while let Some(msg) = read.next().await {
                match msg {
                    Ok(Message::Text(text)) => {
                        if let Ok(server_msg) = serde_json::from_str::<ServerMessage>(&text) {
                            let mut messages = received_clone.lock().await;
                            messages.push(server_msg);
                        }
                    }
                    Ok(Message::Close(_)) => {
                        let mut conn = connected_clone.lock().await;
                        *conn = false;
                        break;
                    }
                    Err(_) => {
                        let mut conn = connected_clone.lock().await;
                        *conn = false;
                        break;
                    }
                    _ => {}
                }
            }
        });

        // Spawn writer task
        tokio::spawn(async move {
            while let Some(msg) = rx.recv().await {
                let text = serde_json::to_string(&msg).unwrap();
                if write.send(Message::Text(text)).await.is_err() {
                    break;
                }
            }
        });

        Ok(Self {
            sender: tx,
            received_messages,
            connected,
        })
    }

    /// Waits for the SYNC message and returns it.
    pub async fn wait_for_sync(&self, timeout_ms: u64) -> Option<SyncPayload> {
        let deadline = tokio::time::Instant::now() + tokio::time::Duration::from_millis(timeout_ms);

        loop {
            let messages = self.received_messages.lock().await;
            for msg in messages.iter() {
                if let ServerMessage::Sync { payload } = msg {
                    return Some(payload.clone());
                }
            }
            drop(messages);

            if tokio::time::Instant::now() >= deadline {
                return None;
            }
            tokio::time::sleep(tokio::time::Duration::from_millis(50)).await;
        }
    }

    /// Waits for a SESSION_STARTED message for a specific session.
    pub async fn wait_for_session_started(&self, timeout_ms: u64) -> Option<SessionInfo> {
        let deadline = tokio::time::Instant::now() + tokio::time::Duration::from_millis(timeout_ms);

        loop {
            let messages = self.received_messages.lock().await;
            for msg in messages.iter() {
                if let ServerMessage::SessionStarted { payload } = msg {
                    return Some(payload.clone());
                }
            }
            drop(messages);

            if tokio::time::Instant::now() >= deadline {
                return None;
            }
            tokio::time::sleep(tokio::time::Duration::from_millis(50)).await;
        }
    }

    /// Waits for a SESSION_DATA message for a specific session.
    pub async fn wait_for_session_data(&self, session_id: &str, timeout_ms: u64) -> Option<Value> {
        let deadline = tokio::time::Instant::now() + tokio::time::Duration::from_millis(timeout_ms);

        loop {
            let messages = self.received_messages.lock().await;
            for msg in messages.iter() {
                if let ServerMessage::SessionData {
                    session_id: sid,
                    payload,
                    ..
                } = msg
                {
                    if sid == session_id {
                        return Some(payload.clone());
                    }
                }
            }
            drop(messages);

            if tokio::time::Instant::now() >= deadline {
                return None;
            }
            tokio::time::sleep(tokio::time::Duration::from_millis(50)).await;
        }
    }

    /// Waits for a SESSION_LOG message for a specific session.
    pub async fn wait_for_session_log(
        &self,
        session_id: &str,
        timeout_ms: u64,
    ) -> Option<LogPayload> {
        let deadline = tokio::time::Instant::now() + tokio::time::Duration::from_millis(timeout_ms);

        loop {
            let messages = self.received_messages.lock().await;
            for msg in messages.iter() {
                if let ServerMessage::SessionLog {
                    session_id: sid,
                    payload,
                    ..
                } = msg
                {
                    if sid == session_id {
                        return Some(payload.clone());
                    }
                }
            }
            drop(messages);

            if tokio::time::Instant::now() >= deadline {
                return None;
            }
            tokio::time::sleep(tokio::time::Duration::from_millis(50)).await;
        }
    }

    /// Waits for a SESSION_ENDED message for a specific session.
    pub async fn wait_for_session_ended(&self, session_id: &str, timeout_ms: u64) -> bool {
        let deadline = tokio::time::Instant::now() + tokio::time::Duration::from_millis(timeout_ms);

        loop {
            let messages = self.received_messages.lock().await;
            for msg in messages.iter() {
                if let ServerMessage::SessionEnded {
                    session_id: sid, ..
                } = msg
                {
                    if sid == session_id {
                        return true;
                    }
                }
            }
            drop(messages);

            if tokio::time::Instant::now() >= deadline {
                return false;
            }
            tokio::time::sleep(tokio::time::Duration::from_millis(50)).await;
        }
    }

    /// Waits for an ACTION_RESULT message.
    pub async fn wait_for_action_result(
        &self,
        action_id: &str,
        timeout_ms: u64,
    ) -> Option<ActionResultPayload> {
        let deadline = tokio::time::Instant::now() + tokio::time::Duration::from_millis(timeout_ms);

        loop {
            let messages = self.received_messages.lock().await;
            for msg in messages.iter() {
                if let ServerMessage::ActionResult { payload, .. } = msg {
                    if payload.action_id == action_id {
                        return Some(payload.clone());
                    }
                }
            }
            drop(messages);

            if tokio::time::Instant::now() >= deadline {
                return None;
            }
            tokio::time::sleep(tokio::time::Duration::from_millis(50)).await;
        }
    }

    /// Sends an action to a session.
    pub async fn send_action(
        &self,
        session_id: &str,
        action: &str,
        args: Option<Value>,
    ) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        let action_id = uuid::Uuid::new_v4().to_string();

        let msg = DashboardMessage::Action {
            session_id: session_id.to_string(),
            payload: ActionPayload {
                action_id: action_id.clone(),
                action: action.to_string(),
                args,
            },
        };

        self.sender.send(msg).await?;
        Ok(action_id)
    }

    /// Gets all received messages.
    pub async fn received_messages(&self) -> Vec<ServerMessage> {
        self.received_messages.lock().await.clone()
    }

    /// Clears received messages.
    pub async fn clear_messages(&self) {
        let mut messages = self.received_messages.lock().await;
        messages.clear();
    }

    /// Checks if the client is connected.
    pub async fn is_connected(&self) -> bool {
        *self.connected.lock().await
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn test_action_message_serialization() {
        let msg = DashboardMessage::Action {
            session_id: "session-1".to_string(),
            payload: ActionPayload {
                action_id: "action-1".to_string(),
                action: "clear_cache".to_string(),
                args: Some(json!({"type": "all"})),
            },
        };

        let json = serde_json::to_string(&msg).unwrap();
        assert!(json.contains("\"type\":\"ACTION\""));
        assert!(json.contains("session-1"));
        assert!(json.contains("clear_cache"));
    }
}
