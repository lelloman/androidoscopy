use std::{sync::Arc, time::Duration};

use androidoscopy::{create_router, AppState, Config};
use futures::{SinkExt, StreamExt};
use simple_server::lifecycle::Shutdown;
use simple_server::web::tls::{self, TlsConfig};
use tokio_tungstenite::{connect_async_tls_with_config, tungstenite::Message, Connector};

#[tokio::test]
async fn app_can_register_over_tls() {
    let _ = rustls::crypto::ring::default_provider().install_default();
    // Trust only this test's certificate: exercise TLS verification and the
    // WebSocket upgrade through the same shared TLS adapter used in production.
    let certificate = rcgen::generate_simple_self_signed(vec!["localhost".into()]).unwrap();
    let tls = TlsConfig::from_pem(
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

    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = listener.local_addr().unwrap();
    let shutdown = Shutdown::new();
    let server = tls::serve(
        listener,
        create_router(AppState::new(Config::default())),
        tls,
        shutdown.clone(),
    );
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
    shutdown.request();
    tokio::time::timeout(Duration::from_secs(5), task)
        .await
        .expect("TLS server did not stop after shutdown")
        .unwrap()
        .unwrap();
}
