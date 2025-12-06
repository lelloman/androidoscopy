//! Session management for connected apps and dashboards.
//!
//! This module handles:
//! - Creating and tracking app sessions
//! - Buffering recent data and logs for late-joining dashboards
//! - Managing dashboard connections
//! - Routing messages between apps and dashboards

use chrono::{DateTime, Utc};
use serde_json::Value;
use std::collections::{HashMap, VecDeque};
use tokio::sync::mpsc;
use uuid::Uuid;

use crate::protocol::{
    DeviceInfo, LogEntry, LogPayload, RegisterPayload, ServiceToAppMessage, ServiceToDashboardMessage,
    SessionInfo,
};

// === Ring Buffer ===

/// A fixed-capacity ring buffer that drops oldest items when full.
#[derive(Debug, Clone)]
pub struct RingBuffer<T> {
    items: VecDeque<T>,
    capacity: usize,
}

impl<T> RingBuffer<T> {
    pub fn new(capacity: usize) -> Self {
        Self {
            items: VecDeque::with_capacity(capacity),
            capacity,
        }
    }

    pub fn push(&mut self, item: T) {
        if self.items.len() >= self.capacity {
            self.items.pop_front();
        }
        self.items.push_back(item);
    }

    pub fn iter(&self) -> impl Iterator<Item = &T> {
        self.items.iter()
    }

    pub fn len(&self) -> usize {
        self.items.len()
    }

    pub fn is_empty(&self) -> bool {
        self.items.is_empty()
    }

    pub fn clear(&mut self) {
        self.items.clear();
    }
}

// === Data Message ===

#[derive(Debug, Clone)]
pub struct DataMessage {
    pub timestamp: DateTime<Utc>,
    pub payload: Value,
}

// === Log Message ===

#[derive(Debug, Clone)]
pub struct LogMessage {
    pub timestamp: DateTime<Utc>,
    pub payload: LogPayload,
}

impl From<&LogMessage> for LogEntry {
    fn from(msg: &LogMessage) -> Self {
        LogEntry {
            timestamp: msg.timestamp,
            level: msg.payload.level,
            tag: msg.payload.tag.clone(),
            message: msg.payload.message.clone(),
            throwable: msg.payload.throwable.clone(),
        }
    }
}

// === Session ===

#[derive(Debug)]
pub struct Session {
    pub id: String,
    pub app_name: String,
    pub package_name: String,
    pub version_name: String,
    pub device: DeviceInfo,
    pub dashboard_schema: Value,
    pub started_at: DateTime<Utc>,
    pub ended_at: Option<DateTime<Utc>>,
    data_buffer: RingBuffer<DataMessage>,
    log_buffer: RingBuffer<LogMessage>,
    network_requests: RingBuffer<Value>,
    pub app_sender: Option<mpsc::Sender<ServiceToAppMessage>>,
}

impl Session {
    pub fn new(
        register: RegisterPayload,
        data_buffer_size: usize,
        log_buffer_size: usize,
        app_sender: mpsc::Sender<ServiceToAppMessage>,
    ) -> Self {
        Self {
            id: Uuid::new_v4().to_string(),
            app_name: register.app_name,
            package_name: register.package_name,
            version_name: register.version_name,
            device: register.device,
            dashboard_schema: register.dashboard,
            started_at: Utc::now(),
            ended_at: None,
            data_buffer: RingBuffer::new(data_buffer_size),
            log_buffer: RingBuffer::new(log_buffer_size),
            network_requests: RingBuffer::new(500), // Store up to 500 network requests
            app_sender: Some(app_sender),
        }
    }

    pub fn add_data(&mut self, timestamp: DateTime<Utc>, payload: Value) {
        self.data_buffer.push(DataMessage { timestamp, payload });
    }

    pub fn clear_network_requests(&mut self) {
        self.network_requests.clear();
    }

    pub fn add_log(&mut self, timestamp: DateTime<Utc>, payload: LogPayload) {
        self.log_buffer.push(LogMessage { timestamp, payload });
    }

    pub fn get_latest_data(&self) -> Option<Value> {
        self.data_buffer.iter().last().map(|d| d.payload.clone())
    }

    pub fn get_recent_logs(&self) -> Vec<LogEntry> {
        self.log_buffer.iter().map(|l| l.into()).collect()
    }

    pub fn is_active(&self) -> bool {
        self.ended_at.is_none()
    }

    pub fn end(&mut self) {
        self.ended_at = Some(Utc::now());
        self.app_sender = None;
    }

    pub fn resume(&mut self, app_sender: mpsc::Sender<ServiceToAppMessage>) {
        self.ended_at = None;
        self.app_sender = Some(app_sender);
    }

    pub fn to_session_info(&self) -> SessionInfo {
        SessionInfo {
            session_id: self.id.clone(),
            app_name: self.app_name.clone(),
            package_name: self.package_name.clone(),
            version_name: self.version_name.clone(),
            device: self.device.clone(),
            dashboard: self.dashboard_schema.clone(),
            started_at: self.started_at,
            latest_data: self.get_latest_data(),
            recent_logs: self.get_recent_logs(),
        }
    }
}

// === Session Manager ===

pub struct SessionManager {
    sessions: HashMap<String, Session>,
    dashboard_senders: Vec<mpsc::Sender<ServiceToDashboardMessage>>,
    data_buffer_size: usize,
    log_buffer_size: usize,
    ended_session_ttl_seconds: u64,
}

impl SessionManager {
    pub fn new(data_buffer_size: usize, log_buffer_size: usize, ended_session_ttl_seconds: u64) -> Self {
        Self {
            sessions: HashMap::new(),
            dashboard_senders: Vec::new(),
            data_buffer_size,
            log_buffer_size,
            ended_session_ttl_seconds,
        }
    }

    pub fn create_session(
        &mut self,
        register: RegisterPayload,
        app_sender: mpsc::Sender<ServiceToAppMessage>,
    ) -> (String, bool) {
        // Check for existing ended session from same device + package
        let existing_session_id = self.sessions.iter()
            .find(|(_, s)| {
                s.ended_at.is_some()
                    && s.device.device_id == register.device.device_id
                    && s.package_name == register.package_name
            })
            .map(|(id, _)| id.clone());

        if let Some(session_id) = existing_session_id {
            // Resume existing session
            if let Some(session) = self.sessions.get_mut(&session_id) {
                session.resume(app_sender);
            }
            (session_id, true) // true = resumed
        } else {
            // Create new session
            let session = Session::new(
                register,
                self.data_buffer_size,
                self.log_buffer_size,
                app_sender,
            );
            let session_id = session.id.clone();
            self.sessions.insert(session_id.clone(), session);
            (session_id, false) // false = new session
        }
    }

    pub fn end_session(&mut self, session_id: &str) {
        if let Some(session) = self.sessions.get_mut(session_id) {
            session.end();
        }
    }

    pub fn remove_session(&mut self, session_id: &str) -> Option<Session> {
        self.sessions.remove(session_id)
    }

    pub fn get_session(&self, session_id: &str) -> Option<&Session> {
        self.sessions.get(session_id)
    }

    pub fn get_session_mut(&mut self, session_id: &str) -> Option<&mut Session> {
        self.sessions.get_mut(session_id)
    }

    pub fn get_active_sessions(&self) -> Vec<&Session> {
        self.sessions.values().filter(|s| s.is_active()).collect()
    }

    pub fn get_all_sessions(&self) -> Vec<&Session> {
        self.sessions.values().collect()
    }

    pub fn add_data(&mut self, session_id: &str, timestamp: DateTime<Utc>, data: Value) -> bool {
        if let Some(session) = self.sessions.get_mut(session_id) {
            session.add_data(timestamp, data);
            true
        } else {
            false
        }
    }

    pub fn add_log(&mut self, session_id: &str, timestamp: DateTime<Utc>, log: LogPayload) -> bool {
        if let Some(session) = self.sessions.get_mut(session_id) {
            session.add_log(timestamp, log);
            true
        } else {
            false
        }
    }

    pub fn add_dashboard_sender(&mut self, sender: mpsc::Sender<ServiceToDashboardMessage>) {
        self.dashboard_senders.push(sender);
    }

    pub fn remove_dashboard_sender(&mut self, sender: &mpsc::Sender<ServiceToDashboardMessage>) {
        self.dashboard_senders.retain(|s| !s.same_channel(sender));
    }

    pub fn get_dashboard_senders(&self) -> &[mpsc::Sender<ServiceToDashboardMessage>] {
        &self.dashboard_senders
    }

    pub fn cleanup_ended_sessions(&mut self) {
        let now = Utc::now();
        let ttl_seconds = self.ended_session_ttl_seconds as i64;

        self.sessions.retain(|_, session| {
            if let Some(ended_at) = session.ended_at {
                let elapsed = now.signed_duration_since(ended_at).num_seconds();
                elapsed < ttl_seconds
            } else {
                true
            }
        });
    }

    pub fn session_count(&self) -> usize {
        self.sessions.len()
    }

    pub fn active_session_count(&self) -> usize {
        self.sessions.values().filter(|s| s.is_active()).count()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::protocol::LogLevel;
    use serde_json::json;

    fn create_test_register_payload() -> RegisterPayload {
        RegisterPayload {
            protocol_version: "1.0".to_string(),
            app_name: "TestApp".to_string(),
            package_name: "com.test.app".to_string(),
            version_name: "1.0.0".to_string(),
            device: DeviceInfo {
                device_id: "abc123".to_string(),
                manufacturer: "Google".to_string(),
                model: "Pixel 5".to_string(),
                android_version: "13".to_string(),
                api_level: 33,
                is_emulator: false,
            },
            dashboard: json!({ "sections": [] }),
        }
    }

    #[test]
    fn test_ring_buffer_push_within_capacity() {
        let mut buffer: RingBuffer<i32> = RingBuffer::new(5);
        buffer.push(1);
        buffer.push(2);
        buffer.push(3);

        assert_eq!(buffer.len(), 3);
        let items: Vec<_> = buffer.iter().copied().collect();
        assert_eq!(items, vec![1, 2, 3]);
    }

    #[test]
    fn test_ring_buffer_overflow() {
        let mut buffer: RingBuffer<i32> = RingBuffer::new(3);
        buffer.push(1);
        buffer.push(2);
        buffer.push(3);
        buffer.push(4);
        buffer.push(5);

        assert_eq!(buffer.len(), 3);
        let items: Vec<_> = buffer.iter().copied().collect();
        assert_eq!(items, vec![3, 4, 5]);
    }

    #[test]
    fn test_session_creation() {
        let (tx, _rx) = mpsc::channel(10);
        let register = create_test_register_payload();
        let session = Session::new(register, 100, 500, tx);

        assert_eq!(session.app_name, "TestApp");
        assert_eq!(session.package_name, "com.test.app");
        assert!(session.is_active());
        assert!(session.ended_at.is_none());
    }

    #[test]
    fn test_session_add_data() {
        let (tx, _rx) = mpsc::channel(10);
        let register = create_test_register_payload();
        let mut session = Session::new(register, 100, 500, tx);

        session.add_data(Utc::now(), json!({ "memory": 1000 }));
        session.add_data(Utc::now(), json!({ "memory": 2000 }));

        let latest = session.get_latest_data().unwrap();
        assert_eq!(latest["memory"], 2000);
    }

    #[test]
    fn test_session_add_log() {
        let (tx, _rx) = mpsc::channel(10);
        let register = create_test_register_payload();
        let mut session = Session::new(register, 100, 500, tx);

        session.add_log(
            Utc::now(),
            LogPayload {
                level: LogLevel::Error,
                tag: Some("Test".to_string()),
                message: "Test error".to_string(),
                throwable: None,
            },
        );

        let logs = session.get_recent_logs();
        assert_eq!(logs.len(), 1);
        assert_eq!(logs[0].message, "Test error");
    }

    #[test]
    fn test_session_end() {
        let (tx, _rx) = mpsc::channel(10);
        let register = create_test_register_payload();
        let mut session = Session::new(register, 100, 500, tx);

        assert!(session.is_active());
        session.end();
        assert!(!session.is_active());
        assert!(session.ended_at.is_some());
        assert!(session.app_sender.is_none());
    }

    #[test]
    fn test_session_manager_create_and_get() {
        let mut manager = SessionManager::new(100, 500, 3600);
        let (tx, _rx) = mpsc::channel(10);
        let register = create_test_register_payload();

        let (session_id, _resumed) = manager.create_session(register, tx);

        assert!(manager.get_session(&session_id).is_some());
        assert_eq!(manager.session_count(), 1);
        assert_eq!(manager.active_session_count(), 1);
    }

    #[test]
    fn test_session_manager_end_session() {
        let mut manager = SessionManager::new(100, 500, 3600);
        let (tx, _rx) = mpsc::channel(10);
        let register = create_test_register_payload();

        let (session_id, _resumed) = manager.create_session(register, tx);
        manager.end_session(&session_id);

        assert_eq!(manager.session_count(), 1);
        assert_eq!(manager.active_session_count(), 0);
        assert!(!manager.get_session(&session_id).unwrap().is_active());
    }

    #[test]
    fn test_session_manager_add_data() {
        let mut manager = SessionManager::new(100, 500, 3600);
        let (tx, _rx) = mpsc::channel(10);
        let register = create_test_register_payload();

        let (session_id, _resumed) = manager.create_session(register, tx);
        let result = manager.add_data(&session_id, Utc::now(), json!({ "test": 123 }));

        assert!(result);

        let session = manager.get_session(&session_id).unwrap();
        let latest = session.get_latest_data().unwrap();
        assert_eq!(latest["test"], 123);
    }

    #[test]
    fn test_session_manager_add_data_nonexistent() {
        let mut manager = SessionManager::new(100, 500, 3600);
        let result = manager.add_data("nonexistent", Utc::now(), json!({}));
        assert!(!result);
    }

    #[test]
    fn test_session_manager_get_active_sessions() {
        let mut manager = SessionManager::new(100, 500, 3600);
        let (tx1, _rx1) = mpsc::channel(10);
        let (tx2, _rx2) = mpsc::channel(10);

        let (session1_id, _) = manager.create_session(create_test_register_payload(), tx1);
        let (_session2_id, _) = manager.create_session(create_test_register_payload(), tx2);

        manager.end_session(&session1_id);

        let active = manager.get_active_sessions();
        assert_eq!(active.len(), 1);
    }

    #[test]
    fn test_session_manager_cleanup() {
        let mut manager = SessionManager::new(100, 500, 1); // 1 second TTL
        let (tx, _rx) = mpsc::channel(10);
        let register = create_test_register_payload();

        let (session_id, _resumed) = manager.create_session(register, tx);
        manager.end_session(&session_id);

        // Set ended_at to 2 seconds ago
        if let Some(session) = manager.get_session_mut(&session_id) {
            session.ended_at = Some(Utc::now() - chrono::Duration::seconds(2));
        }

        manager.cleanup_ended_sessions();
        assert_eq!(manager.session_count(), 0);
    }

    #[test]
    fn test_session_to_session_info() {
        let (tx, _rx) = mpsc::channel(10);
        let register = create_test_register_payload();
        let mut session = Session::new(register, 100, 500, tx);

        session.add_data(Utc::now(), json!({ "test": 456 }));

        let info = session.to_session_info();
        assert_eq!(info.app_name, "TestApp");
        assert_eq!(info.latest_data.unwrap()["test"], 456);
    }
}
