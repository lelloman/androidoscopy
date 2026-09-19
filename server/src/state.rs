use std::sync::Arc;
use tokio::sync::Mutex;

use crate::config::Config;
use crate::session::SessionManager;

#[derive(Clone)]
pub struct AppState {
    pub session_manager: Arc<Mutex<SessionManager>>,
    pub config: Arc<Config>,
    pub shutdown: simple_server::lifecycle::Shutdown,
    pub tasks: tokio_util::task::TaskTracker,
}

impl AppState {
    pub fn new(config: Config) -> Self {
        let session_manager = SessionManager::new(
            config.session.data_buffer_size,
            config.session.log_buffer_size,
            config.session.ended_session_ttl_seconds,
        );

        Self {
            session_manager: Arc::new(Mutex::new(session_manager)),
            config: Arc::new(config),
            shutdown: simple_server::lifecycle::Shutdown::new(),
            tasks: tokio_util::task::TaskTracker::new(),
        }
    }
}
