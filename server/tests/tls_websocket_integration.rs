use std::{sync::Arc, time::Duration};

use androidoscopy::{create_router, AppState, Config};
use axum_server::tls_rustls::RustlsConfig;
use futures::{SinkExt, StreamExt};
use tokio_tungstenite::{connect_async_tls_with_config, tungstenite::Message, Connector};

#[tokio::test]
async fn app_can_register_over_tls() {
    let _ = rustls::crypto::ring::default_provider().install_default();
    // Trust only this test's certificate: exercise TLS verification and the
    // Axum upgrade through the same axum-server adapter used in production.
    let certificate = rcgen::generate_simple_self_signed(vec!["localhost".into()]).unwrap();
    let tls = RustlsConfig::from_pem(
        certificate.cert.pem().into_bytes(),
        certificate.key_pair.serialize_pem().into_bytes(),
    )
    .await
    .unwrap();
    let mut roots = rustls::RootCertStore::empty();
    roots.add(certificate.cert.der().clone()).unwrap();
    let client = rustls::ClientConfig::builder()
        .with_root_certificates(roots)
        .with_no_client_auth();

    let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap();
    let handle = axum_server::Handle::new();
    let server = axum_server::from_tcp_rustls(listener, tls)
        .handle(handle.clone())
        .serve(create_router(AppState::new(Config::default())).into_make_service());
    let task = tokio::spawn(server);

    tokio::time::timeout(Duration::from_secs(5), async {
        let (mut socket, response) = connect_async_tls_with_config(
            format!("wss://localhost:{}/ws/app", address.port()),
            None,
            false,
            Some(Connector::Rustls(Arc::new(client))),
        )
        .await
        .unwrap();
        assert_eq!(response.status(), 101);
        let message = serde_json::json!({
            "type": "REGISTER",
            "timestamp": chrono::Utc::now(),
            "payload": {
                "protocol_version": "1.0", "app_name": "TLS test",
                "package_name": "test.tls", "version_name": "1.0",
                "device": {"manufacturer": "Test", "model": "Test", "android_version": "14", "api_level": 34, "is_emulator": true, "device_id": "tls-test"},
                "dashboard": {"sections": []}
            }
        });
        socket.send(Message::Text(message.to_string())).await.unwrap();
        let reply = socket.next().await.unwrap().unwrap().into_text().unwrap();
        let reply: serde_json::Value = serde_json::from_str(&reply).unwrap();
        assert_eq!(reply["type"], "REGISTERED");
        assert!(reply["payload"]["session_id"].as_str().is_some());
        socket.close(None).await.unwrap();
    })
    .await
    .expect("TLS registration timed out");
    handle.shutdown();
    task.await.unwrap().unwrap();
}
