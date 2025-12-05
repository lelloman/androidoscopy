//! E2E Test Harness for Androidoscopy.
//!
//! This library provides utilities for running end-to-end tests
//! of the Androidoscopy system, including:
//!
//! - Mock app client that simulates the Android SDK
//! - Test runner for orchestrating tests
//! - Utilities for starting test servers

pub mod mock_app;
pub mod test_runner;

pub use mock_app::MockAppClient;
pub use test_runner::{TestConfig, TestRunner};
