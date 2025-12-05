use serde::Deserialize;
use std::fs;
use std::path::PathBuf;

#[derive(Debug, Deserialize)]
pub struct Config {
    #[serde(default)]
    pub server: ServerConfig,
    #[serde(default)]
    pub session: SessionConfig,
    #[serde(default)]
    pub logging: LoggingConfig,
    #[serde(default)]
    pub dashboard: DashboardConfig,
}

#[derive(Debug, Deserialize)]
pub struct ServerConfig {
    #[serde(default = "default_websocket_port")]
    pub websocket_port: u16,
    #[serde(default = "default_http_port")]
    pub http_port: u16,
    #[serde(default = "default_bind_address")]
    pub bind_address: String,
    #[serde(default = "default_max_connections")]
    pub max_connections: usize,
}

#[derive(Debug, Deserialize)]
pub struct SessionConfig {
    #[serde(default = "default_ended_session_ttl")]
    pub ended_session_ttl_seconds: u64,
    #[serde(default = "default_data_buffer_size")]
    pub data_buffer_size: usize,
    #[serde(default = "default_log_buffer_size")]
    pub log_buffer_size: usize,
}

#[derive(Debug, Deserialize)]
pub struct LoggingConfig {
    #[serde(default = "default_log_level")]
    pub level: String,
}

#[derive(Debug, Deserialize)]
pub struct DashboardConfig {
    #[serde(default = "default_static_dir")]
    pub static_dir: String,
}

fn default_websocket_port() -> u16 {
    9999
}

fn default_http_port() -> u16 {
    8080
}

fn default_bind_address() -> String {
    "127.0.0.1".to_string()
}

fn default_max_connections() -> usize {
    100
}

fn default_ended_session_ttl() -> u64 {
    3600
}

fn default_data_buffer_size() -> usize {
    1000
}

fn default_log_buffer_size() -> usize {
    5000
}

fn default_log_level() -> String {
    "info".to_string()
}

fn default_static_dir() -> String {
    "./static".to_string()
}

impl Default for Config {
    fn default() -> Self {
        Self {
            server: ServerConfig::default(),
            session: SessionConfig::default(),
            logging: LoggingConfig::default(),
            dashboard: DashboardConfig::default(),
        }
    }
}

impl Default for ServerConfig {
    fn default() -> Self {
        Self {
            websocket_port: default_websocket_port(),
            http_port: default_http_port(),
            bind_address: default_bind_address(),
            max_connections: default_max_connections(),
        }
    }
}

impl Default for SessionConfig {
    fn default() -> Self {
        Self {
            ended_session_ttl_seconds: default_ended_session_ttl(),
            data_buffer_size: default_data_buffer_size(),
            log_buffer_size: default_log_buffer_size(),
        }
    }
}

impl Default for LoggingConfig {
    fn default() -> Self {
        Self {
            level: default_log_level(),
        }
    }
}

impl Default for DashboardConfig {
    fn default() -> Self {
        Self {
            static_dir: default_static_dir(),
        }
    }
}

impl Config {
    pub fn load() -> Result<Self, Box<dyn std::error::Error>> {
        let config_path = Self::config_path();

        if config_path.exists() {
            let content = fs::read_to_string(&config_path)?;
            let config: Config = toml::from_str(&content)?;
            Ok(config)
        } else {
            Ok(Config::default())
        }
    }

    fn config_path() -> PathBuf {
        dirs::home_dir()
            .unwrap_or_else(|| PathBuf::from("."))
            .join(".androidoscopy")
            .join("config.toml")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_config() {
        let config = Config::default();
        assert_eq!(config.server.http_port, 8080);
        assert_eq!(config.server.websocket_port, 9999);
        assert_eq!(config.server.bind_address, "127.0.0.1");
    }

    #[test]
    fn test_parse_config() {
        let toml_content = r#"
            [server]
            http_port = 9000
            websocket_port = 9998

            [session]
            data_buffer_size = 500
        "#;

        let config: Config = toml::from_str(toml_content).unwrap();
        assert_eq!(config.server.http_port, 9000);
        assert_eq!(config.server.websocket_port, 9998);
        assert_eq!(config.session.data_buffer_size, 500);
    }
}
