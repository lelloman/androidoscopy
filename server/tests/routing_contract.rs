use androidoscopy::{dashboard, AppState, Config};
use simple_server::web::{routing::get, Router};

#[tokio::test]
async fn dashboard_preserves_assets_spa_fallback_and_head() {
    let app = Router::new()
        .route(
            "/ws/dashboard",
            get(androidoscopy::handlers::handle_dashboard_ws),
        )
        .fallback(dashboard::serve_embedded)
        .with_state(AppState::new(Config::default()));
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = listener.local_addr().unwrap();
    let task = tokio::spawn(async move {
        simple_server::web::serve(listener, app, simple_server::lifecycle::Shutdown::new())
            .await
            .unwrap()
    });
    let client = reqwest::Client::new();
    let base = format!("http://{address}");
    let index = client.get(&base).send().await.unwrap();
    assert_eq!(index.status(), 200);
    assert!(index.headers()["content-type"]
        .to_str()
        .unwrap()
        .starts_with("text/html"));
    let index = index.text().await.unwrap();
    assert!(index.contains("<html"));
    let fallback = client
        .get(format!("{base}/some/client/route"))
        .send()
        .await
        .unwrap();
    assert_eq!(fallback.status(), 200);
    assert_eq!(fallback.text().await.unwrap(), index);
    let head = client.head(&base).send().await.unwrap();
    assert_eq!(head.status(), 200);
    assert!(head.bytes().await.unwrap().is_empty());
    let asset = client.get(format!("{base}/vite.svg")).send().await.unwrap();
    assert_eq!(asset.status(), 200);
    assert!(asset.headers()["content-type"]
        .to_str()
        .unwrap()
        .starts_with("image/svg+xml"));
    let upgrade = client
        .get(format!("{base}/ws/dashboard"))
        .send()
        .await
        .unwrap();
    assert_eq!(upgrade.status(), 400);
    task.abort();
}
