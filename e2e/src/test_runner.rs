//! Test runner for orchestrating E2E tests.
//!
//! Provides utilities for starting the server and connecting mock clients.

use std::net::TcpListener;
use std::time::Duration;
use tokio::time::sleep;

/// Configuration for test environment.
pub struct TestConfig {
    pub server_host: String,
    pub websocket_port: u16,
    pub http_port: u16,
}

impl Default for TestConfig {
    fn default() -> Self {
        Self {
            server_host: "127.0.0.1".to_string(),
            websocket_port: find_available_port(),
            http_port: find_available_port(),
        }
    }
}

impl TestConfig {
    /// Returns the WebSocket URL for app connections.
    pub fn app_ws_url(&self) -> String {
        format!("ws://{}:{}/ws/app", self.server_host, self.websocket_port)
    }

    /// Returns the WebSocket URL for dashboard connections.
    pub fn dashboard_ws_url(&self) -> String {
        format!("ws://{}:{}/ws/dashboard", self.server_host, self.websocket_port)
    }

    /// Returns the HTTP base URL.
    pub fn http_url(&self) -> String {
        format!("http://{}:{}", self.server_host, self.http_port)
    }
}

/// Test runner for managing test lifecycle.
pub struct TestRunner {
    config: TestConfig,
}

impl TestRunner {
    /// Creates a new test runner with default configuration.
    pub fn new() -> Self {
        Self {
            config: TestConfig::default(),
        }
    }

    /// Creates a new test runner with custom configuration.
    pub fn with_config(config: TestConfig) -> Self {
        Self { config }
    }

    /// Gets the test configuration.
    pub fn config(&self) -> &TestConfig {
        &self.config
    }

    /// Waits for a condition to be true with timeout.
    pub async fn wait_for<F, Fut>(
        &self,
        condition: F,
        timeout: Duration,
        poll_interval: Duration,
    ) -> bool
    where
        F: Fn() -> Fut,
        Fut: std::future::Future<Output = bool>,
    {
        let deadline = tokio::time::Instant::now() + timeout;
        loop {
            if condition().await {
                return true;
            }
            if tokio::time::Instant::now() >= deadline {
                return false;
            }
            sleep(poll_interval).await;
        }
    }
}

impl Default for TestRunner {
    fn default() -> Self {
        Self::new()
    }
}

/// Finds an available TCP port.
fn find_available_port() -> u16 {
    TcpListener::bind("127.0.0.1:0")
        .expect("Failed to bind to random port")
        .local_addr()
        .expect("Failed to get local address")
        .port()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_config_urls() {
        let config = TestConfig {
            server_host: "localhost".to_string(),
            websocket_port: 9999,
            http_port: 8080,
        };

        assert_eq!(config.app_ws_url(), "ws://localhost:9999/ws/app");
        assert_eq!(config.dashboard_ws_url(), "ws://localhost:9999/ws/dashboard");
        assert_eq!(config.http_url(), "http://localhost:8080");
    }

    #[test]
    fn test_find_available_port() {
        let port1 = find_available_port();
        let port2 = find_available_port();
        assert!(port1 > 0);
        assert!(port2 > 0);
        // Ports should be different (with high probability)
        // Note: This is probabilistic, but extremely unlikely to fail
    }
}
