pub mod config;
pub mod discovery;
pub mod handlers;
pub mod protocol;
pub mod session;
pub mod state;

use axum::{routing::get, Router};
use std::net::SocketAddr;
use tokio::net::TcpListener;

pub use config::Config;
pub use state::AppState;

/// Creates the Axum router for the server.
/// Useful for testing - can be used with `axum::serve` directly.
pub fn create_router(state: AppState) -> Router {
    Router::new()
        .route("/ws/app", get(handlers::handle_app_ws))
        .route("/ws/dashboard", get(handlers::handle_dashboard_ws))
        .with_state(state)
}

/// Starts the server on a random available port and returns the address.
/// Useful for integration tests.
pub async fn start_test_server(config: Config) -> std::io::Result<(SocketAddr, tokio::task::JoinHandle<()>)> {
    let state = AppState::new(config);
    let app = create_router(state);

    let listener = TcpListener::bind("127.0.0.1:0").await?;
    let addr = listener.local_addr()?;

    let handle = tokio::spawn(async move {
        axum::serve(listener, app).await.ok();
    });

    Ok((addr, handle))
}
