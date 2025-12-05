use axum::{routing::get, Router};
use std::net::SocketAddr;
use tokio::net::TcpListener;
use tower_http::services::{ServeDir, ServeFile};
use tracing::info;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

mod config;
mod discovery;
mod handlers;
mod protocol;
mod session;
mod state;

use config::Config;
use state::AppState;

#[tokio::main]
async fn main() {
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "androidoscopy_server=debug,tower_http=debug".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    let config = Config::load().unwrap_or_default();
    let state = AppState::new(config.clone());

    // Start UDP discovery broadcast if enabled
    if config.server.udp_discovery_enabled {
        let websocket_port = config.server.websocket_port;
        let http_port = config.server.http_port;
        tokio::spawn(async move {
            discovery::broadcast_presence(websocket_port, http_port).await;
        });
    }

    // Set up static file serving with SPA fallback
    let static_dir = &config.dashboard.static_dir;
    let index_path = format!("{}/index.html", static_dir);
    let serve_dir = ServeDir::new(static_dir)
        .not_found_service(ServeFile::new(&index_path));

    let app = Router::new()
        .route("/ws/app", get(handlers::handle_app_ws))
        .route("/ws/dashboard", get(handlers::handle_dashboard_ws))
        .fallback_service(serve_dir)
        .with_state(state);

    let addr = SocketAddr::from(([127, 0, 0, 1], config.server.http_port));
    let listener = TcpListener::bind(addr).await.unwrap();

    info!("Server listening on {}", addr);

    axum::serve(listener, app).await.unwrap();
}
