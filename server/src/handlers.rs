use axum::{
    extract::ws::{WebSocket, WebSocketUpgrade},
    response::IntoResponse,
};
use tracing::info;

pub async fn handle_app_ws(ws: WebSocketUpgrade) -> impl IntoResponse {
    ws.on_upgrade(handle_app_connection)
}

pub async fn handle_dashboard_ws(ws: WebSocketUpgrade) -> impl IntoResponse {
    ws.on_upgrade(handle_dashboard_connection)
}

async fn handle_app_connection(mut socket: WebSocket) {
    info!("App WebSocket connection established");

    // TODO: Implement app connection handling
    // 1. Wait for REGISTER message
    // 2. Create session, send REGISTERED
    // 3. Loop: receive messages, update session, broadcast to dashboards
    // 4. On disconnect: mark session ended

    while let Some(msg) = socket.recv().await {
        match msg {
            Ok(_msg) => {
                // TODO: Handle message
            }
            Err(e) => {
                info!("App WebSocket error: {}", e);
                break;
            }
        }
    }

    info!("App WebSocket connection closed");
}

async fn handle_dashboard_connection(mut socket: WebSocket) {
    info!("Dashboard WebSocket connection established");

    // TODO: Implement dashboard connection handling
    // 1. Send SYNC with all active sessions
    // 2. Subscribe to session events
    // 3. Loop: forward session events, receive ACTIONs

    while let Some(msg) = socket.recv().await {
        match msg {
            Ok(_msg) => {
                // TODO: Handle message
            }
            Err(e) => {
                info!("Dashboard WebSocket error: {}", e);
                break;
            }
        }
    }

    info!("Dashboard WebSocket connection closed");
}
