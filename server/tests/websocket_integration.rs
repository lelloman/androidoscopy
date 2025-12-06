use std::net::SocketAddr;
use std::time::Duration;

use androidoscopy_server::config::Config;
use androidoscopy_server::handlers;
use androidoscopy_server::protocol::{
    ActionResultPayload, AppMessage, DashboardActionPayload, DashboardToServiceMessage, DeviceInfo,
    LogLevel, LogPayload, RegisterPayload, ServiceToAppMessage, ServiceToDashboardMessage,
};
use androidoscopy_server::state::AppState;

use axum::{routing::get, Router};
use futures::{SinkExt, StreamExt};
use serde_json::json;
use tokio::net::TcpListener;
use tokio_tungstenite::{connect_async, tungstenite::Message};

async fn spawn_test_server() -> SocketAddr {
    let mut config = Config::default();
    config.server.udp_discovery_enabled = false;

    let state = AppState::new(config);

    let app = Router::new()
        .route("/ws/app", get(handlers::handle_app_ws))
        .route("/ws/dashboard", get(handlers::handle_dashboard_ws))
        .with_state(state);

    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();

    tokio::spawn(async move {
        axum::serve(listener, app).await.unwrap();
    });

    // Give the server a moment to start
    tokio::time::sleep(Duration::from_millis(10)).await;

    addr
}

fn create_register_payload() -> RegisterPayload {
    RegisterPayload {
        protocol_version: "1.0".to_string(),
        app_name: "TestApp".to_string(),
        package_name: "com.test.app".to_string(),
        version_name: "1.0.0".to_string(),
        device: DeviceInfo {
            device_id: "test-device-123".to_string(),
            manufacturer: "Google".to_string(),
            model: "Pixel 5".to_string(),
            android_version: "13".to_string(),
            api_level: 33,
            is_emulator: true,
        },
        dashboard: json!({
            "sections": [
                {
                    "id": "memory",
                    "title": "Memory",
                    "widgets": []
                }
            ]
        }),
    }
}

#[tokio::test]
async fn test_app_connection_and_registration() {
    let addr = spawn_test_server().await;
    let url = format!("ws://{}/ws/app", addr);

    let (mut ws_stream, _) = connect_async(&url).await.expect("Failed to connect");

    // Send REGISTER message
    let register_msg = AppMessage::Register {
        timestamp: chrono::Utc::now(),
        payload: create_register_payload(),
    };
    let json = serde_json::to_string(&register_msg).unwrap();
    ws_stream.send(Message::Text(json.into())).await.unwrap();

    // Receive REGISTERED response
    let response = tokio::time::timeout(Duration::from_secs(5), ws_stream.next())
        .await
        .expect("Timeout waiting for response")
        .expect("Stream closed")
        .expect("WebSocket error");

    if let Message::Text(text) = response {
        let msg: ServiceToAppMessage = serde_json::from_str(&text).unwrap();
        match msg {
            ServiceToAppMessage::Registered { payload, .. } => {
                assert!(!payload.session_id.is_empty());
            }
            _ => panic!("Expected REGISTERED message"),
        }
    } else {
        panic!("Expected text message");
    }

    ws_stream.close(None).await.ok();
}

#[tokio::test]
async fn test_dashboard_connection_and_sync() {
    let addr = spawn_test_server().await;
    let dashboard_url = format!("ws://{}/ws/dashboard", addr);

    let (mut dashboard_ws, _) = connect_async(&dashboard_url)
        .await
        .expect("Failed to connect dashboard");

    // Dashboard should immediately receive a SYNC message
    let response = tokio::time::timeout(Duration::from_secs(5), dashboard_ws.next())
        .await
        .expect("Timeout waiting for SYNC")
        .expect("Stream closed")
        .expect("WebSocket error");

    if let Message::Text(text) = response {
        let msg: ServiceToDashboardMessage = serde_json::from_str(&text).unwrap();
        match msg {
            ServiceToDashboardMessage::Sync { payload } => {
                // No sessions yet
                assert!(payload.sessions.is_empty());
            }
            _ => panic!("Expected SYNC message, got {:?}", msg),
        }
    } else {
        panic!("Expected text message");
    }

    dashboard_ws.close(None).await.ok();
}

#[tokio::test]
async fn test_dashboard_receives_session_started_on_app_connect() {
    let addr = spawn_test_server().await;
    let app_url = format!("ws://{}/ws/app", addr);
    let dashboard_url = format!("ws://{}/ws/dashboard", addr);

    // Connect dashboard first
    let (mut dashboard_ws, _) = connect_async(&dashboard_url)
        .await
        .expect("Failed to connect dashboard");

    // Receive initial SYNC
    let _ = tokio::time::timeout(Duration::from_secs(5), dashboard_ws.next())
        .await
        .expect("Timeout waiting for SYNC")
        .expect("Stream closed")
        .expect("WebSocket error");

    // Now connect app and register
    let (mut app_ws, _) = connect_async(&app_url)
        .await
        .expect("Failed to connect app");

    let register_msg = AppMessage::Register {
        timestamp: chrono::Utc::now(),
        payload: create_register_payload(),
    };
    let json = serde_json::to_string(&register_msg).unwrap();
    app_ws.send(Message::Text(json.into())).await.unwrap();

    // App receives REGISTERED
    let _ = tokio::time::timeout(Duration::from_secs(5), app_ws.next())
        .await
        .expect("Timeout waiting for REGISTERED")
        .expect("Stream closed")
        .expect("WebSocket error");

    // Dashboard should receive SESSION_STARTED
    let response = tokio::time::timeout(Duration::from_secs(5), dashboard_ws.next())
        .await
        .expect("Timeout waiting for SESSION_STARTED")
        .expect("Stream closed")
        .expect("WebSocket error");

    if let Message::Text(text) = response {
        let msg: ServiceToDashboardMessage = serde_json::from_str(&text).unwrap();
        match msg {
            ServiceToDashboardMessage::SessionStarted { payload } => {
                assert_eq!(payload.session.app_name, "TestApp");
                assert_eq!(payload.session.package_name, "com.test.app");
            }
            _ => panic!("Expected SESSION_STARTED message, got {:?}", msg),
        }
    } else {
        panic!("Expected text message");
    }

    app_ws.close(None).await.ok();
    dashboard_ws.close(None).await.ok();
}

#[tokio::test]
async fn test_data_message_routing_to_dashboard() {
    let addr = spawn_test_server().await;
    let app_url = format!("ws://{}/ws/app", addr);
    let dashboard_url = format!("ws://{}/ws/dashboard", addr);

    // Connect dashboard first
    let (mut dashboard_ws, _) = connect_async(&dashboard_url)
        .await
        .expect("Failed to connect dashboard");

    // Receive initial SYNC
    let _ = dashboard_ws.next().await;

    // Connect and register app
    let (mut app_ws, _) = connect_async(&app_url)
        .await
        .expect("Failed to connect app");

    let register_msg = AppMessage::Register {
        timestamp: chrono::Utc::now(),
        payload: create_register_payload(),
    };
    app_ws
        .send(Message::Text(serde_json::to_string(&register_msg).unwrap().into()))
        .await
        .unwrap();

    // Get session ID from REGISTERED response
    let registered_response = app_ws.next().await.unwrap().unwrap();
    let session_id = if let Message::Text(text) = registered_response {
        let msg: ServiceToAppMessage = serde_json::from_str(&text).unwrap();
        match msg {
            ServiceToAppMessage::Registered { payload, .. } => payload.session_id,
            _ => panic!("Expected REGISTERED"),
        }
    } else {
        panic!("Expected text message")
    };

    // Dashboard receives SESSION_STARTED
    let _ = dashboard_ws.next().await;

    // App sends DATA message
    let data_msg = AppMessage::Data {
        timestamp: chrono::Utc::now(),
        session_id: session_id.clone(),
        payload: json!({
            "memory": {
                "heap_used_bytes": 1000000,
                "heap_max_bytes": 5000000
            }
        }),
    };
    app_ws
        .send(Message::Text(serde_json::to_string(&data_msg).unwrap().into()))
        .await
        .unwrap();

    // Dashboard should receive SESSION_DATA
    let response = tokio::time::timeout(Duration::from_secs(5), dashboard_ws.next())
        .await
        .expect("Timeout waiting for SESSION_DATA")
        .expect("Stream closed")
        .expect("WebSocket error");

    if let Message::Text(text) = response {
        let msg: ServiceToDashboardMessage = serde_json::from_str(&text).unwrap();
        match msg {
            ServiceToDashboardMessage::SessionData {
                payload,
                ..
            } => {
                assert_eq!(payload.session_id, session_id);
                assert_eq!(payload.data["memory"]["heap_used_bytes"], 1000000);
            }
            _ => panic!("Expected SESSION_DATA message, got {:?}", msg),
        }
    } else {
        panic!("Expected text message");
    }

    app_ws.close(None).await.ok();
    dashboard_ws.close(None).await.ok();
}

#[tokio::test]
async fn test_log_message_routing_to_dashboard() {
    let addr = spawn_test_server().await;
    let app_url = format!("ws://{}/ws/app", addr);
    let dashboard_url = format!("ws://{}/ws/dashboard", addr);

    // Connect dashboard first
    let (mut dashboard_ws, _) = connect_async(&dashboard_url)
        .await
        .expect("Failed to connect dashboard");

    // Receive initial SYNC
    let _ = dashboard_ws.next().await;

    // Connect and register app
    let (mut app_ws, _) = connect_async(&app_url)
        .await
        .expect("Failed to connect app");

    let register_msg = AppMessage::Register {
        timestamp: chrono::Utc::now(),
        payload: create_register_payload(),
    };
    app_ws
        .send(Message::Text(serde_json::to_string(&register_msg).unwrap().into()))
        .await
        .unwrap();

    // Get session ID from REGISTERED response
    let registered_response = app_ws.next().await.unwrap().unwrap();
    let session_id = if let Message::Text(text) = registered_response {
        let msg: ServiceToAppMessage = serde_json::from_str(&text).unwrap();
        match msg {
            ServiceToAppMessage::Registered { payload, .. } => payload.session_id,
            _ => panic!("Expected REGISTERED"),
        }
    } else {
        panic!("Expected text message")
    };

    // Dashboard receives SESSION_STARTED
    let _ = dashboard_ws.next().await;

    // App sends LOG message
    let log_msg = AppMessage::Log {
        timestamp: chrono::Utc::now(),
        session_id: session_id.clone(),
        payload: LogPayload {
            level: LogLevel::Error,
            tag: Some("NetworkClient".to_string()),
            message: "Connection failed".to_string(),
            throwable: Some("java.io.IOException: Connection refused".to_string()),
        },
    };
    app_ws
        .send(Message::Text(serde_json::to_string(&log_msg).unwrap().into()))
        .await
        .unwrap();

    // Dashboard should receive SESSION_LOG
    let response = tokio::time::timeout(Duration::from_secs(5), dashboard_ws.next())
        .await
        .expect("Timeout waiting for SESSION_LOG")
        .expect("Stream closed")
        .expect("WebSocket error");

    if let Message::Text(text) = response {
        let msg: ServiceToDashboardMessage = serde_json::from_str(&text).unwrap();
        match msg {
            ServiceToDashboardMessage::SessionLog {
                payload,
                ..
            } => {
                assert_eq!(payload.session_id, session_id);
                assert_eq!(payload.log.level, LogLevel::Error);
                assert_eq!(payload.log.tag, Some("NetworkClient".to_string()));
                assert_eq!(payload.log.message, "Connection failed");
            }
            _ => panic!("Expected SESSION_LOG message, got {:?}", msg),
        }
    } else {
        panic!("Expected text message");
    }

    app_ws.close(None).await.ok();
    dashboard_ws.close(None).await.ok();
}

#[tokio::test]
async fn test_action_routing_from_dashboard_to_app() {
    let addr = spawn_test_server().await;
    let app_url = format!("ws://{}/ws/app", addr);
    let dashboard_url = format!("ws://{}/ws/dashboard", addr);

    // Connect dashboard first
    let (mut dashboard_ws, _) = connect_async(&dashboard_url)
        .await
        .expect("Failed to connect dashboard");

    // Receive initial SYNC
    let _ = dashboard_ws.next().await;

    // Connect and register app
    let (mut app_ws, _) = connect_async(&app_url)
        .await
        .expect("Failed to connect app");

    let register_msg = AppMessage::Register {
        timestamp: chrono::Utc::now(),
        payload: create_register_payload(),
    };
    app_ws
        .send(Message::Text(serde_json::to_string(&register_msg).unwrap().into()))
        .await
        .unwrap();

    // Get session ID from REGISTERED response
    let registered_response = app_ws.next().await.unwrap().unwrap();
    let session_id = if let Message::Text(text) = registered_response {
        let msg: ServiceToAppMessage = serde_json::from_str(&text).unwrap();
        match msg {
            ServiceToAppMessage::Registered { payload, .. } => payload.session_id,
            _ => panic!("Expected REGISTERED"),
        }
    } else {
        panic!("Expected text message")
    };

    // Dashboard receives SESSION_STARTED
    let _ = dashboard_ws.next().await;

    // Dashboard sends ACTION to app
    let action_msg = DashboardToServiceMessage::Action {
        payload: DashboardActionPayload {
            session_id: session_id.clone(),
            action_id: "action-123".to_string(),
            action: "clear_cache".to_string(),
            args: Some(json!({ "cache_type": "image" })),
        },
    };
    dashboard_ws
        .send(Message::Text(serde_json::to_string(&action_msg).unwrap().into()))
        .await
        .unwrap();

    // App should receive ACTION
    let response = tokio::time::timeout(Duration::from_secs(5), app_ws.next())
        .await
        .expect("Timeout waiting for ACTION")
        .expect("Stream closed")
        .expect("WebSocket error");

    if let Message::Text(text) = response {
        let msg: ServiceToAppMessage = serde_json::from_str(&text).unwrap();
        match msg {
            ServiceToAppMessage::Action {
                session_id: sid,
                payload,
                ..
            } => {
                assert_eq!(sid, session_id);
                assert_eq!(payload.action_id, "action-123");
                assert_eq!(payload.action, "clear_cache");
            }
            _ => panic!("Expected ACTION message, got {:?}", msg),
        }
    } else {
        panic!("Expected text message");
    }

    app_ws.close(None).await.ok();
    dashboard_ws.close(None).await.ok();
}

#[tokio::test]
async fn test_action_result_routing_to_dashboard() {
    let addr = spawn_test_server().await;
    let app_url = format!("ws://{}/ws/app", addr);
    let dashboard_url = format!("ws://{}/ws/dashboard", addr);

    // Connect dashboard first
    let (mut dashboard_ws, _) = connect_async(&dashboard_url)
        .await
        .expect("Failed to connect dashboard");

    // Receive initial SYNC
    let _ = dashboard_ws.next().await;

    // Connect and register app
    let (mut app_ws, _) = connect_async(&app_url)
        .await
        .expect("Failed to connect app");

    let register_msg = AppMessage::Register {
        timestamp: chrono::Utc::now(),
        payload: create_register_payload(),
    };
    app_ws
        .send(Message::Text(serde_json::to_string(&register_msg).unwrap().into()))
        .await
        .unwrap();

    // Get session ID from REGISTERED response
    let registered_response = app_ws.next().await.unwrap().unwrap();
    let session_id = if let Message::Text(text) = registered_response {
        let msg: ServiceToAppMessage = serde_json::from_str(&text).unwrap();
        match msg {
            ServiceToAppMessage::Registered { payload, .. } => payload.session_id,
            _ => panic!("Expected REGISTERED"),
        }
    } else {
        panic!("Expected text message")
    };

    // Dashboard receives SESSION_STARTED
    let _ = dashboard_ws.next().await;

    // App sends ACTION_RESULT
    let result_msg = AppMessage::ActionResult {
        timestamp: chrono::Utc::now(),
        session_id: session_id.clone(),
        payload: ActionResultPayload {
            action_id: "action-123".to_string(),
            success: true,
            message: Some("Cache cleared successfully".to_string()),
            data: Some(json!({ "bytes_cleared": 1024000 })),
        },
    };
    app_ws
        .send(Message::Text(serde_json::to_string(&result_msg).unwrap().into()))
        .await
        .unwrap();

    // Dashboard should receive ACTION_RESULT
    let response = tokio::time::timeout(Duration::from_secs(5), dashboard_ws.next())
        .await
        .expect("Timeout waiting for ACTION_RESULT")
        .expect("Stream closed")
        .expect("WebSocket error");

    if let Message::Text(text) = response {
        let msg: ServiceToDashboardMessage = serde_json::from_str(&text).unwrap();
        match msg {
            ServiceToDashboardMessage::ActionResult {
                payload,
                ..
            } => {
                assert_eq!(payload.session_id, session_id);
                assert!(payload.success);
                assert_eq!(payload.action_id, "action-123");
                assert_eq!(payload.message, Some("Cache cleared successfully".to_string()));
            }
            _ => panic!("Expected ACTION_RESULT message, got {:?}", msg),
        }
    } else {
        panic!("Expected text message");
    }

    app_ws.close(None).await.ok();
    dashboard_ws.close(None).await.ok();
}

#[tokio::test]
async fn test_app_disconnection_sends_session_ended() {
    let addr = spawn_test_server().await;
    let app_url = format!("ws://{}/ws/app", addr);
    let dashboard_url = format!("ws://{}/ws/dashboard", addr);

    // Connect dashboard first
    let (mut dashboard_ws, _) = connect_async(&dashboard_url)
        .await
        .expect("Failed to connect dashboard");

    // Receive initial SYNC
    let _ = dashboard_ws.next().await;

    // Connect and register app
    let (mut app_ws, _) = connect_async(&app_url)
        .await
        .expect("Failed to connect app");

    let register_msg = AppMessage::Register {
        timestamp: chrono::Utc::now(),
        payload: create_register_payload(),
    };
    app_ws
        .send(Message::Text(serde_json::to_string(&register_msg).unwrap().into()))
        .await
        .unwrap();

    // Get session ID from REGISTERED response
    let registered_response = app_ws.next().await.unwrap().unwrap();
    let session_id = if let Message::Text(text) = registered_response {
        let msg: ServiceToAppMessage = serde_json::from_str(&text).unwrap();
        match msg {
            ServiceToAppMessage::Registered { payload, .. } => payload.session_id,
            _ => panic!("Expected REGISTERED"),
        }
    } else {
        panic!("Expected text message")
    };

    // Dashboard receives SESSION_STARTED
    let _ = dashboard_ws.next().await;

    // App disconnects
    app_ws.close(None).await.ok();

    // Dashboard should receive SESSION_ENDED
    let response = tokio::time::timeout(Duration::from_secs(5), dashboard_ws.next())
        .await
        .expect("Timeout waiting for SESSION_ENDED")
        .expect("Stream closed")
        .expect("WebSocket error");

    if let Message::Text(text) = response {
        let msg: ServiceToDashboardMessage = serde_json::from_str(&text).unwrap();
        match msg {
            ServiceToDashboardMessage::SessionEnded {
                payload, ..
            } => {
                assert_eq!(payload.session_id, session_id);
            }
            _ => panic!("Expected SESSION_ENDED message, got {:?}", msg),
        }
    } else {
        panic!("Expected text message");
    }

    dashboard_ws.close(None).await.ok();
}

#[tokio::test]
async fn test_late_dashboard_receives_existing_sessions_in_sync() {
    let addr = spawn_test_server().await;
    let app_url = format!("ws://{}/ws/app", addr);
    let dashboard_url = format!("ws://{}/ws/dashboard", addr);

    // Connect and register app first
    let (mut app_ws, _) = connect_async(&app_url)
        .await
        .expect("Failed to connect app");

    let register_msg = AppMessage::Register {
        timestamp: chrono::Utc::now(),
        payload: create_register_payload(),
    };
    app_ws
        .send(Message::Text(serde_json::to_string(&register_msg).unwrap().into()))
        .await
        .unwrap();

    // Get session ID from REGISTERED response
    let registered_response = app_ws.next().await.unwrap().unwrap();
    let session_id = if let Message::Text(text) = registered_response {
        let msg: ServiceToAppMessage = serde_json::from_str(&text).unwrap();
        match msg {
            ServiceToAppMessage::Registered { payload, .. } => payload.session_id,
            _ => panic!("Expected REGISTERED"),
        }
    } else {
        panic!("Expected text message")
    };

    // Now connect dashboard (after app is already registered)
    let (mut dashboard_ws, _) = connect_async(&dashboard_url)
        .await
        .expect("Failed to connect dashboard");

    // Dashboard should receive SYNC with the existing session
    let response = tokio::time::timeout(Duration::from_secs(5), dashboard_ws.next())
        .await
        .expect("Timeout waiting for SYNC")
        .expect("Stream closed")
        .expect("WebSocket error");

    if let Message::Text(text) = response {
        let msg: ServiceToDashboardMessage = serde_json::from_str(&text).unwrap();
        match msg {
            ServiceToDashboardMessage::Sync { payload } => {
                assert_eq!(payload.sessions.len(), 1);
                assert_eq!(payload.sessions[0].session_id, session_id);
                assert_eq!(payload.sessions[0].app_name, "TestApp");
            }
            _ => panic!("Expected SYNC message, got {:?}", msg),
        }
    } else {
        panic!("Expected text message");
    }

    app_ws.close(None).await.ok();
    dashboard_ws.close(None).await.ok();
}

#[tokio::test]
async fn test_multiple_dashboards_receive_updates() {
    let addr = spawn_test_server().await;
    let app_url = format!("ws://{}/ws/app", addr);
    let dashboard_url = format!("ws://{}/ws/dashboard", addr);

    // Connect two dashboards
    let (mut dashboard1_ws, _) = connect_async(&dashboard_url)
        .await
        .expect("Failed to connect dashboard1");
    let (mut dashboard2_ws, _) = connect_async(&dashboard_url)
        .await
        .expect("Failed to connect dashboard2");

    // Receive initial SYNC on both
    let _ = dashboard1_ws.next().await;
    let _ = dashboard2_ws.next().await;

    // Connect and register app
    let (mut app_ws, _) = connect_async(&app_url)
        .await
        .expect("Failed to connect app");

    let register_msg = AppMessage::Register {
        timestamp: chrono::Utc::now(),
        payload: create_register_payload(),
    };
    app_ws
        .send(Message::Text(serde_json::to_string(&register_msg).unwrap().into()))
        .await
        .unwrap();

    // App receives REGISTERED
    let _ = app_ws.next().await;

    // Both dashboards should receive SESSION_STARTED
    let response1 = tokio::time::timeout(Duration::from_secs(5), dashboard1_ws.next())
        .await
        .expect("Timeout waiting for SESSION_STARTED on dashboard1")
        .expect("Stream closed")
        .expect("WebSocket error");

    let response2 = tokio::time::timeout(Duration::from_secs(5), dashboard2_ws.next())
        .await
        .expect("Timeout waiting for SESSION_STARTED on dashboard2")
        .expect("Stream closed")
        .expect("WebSocket error");

    // Verify both received SESSION_STARTED
    for (i, response) in [(1, response1), (2, response2)] {
        if let Message::Text(text) = response {
            let msg: ServiceToDashboardMessage = serde_json::from_str(&text).unwrap();
            match msg {
                ServiceToDashboardMessage::SessionStarted { payload } => {
                    assert_eq!(payload.session.app_name, "TestApp", "Dashboard {} mismatch", i);
                }
                _ => panic!("Expected SESSION_STARTED on dashboard {}, got {:?}", i, msg),
            }
        } else {
            panic!("Expected text message on dashboard {}", i);
        }
    }

    app_ws.close(None).await.ok();
    dashboard1_ws.close(None).await.ok();
    dashboard2_ws.close(None).await.ok();
}
