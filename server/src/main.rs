use axum::{routing::get, Router};
use axum_server::tls_rustls::RustlsConfig;
use clap::{Parser, Subcommand};
use std::net::SocketAddr;
use tokio::net::TcpListener;
use tracing::{info, warn};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

mod config;
mod dashboard;
mod discovery;
mod handlers;
mod protocol;
mod service;
mod session;
mod state;
mod tls;

use config::Config;
use state::AppState;

#[derive(Parser)]
#[command(name = "androidoscopy-server")]
#[command(about = "Androidoscopy development server", long_about = None)]
struct Cli {
    #[command(subcommand)]
    command: Option<Commands>,
}

#[derive(Subcommand)]
enum Commands {
    /// Run the server (default)
    Run,
    /// Install as a systemd user service
    Install,
    /// Uninstall the systemd user service
    Uninstall,
    /// Show service status
    Status,
}

#[tokio::main]
async fn main() {
    let cli = Cli::parse();

    match cli.command.unwrap_or(Commands::Run) {
        Commands::Run => run_server().await,
        Commands::Install => {
            if let Err(e) = service::install() {
                eprintln!("Installation failed: {}", e);
                std::process::exit(1);
            }
        }
        Commands::Uninstall => {
            if let Err(e) = service::uninstall() {
                eprintln!("Uninstallation failed: {}", e);
                std::process::exit(1);
            }
        }
        Commands::Status => {
            if let Err(e) = service::status() {
                eprintln!("Failed to get status: {}", e);
                std::process::exit(1);
            }
        }
    }
}

async fn run_server() {
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

    let bind_addr: std::net::IpAddr = config
        .server
        .bind_address
        .parse()
        .unwrap_or([127, 0, 0, 1].into());

    // Start HTTP server for dashboard with embedded assets
    let http_addr = SocketAddr::from((bind_addr, config.server.http_port));
    let http_state = state.clone();

    tokio::spawn(async move {
        let http_app = Router::new()
            .route("/ws/dashboard", get(handlers::handle_dashboard_ws))
            .fallback(dashboard::serve_embedded)
            .with_state(http_state);

        let listener = TcpListener::bind(http_addr).await.unwrap();
        info!("Dashboard: http://{}", http_addr);
        axum::serve(listener, http_app).await.unwrap();
    });

    // Start WSS server for Android app connections (TLS, no cleartext needed)
    let wss_addr = SocketAddr::from((bind_addr, config.server.websocket_port));

    if config.server.tls.enabled {
        let (cert_path, key_path) = match tls::ensure_certificates(&config.server.tls) {
            Ok(paths) => paths,
            Err(e) => {
                warn!(
                    "Failed to setup TLS certificates: {}. Falling back to WS.",
                    e
                );
                start_ws_server(wss_addr, state).await;
                return;
            }
        };

        let tls_config = RustlsConfig::from_pem_file(&cert_path, &key_path)
            .await
            .expect("Failed to load TLS configuration");

        let wss_app = Router::new()
            .route("/ws/app", get(handlers::handle_app_ws))
            .with_state(state);

        info!("Android app: wss://{}/ws/app", wss_addr);

        axum_server::bind_rustls(wss_addr, tls_config)
            .serve(wss_app.into_make_service())
            .await
            .unwrap();
    } else {
        warn!("TLS is disabled - Android apps will need cleartext permission");
        start_ws_server(wss_addr, state).await;
    }
}

async fn start_ws_server(addr: SocketAddr, state: AppState) {
    let app = Router::new()
        .route("/ws/app", get(handlers::handle_app_ws))
        .with_state(state);

    let listener = TcpListener::bind(addr).await.unwrap();
    info!("Android app: ws://{}/ws/app", addr);
    axum::serve(listener, app).await.unwrap();
}
