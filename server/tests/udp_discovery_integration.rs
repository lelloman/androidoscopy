use std::time::Duration;

use serde::Deserialize;
use tokio::net::UdpSocket;
use tokio::time::timeout;

use androidoscopy_server::discovery::broadcast_presence;

#[derive(Debug, Deserialize)]
struct DiscoveryMessage {
    service: String,
    version: String,
    websocket_port: u16,
    http_port: u16,
}

#[tokio::test]
async fn test_udp_discovery_broadcast_and_message_format() {
    let websocket_port = 9999;
    let http_port = 8080;

    // Bind a socket to receive the broadcast
    // Use 0.0.0.0 to receive broadcasts
    let receiver = UdpSocket::bind("0.0.0.0:9998")
        .await
        .expect("Failed to bind receiver socket");

    // Start the broadcast in a separate task
    let broadcast_handle = tokio::spawn(async move {
        broadcast_presence(websocket_port, http_port).await;
    });

    // Wait for a broadcast message (with timeout)
    let mut buf = [0u8; 1024];
    let result = timeout(Duration::from_secs(10), receiver.recv_from(&mut buf)).await;

    // Verify we received a message
    let (len, _src_addr) = result
        .expect("Timeout waiting for broadcast")
        .expect("Failed to receive broadcast");

    let message_str = std::str::from_utf8(&buf[..len]).expect("Invalid UTF-8 in broadcast");
    let message: DiscoveryMessage =
        serde_json::from_str(message_str).expect("Failed to parse discovery message");

    // Verify message format
    assert_eq!(message.service, "androidoscopy");
    assert_eq!(message.version, "1.0");
    assert_eq!(message.websocket_port, websocket_port);
    assert_eq!(message.http_port, http_port);

    // Wait for a second broadcast to verify it repeats (should arrive within ~5 seconds)
    let result2 = timeout(Duration::from_secs(10), receiver.recv_from(&mut buf)).await;
    assert!(result2.is_ok(), "Second broadcast not received");

    let (len2, _) = result2.unwrap().unwrap();
    let message2: DiscoveryMessage =
        serde_json::from_slice(&buf[..len2]).expect("Failed to parse second message");
    assert_eq!(message2.service, "androidoscopy");

    // Abort the broadcast task
    broadcast_handle.abort();
}
