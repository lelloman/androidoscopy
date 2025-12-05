//! Mock Android app client for E2E testing.
//!
//! This module simulates the behavior of the Android SDK
//! for testing the server and dashboard without a real Android device.

use futures_util::{SinkExt, StreamExt};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::sync::Arc;
use tokio::sync::{mpsc, Mutex};
use tokio_tungstenite::{connect_async, tungstenite::Message};

/// Messages sent from the mock app to the server.
#[derive(Debug, Clone, Serialize)]
#[serde(tag = "type")]
pub enum AppMessage {
    #[serde(rename = "REGISTER")]
    Register {
        timestamp: String,
        payload: RegisterPayload,
    },
    #[serde(rename = "DATA")]
    Data {
        timestamp: String,
        session_id: String,
        payload: Value,
    },
    #[serde(rename = "LOG")]
    Log {
        timestamp: String,
        session_id: String,
        payload: LogPayload,
    },
    #[serde(rename = "ACTION_RESULT")]
    ActionResult {
        timestamp: String,
        session_id: String,
        payload: ActionResultPayload,
    },
}

#[derive(Debug, Clone, Serialize)]
pub struct RegisterPayload {
    pub protocol_version: String,
    pub app_name: String,
    pub package_name: String,
    pub version_name: String,
    pub device: DeviceInfo,
    pub dashboard: Value,
}

#[derive(Debug, Clone, Serialize)]
pub struct DeviceInfo {
    pub device_id: String,
    pub manufacturer: String,
    pub model: String,
    pub android_version: String,
    pub api_level: i32,
    pub is_emulator: bool,
}

#[derive(Debug, Clone, Serialize)]
pub struct LogPayload {
    pub level: String,
    pub tag: Option<String>,
    pub message: String,
    pub throwable: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct ActionResultPayload {
    pub action_id: String,
    pub success: bool,
    pub message: Option<String>,
    pub data: Option<Value>,
}

/// Messages received from the server.
#[derive(Debug, Clone, Deserialize)]
#[serde(tag = "type")]
pub enum ServerMessage {
    #[serde(rename = "REGISTERED")]
    Registered {
        #[allow(dead_code)]
        timestamp: String,
        payload: RegisteredPayload,
    },
    #[serde(rename = "ACTION")]
    Action {
        #[allow(dead_code)]
        timestamp: String,
        #[allow(dead_code)]
        session_id: String,
        payload: ActionPayload,
    },
    #[serde(rename = "ERROR")]
    Error {
        #[allow(dead_code)]
        timestamp: String,
        payload: ErrorPayload,
    },
}

#[derive(Debug, Clone, Deserialize)]
pub struct RegisteredPayload {
    pub session_id: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ActionPayload {
    pub action_id: String,
    pub action: String,
    pub args: Option<Value>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ErrorPayload {
    pub code: String,
    pub message: String,
}

/// A mock app client that simulates the Android SDK behavior.
pub struct MockAppClient {
    session_id: Arc<Mutex<Option<String>>>,
    sender: mpsc::Sender<AppMessage>,
    received_messages: Arc<Mutex<Vec<ServerMessage>>>,
    connected: Arc<Mutex<bool>>,
    /// Close sender to trigger WebSocket close
    close_tx: mpsc::Sender<()>,
}

impl MockAppClient {
    /// Creates a new mock app client and connects to the server.
    pub async fn connect(server_url: &str) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        let (ws_stream, _) = connect_async(server_url).await?;
        let (mut write, mut read) = ws_stream.split();

        let (tx, mut rx) = mpsc::channel::<AppMessage>(100);
        let (close_tx, mut close_rx) = mpsc::channel::<()>(1);
        let received_messages: Arc<Mutex<Vec<ServerMessage>>> = Arc::new(Mutex::new(Vec::new()));
        let session_id: Arc<Mutex<Option<String>>> = Arc::new(Mutex::new(None));
        let connected = Arc::new(Mutex::new(true));

        let received_clone = received_messages.clone();
        let session_clone = session_id.clone();
        let connected_clone = connected.clone();

        // Spawn reader task
        tokio::spawn(async move {
            while let Some(msg) = read.next().await {
                match msg {
                    Ok(Message::Text(text)) => {
                        if let Ok(server_msg) = serde_json::from_str::<ServerMessage>(&text) {
                            if let ServerMessage::Registered { payload, .. } = &server_msg {
                                let mut session = session_clone.lock().await;
                                *session = Some(payload.session_id.clone());
                            }
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

        // Spawn writer task that also handles close requests
        tokio::spawn(async move {
            loop {
                tokio::select! {
                    Some(msg) = rx.recv() => {
                        let text = serde_json::to_string(&msg).unwrap();
                        if write.send(Message::Text(text.into())).await.is_err() {
                            break;
                        }
                    }
                    Some(()) = close_rx.recv() => {
                        // Send close frame
                        let _ = write.send(Message::Close(None)).await;
                        break;
                    }
                    else => break,
                }
            }
        });

        Ok(Self {
            session_id,
            sender: tx,
            received_messages,
            connected,
            close_tx,
        })
    }

    /// Disconnects the client by sending a close frame.
    pub async fn disconnect(&self) {
        let _ = self.close_tx.send(()).await;
        // Give the close frame time to be sent
        tokio::time::sleep(tokio::time::Duration::from_millis(50)).await;
    }

    /// Registers the mock app with the server.
    pub async fn register(&self, app_name: &str, package_name: &str) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        let dashboard = json!({
            "sections": [
                {
                    "id": "memory",
                    "title": "Memory",
                    "widgets": [
                        {
                            "type": "gauge",
                            "label": "Heap Usage",
                            "value_path": "$.memory.heap_used",
                            "max_path": "$.memory.heap_max",
                            "format": "bytes"
                        }
                    ]
                }
            ]
        });

        let msg = AppMessage::Register {
            timestamp: iso_timestamp(),
            payload: RegisterPayload {
                protocol_version: "1.0".to_string(),
                app_name: app_name.to_string(),
                package_name: package_name.to_string(),
                version_name: "1.0.0".to_string(),
                device: DeviceInfo {
                    device_id: uuid::Uuid::new_v4().to_string(),
                    manufacturer: "TestManufacturer".to_string(),
                    model: "TestModel".to_string(),
                    android_version: "14".to_string(),
                    api_level: 34,
                    is_emulator: true,
                },
                dashboard,
            },
        };

        self.sender.send(msg).await?;

        // Wait for registration response
        for _ in 0..50 {
            tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
            let session = self.session_id.lock().await;
            if let Some(id) = session.as_ref() {
                return Ok(id.clone());
            }
        }

        Err("Timeout waiting for registration".into())
    }

    /// Gets the session ID if registered.
    pub async fn session_id(&self) -> Option<String> {
        self.session_id.lock().await.clone()
    }

    /// Sends data to the server.
    pub async fn send_data(&self, data: Value) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let session_id = self.session_id.lock().await;
        let session_id = session_id.as_ref().ok_or("Not registered")?;

        let msg = AppMessage::Data {
            timestamp: iso_timestamp(),
            session_id: session_id.clone(),
            payload: data,
        };

        self.sender.send(msg).await?;
        Ok(())
    }

    /// Sends a log entry to the server.
    pub async fn send_log(
        &self,
        level: &str,
        tag: Option<&str>,
        message: &str,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let session_id = self.session_id.lock().await;
        let session_id = session_id.as_ref().ok_or("Not registered")?;

        let msg = AppMessage::Log {
            timestamp: iso_timestamp(),
            session_id: session_id.clone(),
            payload: LogPayload {
                level: level.to_string(),
                tag: tag.map(|s| s.to_string()),
                message: message.to_string(),
                throwable: None,
            },
        };

        self.sender.send(msg).await?;
        Ok(())
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

    /// Waits for an action message and returns it.
    pub async fn wait_for_action(&self, timeout_ms: u64) -> Option<ActionPayload> {
        let deadline = tokio::time::Instant::now() + tokio::time::Duration::from_millis(timeout_ms);

        loop {
            let messages = self.received_messages.lock().await;
            for msg in messages.iter() {
                if let ServerMessage::Action { payload, .. } = msg {
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

    /// Sends an action result.
    pub async fn send_action_result(
        &self,
        action_id: &str,
        success: bool,
        message: Option<&str>,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let session_id = self.session_id.lock().await;
        let session_id = session_id.as_ref().ok_or("Not registered")?;

        let msg = AppMessage::ActionResult {
            timestamp: iso_timestamp(),
            session_id: session_id.clone(),
            payload: ActionResultPayload {
                action_id: action_id.to_string(),
                success,
                message: message.map(|s| s.to_string()),
                data: None,
            },
        };

        self.sender.send(msg).await?;
        Ok(())
    }
}

fn iso_timestamp() -> String {
    chrono::Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Millis, true)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_register_payload_serialization() {
        let payload = RegisterPayload {
            protocol_version: "1.0".to_string(),
            app_name: "Test App".to_string(),
            package_name: "com.test.app".to_string(),
            version_name: "1.0.0".to_string(),
            device: DeviceInfo {
                device_id: "test-id".to_string(),
                manufacturer: "Test".to_string(),
                model: "Model".to_string(),
                android_version: "14".to_string(),
                api_level: 34,
                is_emulator: true,
            },
            dashboard: json!({"sections": []}),
        };

        let json = serde_json::to_string(&payload).unwrap();
        assert!(json.contains("Test App"));
        assert!(json.contains("com.test.app"));
        assert!(json.contains("protocol_version"));
    }

    #[test]
    fn test_app_message_serialization() {
        let msg = AppMessage::Data {
            timestamp: "2024-12-05T14:30:00.000Z".to_string(),
            session_id: "session-1".to_string(),
            payload: json!({"value": 42}),
        };

        let json = serde_json::to_string(&msg).unwrap();
        assert!(json.contains("\"type\":\"DATA\""));
        assert!(json.contains("session-1"));
    }
}
