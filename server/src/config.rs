use serde::Deserialize;
use std::fs;
use std::path::PathBuf;

#[derive(Debug, Clone, Deserialize)]
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

#[derive(Debug, Clone, Deserialize)]
pub struct ServerConfig {
    #[serde(default = "default_websocket_port")]
    pub websocket_port: u16,
    #[serde(default = "default_http_port")]
    pub http_port: u16,
    #[serde(default = "default_bind_address")]
    pub bind_address: String,
    #[serde(default = "default_max_connections")]
    pub max_connections: usize,
    #[serde(default = "default_udp_discovery")]
    pub udp_discovery_enabled: bool,
    #[serde(default)]
    pub tls: TlsConfig,
}

#[derive(Debug, Clone, Deserialize)]
pub struct TlsConfig {
    #[serde(default = "default_tls_enabled")]
    pub enabled: bool,
    #[serde(default = "default_cert_path")]
    pub cert_path: String,
    #[serde(default = "default_key_path")]
    pub key_path: String,
    #[serde(default = "default_auto_generate")]
    pub auto_generate: bool,
}

#[derive(Debug, Clone, Deserialize)]
pub struct SessionConfig {
    #[serde(default = "default_ended_session_ttl")]
    pub ended_session_ttl_seconds: u64,
    #[serde(default = "default_data_buffer_size")]
    pub data_buffer_size: usize,
    #[serde(default = "default_log_buffer_size")]
    pub log_buffer_size: usize,
}

#[derive(Debug, Clone, Deserialize)]
pub struct LoggingConfig {
    #[serde(default = "default_log_level")]
    pub level: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct DashboardConfig {
    #[serde(default = "default_static_dir")]
    pub static_dir: String,
}

fn default_websocket_port() -> u16 {
    8889
}

fn default_http_port() -> u16 {
    8880
}

fn default_bind_address() -> String {
    "127.0.0.1".to_string()
}

fn default_max_connections() -> usize {
    100
}

fn default_udp_discovery() -> bool {
    true
}

fn default_tls_enabled() -> bool {
    true
}

fn default_cert_path() -> String {
    let base = dirs::data_local_dir()
        .unwrap_or_else(|| std::path::PathBuf::from("."))
        .join("androidoscopy");
    base.join("cert.pem").to_string_lossy().to_string()
}

fn default_key_path() -> String {
    let base = dirs::data_local_dir()
        .unwrap_or_else(|| std::path::PathBuf::from("."))
        .join("androidoscopy");
    base.join("key.pem").to_string_lossy().to_string()
}

fn default_auto_generate() -> bool {
    true
}

fn default_ended_session_ttl() -> u64 {
    3600
}

fn default_data_buffer_size() -> usize {
    1000
}

fn default_log_buffer_size() -> usize {
    50000
}

fn default_log_level() -> String {
    "info".to_string()
}

fn default_static_dir() -> String {
    "../dashboard/dist".to_string()
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
            udp_discovery_enabled: default_udp_discovery(),
            tls: TlsConfig::default(),
        }
    }
}

impl Default for TlsConfig {
    fn default() -> Self {
        Self {
            enabled: default_tls_enabled(),
            cert_path: default_cert_path(),
            key_path: default_key_path(),
            auto_generate: default_auto_generate(),
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
        assert_eq!(config.server.http_port, 8880);
        assert_eq!(config.server.websocket_port, 8889);
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
