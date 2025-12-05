//! Full stack E2E tests for Androidoscopy.
//!
//! These tests verify the complete flow of data between:
//! - Mock Android app (simulating the SDK)
//! - Androidoscopy server
//! - Mock dashboard (simulating the web UI)

use e2e_tests::{MockAppClient, MockDashboardClient};
use serde_json::json;
use std::time::Duration;

/// Starts a test server and returns the WebSocket URL for app connections.
async fn start_test_server() -> (String, String, tokio::task::JoinHandle<()>) {
    let config = androidoscopy_server::Config::default();
    let (addr, handle) = androidoscopy_server::start_test_server(config)
        .await
        .expect("Failed to start test server");

    let app_url = format!("ws://{}/ws/app", addr);
    let dashboard_url = format!("ws://{}/ws/dashboard", addr);

    // Give the server a moment to start
    tokio::time::sleep(Duration::from_millis(100)).await;

    (app_url, dashboard_url, handle)
}

/// Test: App connects → Dashboard shows session
///
/// Verifies that when an app connects and registers, the dashboard
/// receives the session information via SESSION_STARTED message.
#[tokio::test]
async fn test_app_connect_dashboard_shows_session() {
    let (app_url, dashboard_url, handle) = start_test_server().await;

    // Connect dashboard first
    let dashboard = MockDashboardClient::connect(&dashboard_url)
        .await
        .expect("Dashboard should connect");

    // Wait for SYNC message (empty sessions initially)
    let sync = dashboard.wait_for_sync(2000).await;
    assert!(sync.is_some(), "Dashboard should receive SYNC message");
    assert!(sync.unwrap().sessions.is_empty(), "No sessions initially");

    // Connect and register app
    let app = MockAppClient::connect(&app_url)
        .await
        .expect("App should connect");

    let session_id = app
        .register("Test App", "com.test.app")
        .await
        .expect("App should register");

    // Dashboard should receive SESSION_STARTED
    let session_info = dashboard.wait_for_session_started(2000).await;
    assert!(session_info.is_some(), "Dashboard should receive SESSION_STARTED");

    let session_info = session_info.unwrap();
    assert_eq!(session_info.session_id, session_id);
    assert_eq!(session_info.app_name, "Test App");
    assert_eq!(session_info.package_name, "com.test.app");

    handle.abort();
}

/// Test: App sends DATA → Dashboard widgets update
///
/// Verifies that when an app sends data updates, the dashboard
/// receives SESSION_DATA messages with the payload.
#[tokio::test]
async fn test_app_sends_data_dashboard_receives() {
    let (app_url, dashboard_url, handle) = start_test_server().await;

    // Connect dashboard
    let dashboard = MockDashboardClient::connect(&dashboard_url)
        .await
        .expect("Dashboard should connect");
    dashboard.wait_for_sync(2000).await;

    // Connect and register app
    let app = MockAppClient::connect(&app_url)
        .await
        .expect("App should connect");

    let session_id = app
        .register("Test App", "com.test.app")
        .await
        .expect("App should register");

    // Wait for dashboard to receive session started
    dashboard.wait_for_session_started(2000).await;
    dashboard.clear_messages().await;

    // App sends data
    let data = json!({
        "memory": {
            "heap_used": 1000000,
            "heap_max": 5000000
        },
        "network": {
            "active_connections": 3
        }
    });
    app.send_data(data.clone())
        .await
        .expect("App should send data");

    // Dashboard should receive SESSION_DATA
    let received_data = dashboard.wait_for_session_data(&session_id, 2000).await;
    assert!(received_data.is_some(), "Dashboard should receive SESSION_DATA");

    let received_data = received_data.unwrap();
    assert_eq!(received_data["memory"]["heap_used"], 1000000);
    assert_eq!(received_data["network"]["active_connections"], 3);

    handle.abort();
}

/// Test: App sends LOG → Log viewer updates
///
/// Verifies that when an app sends log messages, the dashboard
/// receives SESSION_LOG messages with the log entries.
#[tokio::test]
async fn test_app_sends_log_dashboard_receives() {
    let (app_url, dashboard_url, handle) = start_test_server().await;

    // Connect dashboard
    let dashboard = MockDashboardClient::connect(&dashboard_url)
        .await
        .expect("Dashboard should connect");
    dashboard.wait_for_sync(2000).await;

    // Connect and register app
    let app = MockAppClient::connect(&app_url)
        .await
        .expect("App should connect");

    let session_id = app
        .register("Test App", "com.test.app")
        .await
        .expect("App should register");

    // Wait for dashboard to receive session started
    dashboard.wait_for_session_started(2000).await;
    dashboard.clear_messages().await;

    // App sends log
    app.send_log("ERROR", Some("NetworkClient"), "Connection timeout")
        .await
        .expect("App should send log");

    // Dashboard should receive SESSION_LOG
    let log = dashboard.wait_for_session_log(&session_id, 2000).await;
    assert!(log.is_some(), "Dashboard should receive SESSION_LOG");

    let log = log.unwrap();
    assert_eq!(log.level, "ERROR");
    assert_eq!(log.tag, Some("NetworkClient".to_string()));
    assert_eq!(log.message, "Connection timeout");

    handle.abort();
}

/// Test: Dashboard sends ACTION → App receives and responds
///
/// Verifies the full action flow:
/// 1. Dashboard sends ACTION to a session
/// 2. App receives the ACTION
/// 3. App sends ACTION_RESULT
/// 4. Dashboard receives ACTION_RESULT
#[tokio::test]
async fn test_dashboard_sends_action_app_responds() {
    let (app_url, dashboard_url, handle) = start_test_server().await;

    // Connect dashboard
    let dashboard = MockDashboardClient::connect(&dashboard_url)
        .await
        .expect("Dashboard should connect");
    dashboard.wait_for_sync(2000).await;

    // Connect and register app
    let app = MockAppClient::connect(&app_url)
        .await
        .expect("App should connect");

    let session_id = app
        .register("Test App", "com.test.app")
        .await
        .expect("App should register");

    // Wait for dashboard to receive session started
    dashboard.wait_for_session_started(2000).await;

    // Dashboard sends action
    let action_id = dashboard
        .send_action(&session_id, "clear_cache", Some(json!({"type": "all"})))
        .await
        .expect("Dashboard should send action");

    // App should receive action
    let action = app.wait_for_action(2000).await;
    assert!(action.is_some(), "App should receive ACTION");

    let action = action.unwrap();
    assert_eq!(action.action_id, action_id);
    assert_eq!(action.action, "clear_cache");
    assert_eq!(action.args, Some(json!({"type": "all"})));

    // App sends result
    app.send_action_result(&action_id, true, Some("Cache cleared successfully"))
        .await
        .expect("App should send action result");

    // Dashboard should receive result
    let result = dashboard.wait_for_action_result(&action_id, 2000).await;
    assert!(result.is_some(), "Dashboard should receive ACTION_RESULT");

    let result = result.unwrap();
    assert!(result.success);
    assert_eq!(result.message, Some("Cache cleared successfully".to_string()));

    handle.abort();
}

/// Test: App disconnects → Dashboard shows session ended
///
/// Verifies that when an app disconnects (simulating app close/crash),
/// the dashboard receives SESSION_ENDED message.
#[tokio::test]
async fn test_app_disconnect_dashboard_shows_ended() {
    let (app_url, dashboard_url, handle) = start_test_server().await;

    // Connect dashboard
    let dashboard = MockDashboardClient::connect(&dashboard_url)
        .await
        .expect("Dashboard should connect");
    dashboard.wait_for_sync(2000).await;

    // Connect and register app
    let app = MockAppClient::connect(&app_url)
        .await
        .expect("App should connect");

    let session_id = app
        .register("Test App", "com.test.app")
        .await
        .expect("App should register");

    // Wait for dashboard to receive session started
    dashboard.wait_for_session_started(2000).await;
    dashboard.clear_messages().await;

    // Disconnect app properly to trigger SESSION_ENDED
    app.disconnect().await;

    // Dashboard should receive SESSION_ENDED
    let ended = dashboard.wait_for_session_ended(&session_id, 2000).await;
    assert!(ended, "Dashboard should receive SESSION_ENDED");

    handle.abort();
}

/// Test: Dashboard connects after app → Receives session in SYNC
///
/// Verifies that when a dashboard connects after an app is already
/// registered, it receives the session in the initial SYNC message.
#[tokio::test]
async fn test_dashboard_connects_after_app_receives_sync() {
    let (app_url, dashboard_url, handle) = start_test_server().await;

    // Connect and register app first
    let app = MockAppClient::connect(&app_url)
        .await
        .expect("App should connect");

    let session_id = app
        .register("Test App", "com.test.app")
        .await
        .expect("App should register");

    // Give server time to process registration
    tokio::time::sleep(Duration::from_millis(100)).await;

    // Now connect dashboard
    let dashboard = MockDashboardClient::connect(&dashboard_url)
        .await
        .expect("Dashboard should connect");

    // SYNC should contain the existing session
    let sync = dashboard.wait_for_sync(2000).await;
    assert!(sync.is_some(), "Dashboard should receive SYNC message");

    let sync = sync.unwrap();
    assert_eq!(sync.sessions.len(), 1, "Should have one session");
    assert_eq!(sync.sessions[0].session_id, session_id);
    assert_eq!(sync.sessions[0].app_name, "Test App");

    handle.abort();
}

/// Test: Multiple apps and dashboards
///
/// Verifies that multiple apps can connect and multiple dashboards
/// receive updates from all apps.
#[tokio::test]
async fn test_multiple_apps_multiple_dashboards() {
    let (app_url, dashboard_url, handle) = start_test_server().await;

    // Connect two dashboards
    let dashboard1 = MockDashboardClient::connect(&dashboard_url)
        .await
        .expect("Dashboard 1 should connect");
    dashboard1.wait_for_sync(2000).await;

    let dashboard2 = MockDashboardClient::connect(&dashboard_url)
        .await
        .expect("Dashboard 2 should connect");
    dashboard2.wait_for_sync(2000).await;

    // Connect first app
    let app1 = MockAppClient::connect(&app_url)
        .await
        .expect("App 1 should connect");
    let session_id1 = app1
        .register("App One", "com.test.app1")
        .await
        .expect("App 1 should register");

    // Both dashboards should receive SESSION_STARTED for app1
    let session1_d1 = dashboard1.wait_for_session_started(2000).await;
    let session1_d2 = dashboard2.wait_for_session_started(2000).await;
    assert!(session1_d1.is_some() && session1_d2.is_some());

    dashboard1.clear_messages().await;
    dashboard2.clear_messages().await;

    // Connect second app
    let app2 = MockAppClient::connect(&app_url)
        .await
        .expect("App 2 should connect");
    let session_id2 = app2
        .register("App Two", "com.test.app2")
        .await
        .expect("App 2 should register");

    // Both dashboards should receive SESSION_STARTED for app2
    let session2_d1 = dashboard1.wait_for_session_started(2000).await;
    let session2_d2 = dashboard2.wait_for_session_started(2000).await;
    assert!(session2_d1.is_some() && session2_d2.is_some());
    assert_eq!(session2_d1.unwrap().session_id, session_id2);

    // Send data from app1
    dashboard1.clear_messages().await;
    dashboard2.clear_messages().await;

    app1.send_data(json!({"value": 1}))
        .await
        .expect("App 1 should send data");

    // Both dashboards should receive the data
    let data_d1 = dashboard1.wait_for_session_data(&session_id1, 2000).await;
    let data_d2 = dashboard2.wait_for_session_data(&session_id1, 2000).await;
    assert!(data_d1.is_some() && data_d2.is_some());

    handle.abort();
}
