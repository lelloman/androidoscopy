mod logging;
use axum_server::tls_rustls::RustlsConfig;
use clap::{Parser, Subcommand};
use simple_server::axum::{routing::get, Router};
use simple_server::lifecycle::{Lifecycle, ShutdownOptions, Signals};
use std::net::SocketAddr;
use std::time::Duration;
use tracing::{info, warn};

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
#[command(name = "androidoscopy")]
#[command(about = "Androidoscopy development server", long_about = None)]
struct Cli {
    #[command(subcommand)]
    command: Option<Commands>,
}

#[derive(Subcommand)]
enum Commands {
    /// Run the server (default)
    Run,
    /// Run the isolated legacy v1 WebSocket server (no v2 sessions)
    Legacy,
    /// List discovered devices and pairing codes
    Devices,
    /// Connect to a device's LAN IP:port
    Connect { address: String },
    /// Disconnect an app instance
    Disconnect { device: String },
    /// Forget a paired device (also forget this PC on the phone before pairing again)
    Forget { device: String },
    /// Expose one app's tools through MCP stdio
    Mcp {
        #[arg(long)]
        device: String,
    },
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
        Commands::Run => {
            if let Err(e) =
                androidoscopy::control::run(androidoscopy::Config::load().unwrap_or_default()).await
            {
                eprintln!("{e:#}");
                std::process::exit(1);
            }
        }
        Commands::Legacy => {
            if let Err(error) = run_server().await {
                eprintln!("{error:#}");
                std::process::exit(1);
            }
        }
        Commands::Mcp { device } => {
            if let Err(e) = androidoscopy::mcp::run(device).await {
                eprintln!("{e:#}");
                std::process::exit(1);
            }
        }
        Commands::Devices => control_request(reqwest::Method::GET, "devices", None).await,
        Commands::Connect { address } => {
            control_request(
                reqwest::Method::POST,
                "connect",
                Some(serde_json::json!({"address":address})),
            )
            .await
        }
        Commands::Disconnect { device } => {
            control_request(
                reqwest::Method::POST,
                "disconnect",
                Some(serde_json::json!({"device":device})),
            )
            .await
        }
        Commands::Forget { device } => {
            control_request(
                reqwest::Method::POST,
                "forget",
                Some(serde_json::json!({"device":device})),
            )
            .await
        }
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

async fn control_request(method: reqwest::Method, path: &str, body: Option<serde_json::Value>) {
    match androidoscopy::control::request(method, path, body).await {
        Ok(value) => println!("{}", serde_json::to_string_pretty(&value).unwrap()),
        Err(error) => {
            eprintln!("{error:#}");
            std::process::exit(1);
        }
    }
}

async fn run_server() -> anyhow::Result<()> {
    let _ = rustls::crypto::ring::default_provider().install_default();
    logging::init(
        tracing_subscriber::EnvFilter::try_from_default_env()
            .unwrap_or_else(|_| "androidoscopy=debug,tower_http=debug".into()),
    )
    .expect("failed to initialize logging");
    let signals = Signals::install()?;
    let config = Config::load().unwrap_or_default();
    let mut lifecycle = Lifecycle::new(ShutdownOptions {
        grace_period: Duration::from_secs(30),
    });
    let shutdown = lifecycle.shutdown();
    let mut state = AppState::new(config.clone());
    state.shutdown = shutdown.clone();
    if config.server.udp_discovery_enabled {
        let stop = shutdown.clone();
        lifecycle.service("udp-discovery", async move {
            tokio::select! {
                _ = stop.requested() => Ok::<_, std::io::Error>(()),
                _ = discovery::broadcast_presence(config.server.websocket_port, config.server.http_port) =>
                    Err(std::io::Error::other("UDP discovery exited")),
            }
        })?;
    }
    let bind_addr: std::net::IpAddr = config
        .server
        .bind_address
        .parse()
        .unwrap_or([127, 0, 0, 1].into());
    let http_addr = SocketAddr::from((bind_addr, config.server.http_port));
    let http_app = Router::new()
        .route("/ws/dashboard", get(handlers::handle_dashboard_ws))
        .fallback(dashboard::serve_embedded)
        .with_state(state.clone());
    let listener = simple_server::http::bind(http_addr).await?;
    info!("Dashboard: http://{}", http_addr);
    lifecycle.service(
        "dashboard-http",
        simple_server::http::serve(listener, http_app, shutdown.clone()),
    )?;
    let wss_addr = SocketAddr::from((bind_addr, config.server.websocket_port));
    let app = Router::new()
        .route("/ws/app", get(handlers::handle_app_ws))
        .with_state(state.clone());
    let tls_config = if config.server.tls.enabled {
        match tls::ensure_certificates(&config.server.tls) {
            Ok((cert, key)) => Some(RustlsConfig::from_pem_file(cert, key).await?),
            Err(error) => {
                warn!(%error, "Failed to setup TLS certificates. Falling back to WS.");
                None
            }
        }
    } else {
        None
    };
    let listener = simple_server::http::bind(wss_addr).await?;
    if let Some(tls_config) = tls_config {
        let handle = axum_server::Handle::new();
        let drain_handle = handle.clone();
        let stop = shutdown.clone();
        lifecycle.service("tls-shutdown", async move {
            stop.requested().await;
            drain_handle.graceful_shutdown(None);
            Ok::<_, std::io::Error>(())
        })?;
        info!("Android app: wss://{}/ws/app", wss_addr);
        lifecycle.service(
            "app-wss",
            axum_server::from_tcp_rustls(listener.into_std()?, tls_config)
                .handle(handle)
                .serve(app.into_make_service()),
        )?;
    } else {
        info!("Android app: ws://{}/ws/app", wss_addr);
        lifecycle.service(
            "app-ws",
            simple_server::http::serve(listener, app, shutdown),
        )?;
    }
    let report = lifecycle
        .run(signals.wait(), async {
            state.tasks.close();
            state.tasks.wait().await;
            Ok::<_, std::io::Error>(())
        })
        .await?;
    info!(?report.reason, "Graceful shutdown complete");
    Ok(())
}
