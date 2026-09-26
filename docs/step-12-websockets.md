# Step 12: owned WebSocket contracts

Reviewed shared revision: `46c724315a3ed35e35cb086b2328bb4040cd0531`
(`simple-server.rev`). Active development branch: `master`, baseline
`5e9a3969cdc5dbb0a8adf31c34839dca5279b22e`; the original worktree was clean.
Checks used an isolated service worktree alongside an archive of that exact
shared source, offline resolution, two build jobs and disabled dev debug info.

## Adoption

Production controller `/api/v2/events` and legacy `/ws/app` and `/ws/dashboard`
handlers use `simple_server::web::ws::{WebSocketUpgrade, WebSocket, Message}`.
The controller needs only the upgrade and message types; its socket is inferred.
There are no `simple_server::axum` or `web::compat` imports left, and the manifest
uses `web` plus `ws` instead of `web-compat`.

Existing bearer/cookie authentication and loopback Host/Origin policy stay on
the controller router. Legacy access policy stays unchanged. JSON registration,
SYNC, events, actions/results, session teardown and dashboard broadcasts retain
their protocol. Existing transport buffer/frame/message defaults and lack of
subprotocol selection remain unchanged. Split streams, forwarding tasks,
shutdown cancellation, task admission reservations and guard drain remain
application-owned and unchanged. No heartbeat policy or socket configuration
was added.

Only remaining Axum exposure: the `axum-server` TLS configuration, serving adapter
and shutdown handle in `server/src/main.rs`, plus their TLS integration fixture
in `server/tests/tls_websocket_integration.rs`. TLS abstraction is pending.

## Verification (2026-09-26)

- Unmodified baseline server all-target tests: **78 passed**; final: **79 passed**.
  This includes 11 legacy real WebSocket tests, real TLS app registration,
  upgrade rejection and shutdown/task admission checks.
- A new controller test passed before production edits and again after migration:
  real unauthenticated and foreign-origin handshake rejection, bearer and
  wrong-bearer/correct-cookie upgrades, SYNC and broadcast delivery, malformed
  input tolerance, transport Ping/Pong, shutdown guard drain and late-upgrade 503.
- Separate full-stack crate: **12 passed before and after**, covering app and
  dashboard registration, data/log delivery, actions/results and disconnects.
- Changed Rust files pass `rustfmt --check`. Whole-crate formatting still reports
  unchanged `protocol.rs`, `session.rs` and `tls.rs` issues reproduced before edits.
- Normal all-target Clippy and all-target build pass with existing warnings.
  Strict Clippy fails before and after on the unchanged derivable `Default` in
  `config.rs`; existing dead-code, PathBuf and client-test conversion warnings
  remain in normal Clippy. Checks use `--locked --offline` in the server crate.
- Dependency inspection retains exactly Axum 0.8.9 through the shared library;
  `web-compat` is no longer enabled. The active README and checkout/CI pin use the
  reviewed revision. Historical migration revision references are preserved.

The first default-debug native build hit an `aws-lc-sys` assembler error; disabling
dev debug information succeeded. Sandboxed socket tests initially failed to bind;
reported passing runs used approval for isolated loopback ports. Android SDK/device
and browser suites were not run; those sources and committed dashboard assets
are unchanged. No push or deployment.
