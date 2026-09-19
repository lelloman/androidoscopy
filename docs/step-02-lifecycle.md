# Step 02: shared process lifecycle

Both server modes use `simple-server` lifecycle revision
`c5359079ff4fad0b4b0359e8c88880dbbbc4eeb5`, through the sibling checkout.
`simple-server.rev` and `scripts/checkout-simple-server.sh` record and provision
that reviewed revision. The helper refuses to overwrite an existing checkout;
coordinated development can use the sibling with later documentation commits.
Fresh CI checkouts require the recorded revision to be available on the remote.
Server and E2E workflows provision the dependency before compiling.

`run` coordinates the authenticated loopback HTTP server, mDNS discovery and
session expiry. Shutdown stops discovery (including its daemon), cancels device
connections and pending tool calls, and drains tracked device readers, dashboard
upgrades and action tasks. Pending HTTP calls release their cancellation guards
before HTTP draining finishes. The main retains existing CLI and MCP behavior.

`legacy` coordinates dashboard HTTP, app WS or the existing axum-server Rustls
adapter, and optional UDP discovery. Both listeners bind before serving; bind
and service failures propagate. Upgraded app/dashboard connections stop on the
same notification and join their forwarding tasks. Existing TLS certificate
creation and fallback behavior are preserved.

SIGINT and SIGTERM share one 30-second budget for all services and tracked
cleanup. Unexpected service exits and timeouts fail the process with exit 1;
the binary exits explicitly so runtime teardown cannot wait past a failed drain.
The generated systemd service permits 35 seconds. CLI one-shot requests and MCP
stdio are outside these server lifecycles; the Android SDK and remote device
processes retain their own ownership.

`ANDROIDOSCOPY_CONFIG` optionally selects a configuration file, retaining the
existing home-directory default. This lets process tests isolate configuration,
credentials and certificates without changing the user's home directory.

## Validation (2026-09-19)

- Baseline server suite: 69 passed. Final server suite: 70 passed, including a
  regression proving shutdown cancels a pending tool call and clears its guard.
- Full-stack Rust E2E: five unit tests and seven scenarios passed. Run from
  `e2e` with `CARGO_TARGET_DIR=../server/target cargo test` to reuse artifacts.
- `python3 scripts/test-lifecycle.py`: six real-process cases pass (v2, legacy
  WS and legacy TLS, each with SIGINT/SIGTERM). Cases keep upgraded sockets open;
  v2 additionally stalls an outbound TLS handshake. Successful shutdown reports,
  zero exits and released listener ports are checked.
- Strict Clippy retains the existing derivable `Default` finding in `config.rs`.
  Workspace formatting retains existing findings in `protocol.rs`, `session.rs`
  and `tls.rs`; changed Rust files are formatted. Diff whitespace checks pass.
- Existing embedded dashboard assets are used. Android and dashboard sources
  are unchanged; their separate suites were not run. No deployment or push.
