use axum::{
    routing::get,
    Router,
};
use std::net::SocketAddr;
use tokio::net::TcpListener;
use tower_http::services::ServeDir;
use tracing::info;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

mod config;
mod handlers;
mod protocol;
mod session;

use config::Config;

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

    let app = Router::new()
        .route("/ws/app", get(handlers::handle_app_ws))
        .route("/ws/dashboard", get(handlers::handle_dashboard_ws))
        .nest_service("/", ServeDir::new(&config.dashboard.static_dir));

    let addr = SocketAddr::from(([127, 0, 0, 1], config.server.http_port));
    let listener = TcpListener::bind(addr).await.unwrap();

    info!("Server listening on {}", addr);

    axum::serve(listener, app).await.unwrap();
}
