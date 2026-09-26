# Step 01: Axum centralization

Current adoption continues in [Step 12 WebSockets](step-12-websockets.md).
The revisions and compatibility boundaries below describe the historical
Step 01 migration.

Androidoscopy uses the public `simple-server` Git dependency at revision
`46a36391ccca522f3ec9aa24206f2b231162dd96`, with the `ws` feature. It provides
Axum 0.8.9, replacing the directly managed Axum 0.7.9 dependency. Source and
integration tests use `simple_server::axum` during this transitional phase.

The existing `axum-server` 0.7 TLS adapter remains compatible. WebSocket text
conversions were already compatible with Axum 0.8, and routes have no path
parameters requiring syntax changes. Entry points and application behavior
remain owned by Androidoscopy. Building requires a Rust toolchain supporting
edition 2024 for `simple-server` (the project CI uses stable).

Test imports referenced the former `androidoscopy_server` crate name. Server
integration tests now use `androidoscopy`; the E2E dependency explicitly aliases
package `androidoscopy` to its existing `androidoscopy-server` dependency name.

## Verification (2026-09-18)

- `cd server && cargo test --locked`: 62 tests passed (50 unit tests across
  library/binary, one UDP discovery test, ten WebSocket tests, one TLS test).
- `cd e2e && cargo test`: five unit tests and seven full-stack scenarios passed.
- `cargo tree --locked -i axum`: exactly Axum 0.8.9, through `simple-server`.
- The new TLS integration test trusts a generated certificate and exercises
  the production TLS adapter, WebSocket upgrade, and app registration.
- `cargo fmt --check` still reports pre-existing formatting in untouched
  `protocol.rs`, `session.rs`, and `tls.rs`. Changed Rust files are formatted.
- Strict `cargo clippy --locked --all-targets -- -D warnings` stops on the
  existing derivable `Default` implementation in `config.rs`. The same issue
  reproduces on unchanged baseline `cc54c3e`, which also reports four now-obsolete
  WebSocket conversion warnings under Axum 0.7.

The existing embedded dashboard assets were used for the server build. Android
SDK and dashboard sources were unchanged; their separate suites were not run.
