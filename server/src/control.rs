//! Local desktop controller. Only this authenticated loopback API can see v2 sessions.
use crate::{config::Config, lan};
use anyhow::{ensure, Context, Result};
use futures::{SinkExt, StreamExt};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use simple_server::axum::{
    self,
    extract::{ws::Message, Path, State, WebSocketUpgrade},
    http::{header, StatusCode},
    middleware::{self, Next},
    response::{IntoResponse, Response},
    routing::{get, post},
    Json, Router,
};
use simple_server::lifecycle::{Lifecycle, Shutdown, ShutdownOptions, Signals};
use simple_server::tasks::WorkTracker;
use std::{
    collections::HashMap,
    path::PathBuf,
    sync::{Arc, Mutex},
    time::Duration,
};
use tokio::{
    sync::{broadcast, mpsc, oneshot},
    time::timeout,
};
use tokio_util::{sync::CancellationToken, task::AbortOnDropHandle};

#[derive(Clone, Serialize, Deserialize)]
pub struct Credential {
    pub secret: String,
    pub fingerprint: String,
    pub session: String,
    pub remember: bool,
}
#[derive(Default)]
struct Device {
    address: String,
    session: String,
    info: Value,
    tools: Vec<Value>,
    data: Value,
    logs: Vec<Value>,
    status: String,
    code: Option<String>,
    error: Option<String>,
    sender: Option<mpsc::Sender<Value>>,
    cancel: CancellationToken,
    ended: Option<std::time::Instant>,
    expires: Option<std::time::Instant>,
}
struct Inner {
    devices: HashMap<String, Device>,
    credentials: HashMap<String, Credential>,
    pending: HashMap<(String, String), oneshot::Sender<Value>>,
}
#[derive(Clone)]
pub struct Controller {
    inner: Arc<Mutex<Inner>>,
    events: broadcast::Sender<Value>,
    pub token: String,
    peer: String,
    shutdown: Shutdown,
    tasks: WorkTracker,
}

pub fn directory() -> PathBuf {
    std::env::var_os("ANDROIDOSCOPY_STATE_DIR")
        .map(PathBuf::from)
        .unwrap_or_else(|| {
            dirs::data_local_dir()
                .unwrap_or_else(|| PathBuf::from("."))
                .join("androidoscopy")
        })
}
fn private_write(path: PathBuf, value: &[u8]) -> Result<()> {
    use std::io::Write;
    let mut options = std::fs::OpenOptions::new();
    options.write(true).create(true).truncate(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    let mut file = options.open(path)?;
    file.write_all(value)?;
    Ok(())
}
pub fn load_token() -> Result<String> {
    Ok(std::fs::read_to_string(directory().join("control-token"))?)
}
impl Controller {
    pub fn new() -> Result<Self> {
        std::fs::create_dir_all(directory())?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            std::fs::set_permissions(directory(), std::fs::Permissions::from_mode(0o700))?;
        }
        let peer_file = directory().join("peer-id");
        let peer = std::fs::read_to_string(&peer_file)
            .unwrap_or_else(|_| uuid::Uuid::new_v4().to_string());
        private_write(peer_file, peer.as_bytes())?;
        let credentials = std::fs::read(directory().join("debug-peers.json"))
            .ok()
            .and_then(|v| serde_json::from_slice(&v).ok())
            .unwrap_or_default();
        Ok(Self {
            inner: Arc::new(Mutex::new(Inner {
                devices: HashMap::new(),
                credentials,
                pending: HashMap::new(),
            })),
            events: broadcast::channel(256).0,
            token: hex::encode(lan::random()),
            peer,
            shutdown: Shutdown::new(),
            tasks: WorkTracker::new(),
        })
    }
    pub fn devices(&self) -> Value {
        let inner = self.inner.lock().unwrap();
        Value::Array(
            inner
                .devices
                .iter()
                .map(|(id, d)| {
                    json!({"id":id,"address":d.address,"session":d.session,
            "status":d.status,"code":d.code,"error":d.error,"info":d.info,"tools":d.tools})
                })
                .collect(),
        )
    }
    pub fn session(&self, device: &str) -> Value {
        self.inner.lock().unwrap().devices.get(device).map(|d| json!({"session":d.session,"status":d.status,
            "tools":if d.sender.is_some() {d.tools.clone()} else {vec![]},"data":d.data,"logs":d.logs})).unwrap_or(json!({"status":"disconnected","tools":[]}))
    }
    fn emit(&self, event: Value) {
        let _ = self.events.send(event);
    }
    fn view(id: &str, d: &Device) -> Value {
        json!({"session_id":d.session,"app_name":d.info["app_name"],"package_name":d.info["package_name"],
            "version_name":"","device":{"device_id":id,"model":"LAN device","manufacturer":"","android_version":"","api_level":0,"is_emulator":false},
            "dashboard":d.info["dashboard"],"started_at":d.info["started_at"],"ended_at":if d.sender.is_none() {json!(chrono::Utc::now())} else {Value::Null},"latest_data":d.data,"recent_logs":d.logs})
    }
    fn sync(&self) -> Value {
        let inner = self.inner.lock().unwrap();
        json!({"type":"SYNC","payload":{"sessions":inner.devices.iter().filter(|(_,d)| !d.session.is_empty() && d.info.is_object())
            .map(|(id,d)| Self::view(id,d)).collect::<Vec<_>>()}})
    }
    pub async fn discover(&self) -> Result<()> {
        let mdns = mdns_sd::ServiceDaemon::new()?;
        let receiver = mdns.browse("_androidoscopy._tcp.local.")?;
        let this = self.clone();
        let result = async {
            loop {
                let event = tokio::select! {
                    biased;
                    _ = this.shutdown.requested() => break,
                    event = receiver.recv_async() => event?,
                };
                if let mdns_sd::ServiceEvent::ServiceResolved(info) = event {
                    if let (Some(id), Some(ip)) = (
                        info.get_property_val_str("id"),
                        info.get_addresses().iter().find(|ip| ip.is_ipv4()),
                    ) {
                        let address = format!("{}:{}", ip, info.get_port());
                        let mut inner = this.inner.lock().unwrap();
                        let d = inner.devices.entry(id.to_string()).or_default();
                        d.address = address;
                        if d.status.is_empty() {
                            d.status = "available".into();
                        }
                    }
                }
            }
            Ok::<_, anyhow::Error>(())
        }
        .await;
        mdns.shutdown()?.recv_async().await?;
        result
    }
    pub fn connect(&self, address: String) -> Result<()> {
        ensure!(!self.shutdown.is_requested(), "server is shutting down");
        // Connection creation is restricted to the authenticated local controller.
        ensure!(
            address.len() < 256 && address.parse::<std::net::SocketAddr>().is_ok(),
            "expected IP:port"
        );
        let tracked = self.tasks.try_acquire("controller-connection")?;
        {
            let mut inner = self.inner.lock().unwrap();
            ensure!(
                !inner.devices.values().any(|d| d.address == address
                    && matches!(d.status.as_str(), "connecting" | "pairing" | "connected")),
                "device already attached"
            );
            let d = inner
                .devices
                .entry(format!("pending:{address}"))
                .or_default();
            d.address = address.clone();
            d.status = "connecting".into();
        }
        let this = self.clone();
        tokio::spawn(async move {
            let _tracked = tracked;
            let result = tokio::select! {
                biased;
                _ = this.shutdown.requested() => Ok(()),
                result = this.connect_loop(address.clone()) => result,
            };
            if let Err(error) = result {
                tracing::warn!(%address, %error, "device connection ended");
                let mut inner = this.inner.lock().unwrap();
                for d in inner.devices.values_mut().filter(|d| d.address == address) {
                    d.status = "disconnected".into();
                    d.error = Some(error.to_string());
                    d.code = None;
                }
            }
        });
        Ok(())
    }
    async fn connect_loop(&self, mut address: String) -> Result<()> {
        let mut identity: Option<String> = None;
        let mut previous_session: Option<String> = None;
        let mut release = false;
        let mut retry = false;
        let mut lifetime: Option<CancellationToken> = None;
        loop {
            if lifetime.as_ref().is_some_and(|token| token.is_cancelled()) {
                return Ok(());
            }
            if let Some(id) = &identity {
                let mut inner = self.inner.lock().unwrap();
                if inner
                    .devices
                    .get(id)
                    .is_some_and(|d| d.expires.is_some_and(|t| t <= std::time::Instant::now()))
                {
                    inner.credentials.remove(id);
                    anyhow::bail!("SESSION_EXPIRED: activate and connect again");
                }
                if let Some(d) = inner.devices.get(id) {
                    if d.cancel.is_cancelled() {
                        return Ok(());
                    }
                    address = d.address.clone();
                }
            }
            let mut connection =
                match timeout(Duration::from_secs(10), lan::Connection::open(&address)).await {
                    Ok(Ok(c)) => c,
                    _ if retry => {
                        tokio::time::sleep(Duration::from_secs(2)).await;
                        continue;
                    }
                    Ok(Err(error)) => anyhow::bail!("Could not connect to {address}: {error:#}"),
                    Err(_) => anyhow::bail!("Connection timed out at {address}"),
                };
            let id = connection.hello["device"]
                .as_str()
                .context("missing device identity")?
                .to_string();
            let session = connection.hello["session"]
                .as_str()
                .context("missing session")?
                .to_string();
            if let Some(expected) = &identity {
                ensure!(*expected == id, "device identity changed")
            }
            if release && previous_session.as_ref() != Some(&session) {
                anyhow::bail!("SESSION_EXPIRED: activate and connect again")
            }
            identity = Some(id.clone());
            let cancel = {
                let mut inner = self.inner.lock().unwrap();
                inner.devices.remove(&format!("pending:{address}"));
                let d = inner.devices.entry(id.clone()).or_default();
                if !retry {
                    ensure!(
                        d.sender.is_none() && d.status != "pairing",
                        "device already attached"
                    );
                    d.cancel.cancel();
                    d.cancel = CancellationToken::new();
                }
                d.address = address.clone();
                d.status = "connecting".into();
                d.error = None;
                d.cancel.clone()
            };
            lifetime = Some(cancel.clone());
            let stored = self
                .inner
                .lock()
                .unwrap()
                .credentials
                .get(&id)
                .cloned()
                .filter(|c| c.remember || c.session == session);
            let secret = stored
                .as_ref()
                .map(|c| hex::decode(&c.secret))
                .transpose()?;
            if let Some(stored) = &stored {
                ensure!(
                    stored.fingerprint == connection.fingerprint,
                    "Device certificate changed; forget the desktop pairing before reconnecting"
                );
                connection
                    .resume(&self.peer, secret.as_ref().unwrap())
                    .await?;
            } else {
                ensure!(!retry, "Session authorization lost; connect again");
                let code = connection.begin_pairing(&self.peer).await?;
                let mut inner = self.inner.lock().unwrap();
                let d = inner.devices.get_mut(&id).unwrap();
                d.status = "pairing".into();
                d.code = Some(code);
            }
            let authorized = tokio::select! {
                _ = cancel.cancelled() => return Ok(()),
                result = timeout(Duration::from_secs(65), connection.authorized(secret.as_deref())) => result??,
            };
            release = authorized["remember"] != true;
            previous_session = Some(session.clone());
            let credential = Credential {
                secret: authorized["credential"]
                    .as_str()
                    .context("missing credential")?
                    .into(),
                fingerprint: connection.fingerprint,
                session: session.clone(),
                remember: !release,
            };
            let (tx, rx) = mpsc::channel(32);
            {
                let mut inner = self.inner.lock().unwrap();
                inner.credentials.insert(id.clone(), credential);
                let persisted: HashMap<_, _> = inner
                    .credentials
                    .iter()
                    .filter(|(_, c)| c.remember)
                    .collect();
                private_write(
                    directory().join("debug-peers.json"),
                    &serde_json::to_vec(&persisted)?,
                )?;
                let d = inner.devices.get_mut(&id).unwrap();
                if d.session != session {
                    d.data = json!({});
                    d.logs.clear();
                }
                d.session = session.clone();
                d.info = authorized;
                d.info.as_object_mut().unwrap().remove("credential");
                d.expires = d.info["remainingMs"].as_u64().map(|ms| {
                    std::time::Instant::now() + Duration::from_millis(ms.min(86_400_000))
                });
                d.info["started_at"] = json!(chrono::Utc::now());
                d.status = "connected".into();
                d.code = None;
                d.sender = Some(tx);
                d.ended = None;
                self.emit(
                    json!({"type":"SESSION_STARTED","payload":{"session":Self::view(&id,d)}}),
                );
            }
            let result = self
                .pump(&id, &session, connection.stream, rx, cancel.clone())
                .await;
            {
                let mut inner = self.inner.lock().unwrap();
                if let Some(d) = inner.devices.get_mut(&id) {
                    d.sender = None;
                    d.tools.clear();
                    d.status = "disconnected".into();
                    d.ended = Some(std::time::Instant::now());
                }
                inner.pending.retain(|(device, _), _| device != &id);
            }
            self.emit(json!({"type":"SESSION_ENDED","timestamp":chrono::Utc::now(),"payload":{"session_id":session}}));
            if cancel.is_cancelled() {
                return Ok(());
            }
            tracing::debug!(?result, "reconnecting device");
            retry = true;
            tokio::time::sleep(Duration::from_secs(2)).await;
        }
    }
    async fn pump(
        &self,
        id: &str,
        session: &str,
        stream: tokio_rustls::client::TlsStream<tokio::net::TcpStream>,
        mut outgoing: mpsc::Receiver<Value>,
        cancel: CancellationToken,
    ) -> Result<()> {
        let (mut reader, mut writer) = tokio::io::split(stream);
        let (tx, mut incoming) = mpsc::channel(32);
        let tracked = self.tasks.try_acquire("controller-reader")?;
        let read_task = AbortOnDropHandle::new(tokio::spawn(async move {
            let _tracked = tracked;
            while let Ok(frame) = lan::read_frame(&mut reader).await {
                if tx.send(frame).await.is_err() {
                    break;
                }
            }
        }));
        let result = async {
            let mut heartbeat = tokio::time::interval(Duration::from_secs(10));
            loop { tokio::select! {
                _ = cancel.cancelled() => break,
                _ = heartbeat.tick() => lan::write_frame(&mut writer, &json!({"type":"PING","session":session})).await?,
                message = outgoing.recv() => match message { Some(v) => lan::write_frame(&mut writer, &v).await?, None => break },
                message = incoming.recv() => match message { Some(v) => self.receive(id, session, v)?, None => break },
            } } Ok(())
        }.await;
        read_task.abort();
        let _ = read_task.await;
        result
    }
    fn receive(&self, id: &str, session: &str, message: Value) -> Result<()> {
        ensure!(message["session"] == session, "stale session frame");
        let mut inner = self.inner.lock().unwrap();
        if message["type"] == "RESULT" {
            if let Some(request) = message["id"].as_str() {
                if let Some(tx) = inner.pending.remove(&(id.into(), request.into())) {
                    let _ = tx.send(message["result"].clone());
                }
            }
            return Ok(());
        }
        let d = inner.devices.get_mut(id).context("missing device")?;
        match message["type"].as_str() {
            Some("PONG") => {
                d.expires = message["remainingMs"].as_u64().map(|ms| {
                    std::time::Instant::now() + Duration::from_millis(ms.min(86_400_000))
                });
            }
            Some("TOOLS") => {
                d.tools = message["tools"].as_array().cloned().unwrap_or_default();
            }
            Some("DATA") => {
                d.data = message["data"].clone();
                self.emit(json!({"type":"SESSION_DATA","timestamp":chrono::Utc::now(),"payload":{"session_id":session,"data":d.data}}));
            }
            Some("LOG") => {
                d.logs.push(message["log"].clone());
                if d.logs.len() > 1000 {
                    d.logs.remove(0);
                }
                self.emit(json!({"type":"SESSION_LOG","timestamp":chrono::Utc::now(),"payload":{"session_id":session,"log":message["log"]}}));
            }
            _ => {}
        }
        Ok(())
    }
    pub fn disconnect(&self, device: &str) {
        let mut inner = self.inner.lock().unwrap();
        if let Some(d) = inner.devices.get(device) {
            d.cancel.cancel();
        }
        if inner.credentials.get(device).is_some_and(|c| !c.remember) {
            inner.credentials.remove(device);
        }
    }
    pub fn forget(&self, device: &str) -> Result<()> {
        self.disconnect(device);
        let mut inner = self.inner.lock().unwrap();
        inner.credentials.remove(device);
        let persisted: HashMap<_, _> = inner
            .credentials
            .iter()
            .filter(|(_, c)| c.remember)
            .collect();
        private_write(
            directory().join("debug-peers.json"),
            &serde_json::to_vec(&persisted)?,
        )
    }
    pub fn cancel_call(&self, device: &str, request: &str) {
        let mut inner = self.inner.lock().unwrap();
        inner.pending.remove(&(device.into(), request.into()));
        if let Some(d) = inner.devices.get(device) {
            if let Some(tx) = &d.sender {
                let _ = tx.try_send(json!({"type":"CANCEL","session":d.session,"id":request}));
            }
        }
    }
    pub async fn call(
        &self,
        device: &str,
        request: &str,
        name: &str,
        arguments: Value,
        legacy: bool,
    ) -> Result<Value> {
        ensure!(arguments.is_object(), "INVALID_ARGUMENTS: expected object");
        ensure!(
            !request.is_empty() && request.len() <= 128,
            "invalid request ID"
        );
        let (tx, receiver) = oneshot::channel();
        let (sender, session, duration) = {
            let mut inner = self.inner.lock().unwrap();
            ensure!(
                !inner.pending.contains_key(&(device.into(), request.into())),
                "duplicate request"
            );
            let d = inner.devices.get(device).context("device unavailable")?;
            let sender = d.sender.clone().context("SESSION_DISCONNECTED")?;
            let tool = d.tools.iter().find(|t| t["name"] == name);
            ensure!(legacy || tool.is_some(), "UNKNOWN_TOOL");
            let duration = tool
                .and_then(|t| t["timeoutMs"].as_u64())
                .unwrap_or(60_000)
                .min(86_400_000)
                + 2000;
            let session = d.session.clone();
            inner.pending.insert((device.into(), request.into()), tx);
            (sender, session, duration)
        };
        struct CancelOnDrop(Controller, String, String);
        impl Drop for CancelOnDrop {
            fn drop(&mut self) {
                self.0.cancel_call(&self.1, &self.2);
            }
        }
        let _guard = CancelOnDrop(self.clone(), device.into(), request.into());
        tokio::select! {
            biased;
            _ = self.shutdown.requested() => anyhow::bail!("server is shutting down"),
            result = async {
                sender.send(json!({"type":if legacy {"ACTION"} else {"CALL"},"session":session,"id":request,"name":name,"arguments":arguments})).await?;
                Ok(timeout(Duration::from_millis(duration), receiver).await??)
            } => result,
        }
    }
}

async fn auth(
    State(controller): State<Controller>,
    request: axum::extract::Request,
    next: Next,
) -> Response {
    let headers = request.headers();
    let host = headers
        .get(header::HOST)
        .and_then(|v| v.to_str().ok())
        .unwrap_or("");
    let local = host == "localhost"
        || host.starts_with("localhost:")
        || host == "127.0.0.1"
        || host.starts_with("127.0.0.1:");
    let origin_ok = headers
        .get(header::ORIGIN)
        .map(|o| o.to_str().ok() == Some(format!("http://{host}").as_str()))
        .unwrap_or(true);
    let bearer = headers
        .get(header::AUTHORIZATION)
        .and_then(|v| v.to_str().ok())
        .map(|v| v == format!("Bearer {}", controller.token))
        .unwrap_or(false);
    let cookie = headers
        .get(header::COOKIE)
        .and_then(|v| v.to_str().ok())
        .map(|v| {
            v.split(';')
                .any(|s| s.trim() == format!("androidoscopy={}", controller.token))
        })
        .unwrap_or(false);
    if !local || !origin_ok || !(bearer || cookie) {
        return StatusCode::UNAUTHORIZED.into_response();
    }
    next.run(request).await
}
async fn login(State(c): State<Controller>) -> impl IntoResponse {
    (
        [(
            header::SET_COOKIE,
            format!(
                "androidoscopy={}; HttpOnly; SameSite=Strict; Path=/",
                c.token
            ),
        )],
        Json(json!({"ok":true})),
    )
}
async fn devices(State(c): State<Controller>) -> Json<Value> {
    Json(c.devices())
}
async fn session(State(c): State<Controller>, Path(id): Path<String>) -> Json<Value> {
    Json(c.session(&id))
}
async fn connect(State(c): State<Controller>, Json(v): Json<Value>) -> Response {
    match c.connect(v["address"].as_str().unwrap_or("").into()) {
        Ok(()) => Json(json!({"ok":true})).into_response(),
        Err(e) => (StatusCode::BAD_REQUEST, e.to_string()).into_response(),
    }
}
async fn disconnect(State(c): State<Controller>, Json(v): Json<Value>) -> Json<Value> {
    c.disconnect(v["device"].as_str().unwrap_or(""));
    Json(json!({"ok":true}))
}
async fn forget(State(c): State<Controller>, Json(v): Json<Value>) -> Response {
    match c.forget(v["device"].as_str().unwrap_or("")) {
        Ok(()) => Json(json!({"ok":true})).into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}
async fn cancel(State(c): State<Controller>, Json(v): Json<Value>) -> Json<Value> {
    c.cancel_call(
        v["device"].as_str().unwrap_or(""),
        v["id"].as_str().unwrap_or(""),
    );
    Json(json!({"ok":true}))
}
async fn call(State(c): State<Controller>, Json(v): Json<Value>) -> Response {
    let id = v["id"]
        .as_str()
        .map(String::from)
        .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
    match c
        .call(
            v["device"].as_str().unwrap_or(""),
            &id,
            v["name"].as_str().unwrap_or(""),
            v["arguments"].clone(),
            false,
        )
        .await
    {
        Ok(value) => Json(value).into_response(),
        Err(e) => (StatusCode::BAD_REQUEST, e.to_string()).into_response(),
    }
}
async fn events(State(c): State<Controller>, ws: WebSocketUpgrade) -> impl IntoResponse {
    let Ok(tracked) = c.tasks.try_acquire("controller-websocket") else {
        return StatusCode::SERVICE_UNAVAILABLE.into_response();
    };
    ws.on_upgrade(move |socket| async move {
        let _tracked = tracked;
        let (mut sink, mut source) = socket.split(); let mut events = c.events.subscribe();
        if sink.send(Message::Text(c.sync().to_string().into())).await.is_err() { return }
        loop { tokio::select! {
            _ = c.shutdown.requested() => break,
            event = events.recv() => match event {
                Ok(event) => if sink.send(Message::Text(event.to_string().into())).await.is_err() { break },
                Err(broadcast::error::RecvError::Lagged(_)) => { if sink.send(Message::Text(c.sync().to_string().into())).await.is_err() { break } },
                Err(_) => break,
            },
            incoming = source.next() => match incoming {
                Some(Ok(Message::Text(text))) => {
                    let Ok(v) = serde_json::from_str::<Value>(&text) else { continue };
                    if v["type"] != "ACTION" { continue }
                    let p = &v["payload"];
                    let device = c.inner.lock().unwrap().devices.iter().find(|(_,d)| d.session == p["session_id"]).map(|(id,_)| id.clone());
                    if let Some(device) = device {
                        let Ok(tracked) = c.tasks.try_acquire("controller-action") else { break };
                        let ctl = c.clone(); let p = p.clone(); tokio::spawn(async move {
                            let _tracked = tracked;
                            let result = tokio::select! {
                                _ = ctl.shutdown.requested() => return,
                                result = ctl.call(&device,p["action_id"].as_str().unwrap_or(""),p["action"].as_str().unwrap_or(""),p.get("args").cloned().unwrap_or(json!({})),true) => result,
                            };
                            let (success,message,data) = match result {
                                Ok(v) => (v["isError"] != true, v["structuredContent"]["message"].clone(),v["structuredContent"]["data"].clone()),
                                Err(e) => (false,json!(e.to_string()),Value::Null),
                            };
                            ctl.emit(json!({"type":"ACTION_RESULT","timestamp":chrono::Utc::now(),"payload":{"session_id":p["session_id"],"action_id":p["action_id"],"success":success,"message":message,"data":data}}));
                        });
                    }
                },
                Some(Ok(_)) => {}, _ => break,
            }
        } }
    })
}

pub fn router(controller: Controller) -> Router {
    Router::new()
        .route("/api/v2/auth", post(login))
        .route("/api/v2/devices", get(devices))
        .route("/api/v2/session/{id}", get(session))
        .route("/api/v2/connect", post(connect))
        .route("/api/v2/disconnect", post(disconnect))
        .route("/api/v2/call", post(call))
        .route("/api/v2/forget", post(forget))
        .route("/api/v2/cancel", post(cancel))
        .route("/api/v2/events", get(events))
        .layer(middleware::from_fn_with_state(controller.clone(), auth))
        .with_state(controller)
}
pub async fn run(config: Config) -> Result<()> {
    let controller = Controller::new()?;
    let listener =
        tokio::net::TcpListener::bind((std::net::Ipv4Addr::LOCALHOST, config.server.http_port))
            .await?;
    private_write(
        directory().join("control-token"),
        controller.token.as_bytes(),
    )?;
    let signals = Signals::install()?;
    let mut lifecycle = Lifecycle::new(ShutdownOptions {
        grace_period: Duration::from_secs(30),
    });
    let shutdown = lifecycle.shutdown();
    // Keep the same sticky notification in routes and upgraded sessions.
    let controller = Controller {
        shutdown: shutdown.clone(),
        ..controller
    };
    let discovery = controller.clone();
    lifecycle.service("mdns-discovery", async move { discovery.discover().await })?;
    let stopping = controller.clone();
    lifecycle.service("controller-shutdown", async move {
        stopping.shutdown.requested().await;
        let mut inner = stopping.inner.lock().unwrap();
        for device in inner.devices.values() {
            device.cancel.cancel();
        }
        // Release pending HTTP calls before waiting for HTTP draining.
        inner.pending.clear();
        Ok::<_, anyhow::Error>(())
    })?;
    let cleanup = controller.clone();
    let ttl = config.session.ended_session_ttl_seconds;
    lifecycle.service("session-expiry", async move {
        loop {
            tokio::select! {
                _ = cleanup.shutdown.requested() => break,
                _ = tokio::time::sleep(Duration::from_secs(30)) => {},
            }
            let mut inner = cleanup.inner.lock().unwrap();
            let expired: Vec<String> = inner
                .devices
                .iter()
                .filter(|(_, d)| d.ended.is_some_and(|t| t.elapsed().as_secs() >= ttl))
                .map(|(id, _)| id.clone())
                .collect();
            for id in expired {
                if let Some(d) = inner.devices.remove(&id) {
                    d.cancel.cancel();
                }
                if inner.credentials.get(&id).is_some_and(|c| !c.remember) {
                    inner.credentials.remove(&id);
                }
            }
        }
        Ok::<_, anyhow::Error>(())
    })?;
    eprintln!(
        "Androidoscopy: http://127.0.0.1:{}/#token={}",
        config.server.http_port, controller.token
    );
    let app = router(controller.clone()).fallback(crate::dashboard::serve_embedded);
    lifecycle.service("http", simple_server::http::serve(listener, app, shutdown))?;
    let report = lifecycle
        .run(signals.wait(), async {
            // HTTP is drained, so no new route can add a task after closing the tracker.
            controller.tasks.close();
            controller.tasks.wait().await;
            Ok::<_, anyhow::Error>(())
        })
        .await?;
    eprintln!("Graceful shutdown complete: {:?}", report.reason);
    Ok(())
}
pub async fn request(method: reqwest::Method, path: &str, body: Option<Value>) -> Result<Value> {
    let port = Config::load().unwrap_or_default().server.http_port;
    let mut request = reqwest::Client::new()
        .request(method, format!("http://127.0.0.1:{port}/api/v2/{path}"))
        .bearer_auth(load_token()?);
    if let Some(body) = body {
        request = request.json(&body);
    }
    let response = request.send().await?;
    ensure!(response.status().is_success(), "{}", response.text().await?);
    Ok(response.json().await?)
}

#[cfg(test)]
mod tests {
    use super::*;
    fn controller() -> Controller {
        Controller {
            inner: Arc::new(Mutex::new(Inner {
                devices: HashMap::new(),
                credentials: HashMap::new(),
                pending: HashMap::new(),
            })),
            events: broadcast::channel(16).0,
            token: "test-token".into(),
            peer: "test-peer".into(),
            shutdown: Shutdown::new(),
            tasks: WorkTracker::new(),
        }
    }
    #[tokio::test]
    async fn closed_controller_rejects_connections_without_mutating_devices() {
        let c = controller();
        let reservation = c.tasks.try_acquire("pending-upgrade").unwrap();
        c.tasks.close();
        assert!(c.clone().connect("127.0.0.1:9".into()).is_err());
        assert!(c.inner.lock().unwrap().devices.is_empty());
        assert!(timeout(Duration::from_millis(20), c.tasks.wait())
            .await
            .is_err());
        drop(reservation);
        timeout(Duration::from_secs(1), c.tasks.wait())
            .await
            .unwrap();
    }

    #[tokio::test]
    async fn shutdown_cancels_pending_calls_and_drains_their_guards() {
        let c = controller();
        let (sender, mut receiver) = mpsc::channel(8);
        c.inner.lock().unwrap().devices.insert(
            "device".into(),
            Device {
                session: "session".into(),
                sender: Some(sender),
                tools: vec![json!({"name":"tool"})],
                ..Default::default()
            },
        );
        let ctl = c.clone();
        let call = tokio::spawn(async move {
            ctl.call("device", "request", "tool", json!({}), false)
                .await
        });
        receiver.recv().await.unwrap();
        c.shutdown.request();
        assert!(timeout(Duration::from_secs(1), call)
            .await
            .unwrap()
            .unwrap()
            .is_err());
        assert!(c.inner.lock().unwrap().pending.is_empty());
        assert_eq!(receiver.recv().await.unwrap()["type"], "CANCEL");
    }

    #[tokio::test]
    async fn api_rejects_missing_credentials_foreign_origins_and_rebound_hosts() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        let task =
            tokio::spawn(async move { axum::serve(listener, router(controller())).await.unwrap() });
        let url = format!("http://{address}/api/v2/devices");
        let client = reqwest::Client::new();
        assert_eq!(client.get(&url).send().await.unwrap().status(), 401);
        assert_eq!(
            client
                .get(&url)
                .bearer_auth("test-token")
                .send()
                .await
                .unwrap()
                .status(),
            200
        );
        assert_eq!(
            client
                .get(&url)
                .bearer_auth("test-token")
                .header("Origin", "https://evil.example")
                .send()
                .await
                .unwrap()
                .status(),
            401
        );
        assert_eq!(
            client
                .get(&url)
                .bearer_auth("test-token")
                .header("Host", "evil.example")
                .send()
                .await
                .unwrap()
                .status(),
            401
        );
        assert_eq!(
            client
                .get(format!("http://{address}/ws/app"))
                .bearer_auth("test-token")
                .send()
                .await
                .unwrap()
                .status(),
            404
        );
        task.abort();
    }
    #[tokio::test]
    async fn calls_preserve_types_and_cancellation_cleans_pending_requests() {
        let c = controller();
        let (sender, mut receiver) = mpsc::channel(8);
        c.inner.lock().unwrap().devices.insert(
            "device".into(),
            Device {
                session: "session".into(),
                sender: Some(sender),
                tools: vec![json!({"name":"typed"})],
                ..Default::default()
            },
        );
        let args = json!({"nested":[42,true,null,{"n":1.25}]});
        let ctl = c.clone();
        let input = args.clone();
        let task =
            tokio::spawn(async move { ctl.call("device", "request", "typed", input, false).await });
        let frame = receiver.recv().await.unwrap();
        assert_eq!(frame["arguments"], args);
        assert!(c
            .receive(
                "device",
                "session",
                json!({"type":"RESULT","session":"stale"})
            )
            .is_err());
        task.abort();
        let _ = task.await;
        assert_eq!(receiver.recv().await.unwrap()["type"], "CANCEL");
        assert!(c.inner.lock().unwrap().pending.is_empty());
    }
    #[tokio::test]
    async fn retained_tools_cannot_execute_when_disconnected() {
        let c = controller();
        c.inner.lock().unwrap().devices.insert(
            "device".into(),
            Device {
                tools: vec![json!({"name":"old"})],
                ..Default::default()
            },
        );
        assert_eq!(c.session("device")["tools"], json!([]));
        assert!(c
            .call("device", "request", "old", json!({}), false)
            .await
            .is_err());
    }
}
