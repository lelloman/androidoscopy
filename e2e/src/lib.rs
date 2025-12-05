//! E2E Test Harness for Androidoscopy.
//!
//! This library provides utilities for running end-to-end tests
//! of the Androidoscopy system, including:
//!
//! - Mock app client that simulates the Android SDK
//! - Mock dashboard client that simulates the web dashboard
//! - Test runner for orchestrating tests
//! - Utilities for starting test servers

pub mod mock_app;
pub mod mock_dashboard;
pub mod test_runner;

pub use mock_app::MockAppClient;
pub use mock_dashboard::MockDashboardClient;
pub use test_runner::{TestConfig, TestRunner};
