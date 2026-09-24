# Step 10 rate limiting and admission assessment

Status: **Pending** for the Androidoscopy consumer. The production pairing
attempt limit runs in the Android SDK's Kotlin process and does not call the
Rust `simple_server::rate_limit` API. The Rust desktop controller and the
explicit legacy server have no request quota to migrate, so that Rust-only
scope is N/A. This does not make the consumer's Step 10 capability N/A.

The Android SDK's `SessionRuntime.serve` handles TLS socket frames. After it
sends `HELLO` and recognizes the peer's `PAIR` frame (as opposed to `RESUME`),
it samples `SystemClock.elapsedRealtime()`. It requires at least 5,000 ms since the last
admitted pairing attempt, then stores the sample before validating the
commitment or sending `CHALLENGE`. Rejected attempts leave the stored timestamp
unchanged. The timestamp is a field on `SessionRuntime`, initialized to a low
sentinel and not reset by `start` or `stop`, so the gate is global to that
runtime across sessions, not keyed by peer or IP. A denial raises
`PAIRING_RATE_LIMITED`, closes the socket, and maps to the existing “Too many
pairing attempts” user-facing reason. The controller's `lan::Connection`
initiates `PAIR`, but the phone is the enforcement authority; resumed sessions
take the separate credential path. `docs/SESSIONS_V2.md` also promises a
device-side attempt limit.

The shared Step 10 primitives are Rust APIs. Android's `SessionRuntime` is
Kotlin and has no Rust binding or call path. Adding a limiter to the desktop
would leave other LAN clients able to bypass the device's gate. Replacing the
device check or changing its timestamp/reset/rejection behavior would be a
policy change, not a behavior-preserving migration. Shared adoption therefore
requires an agreed cross-language integration route or a change in Step 10
scope; this assessment makes no runtime change.

Other inspected controls are separate: the Android SDK admits only one pending
or active TLS socket and at most 16 concurrent tool calls; the controller's
`WorkTracker` reservations close admission during shutdown; connection retry
delays, heartbeat timers, collection intervals, frame-size bounds, and timeouts
govern protocol pacing or resource capacity. `server::config::max_connections`
is parsed but is not read by either server listener or handler. The v2 loopback
router, legacy WebSocket routes, and MCP stdio bridge have no rate-budget,
cooldown, or request-quota policy. None of these controls justifies enabling the
shared Rust `rate-limit` feature for a production entry point today.

Starting development branch: clean `master` at
`50b1cda3cb45140d53f9c5db9d3b3a4f0883edff`. Reviewed shared library
revision: `b8a53f877f37eb762950aa4f85e0b9a914ea891d`; it is contract context,
not an adopted build dependency for this step. No dependency, active checkout
pin, or build instruction changed. Baseline source/config searches and
`git diff --check` passed before this documentation edit. No runtime build or
device suite was run for a documentation-only assessment.
