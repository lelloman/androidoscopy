use serde::Serialize;
use std::net::SocketAddr;
use std::time::Duration;
use tokio::net::UdpSocket;
use tracing::{error, info};

const DISCOVERY_PORT: u16 = 9998;
const BROADCAST_INTERVAL: Duration = Duration::from_secs(5);

#[derive(Debug, Serialize)]
struct DiscoveryMessage {
    service: &'static str,
    version: &'static str,
    websocket_port: u16,
    http_port: u16,
}

pub async fn broadcast_presence(websocket_port: u16, http_port: u16) {
    let socket = match UdpSocket::bind("0.0.0.0:0").await {
        Ok(s) => s,
        Err(e) => {
            error!("Failed to bind UDP socket for discovery: {}", e);
            return;
        }
    };

    if let Err(e) = socket.set_broadcast(true) {
        error!("Failed to enable broadcast on UDP socket: {}", e);
        return;
    }

    let message = DiscoveryMessage {
        service: "androidoscopy",
        version: "1.0",
        websocket_port,
        http_port,
    };

    let json = match serde_json::to_string(&message) {
        Ok(j) => j,
        Err(e) => {
            error!("Failed to serialize discovery message: {}", e);
            return;
        }
    };

    let broadcast_addr: SocketAddr = format!("255.255.255.255:{}", DISCOVERY_PORT)
        .parse()
        .unwrap();

    info!(
        "Starting UDP discovery broadcast on port {} (broadcasting to {})",
        DISCOVERY_PORT, broadcast_addr
    );

    loop {
        match socket.send_to(json.as_bytes(), broadcast_addr).await {
            Ok(_) => {
                // Successfully sent broadcast
            }
            Err(e) => {
                // Some networks don't allow broadcast, just log and continue
                error!("Failed to send discovery broadcast: {}", e);
            }
        }

        tokio::time::sleep(BROADCAST_INTERVAL).await;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_discovery_message_serialization() {
        let message = DiscoveryMessage {
            service: "androidoscopy",
            version: "1.0",
            websocket_port: 9999,
            http_port: 8080,
        };

        let json = serde_json::to_string(&message).unwrap();
        assert!(json.contains("androidoscopy"));
        assert!(json.contains("9999"));
        assert!(json.contains("8080"));
    }
}
