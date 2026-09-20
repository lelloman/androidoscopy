# Step 03a: logging setup

The legacy server (`androidoscopy legacy`, `main.rs::run_server`) now calls
`logging::init`, which installs `simple_server::logging` with the application
filter and explicitly restores the `tracing-log` bridge. The reviewed source
revision is `71755b5e15ada9b22484559146ebaf4d82c91255` (`simple-server.rev`).
The default v2 controller and MCP/CLI commands did not initialize a subscriber
before this migration and remain unchanged. The configuration-file logging
fields were already unused; the legacy server still uses `RUST_LOG`, falling
back to `androidoscopy=debug,tower_http=debug` on missing/invalid values.

Output remains text on stdout with targets, historical `NO_COLOR` behavior,
active span fields, and log-facade forwarding. The application's original
EnvFilter parser is retained; an accepted empty directive set maps to `off`
for the shared API. No HTTP tracing or correlation behavior is introduced.

## Verification

Migration branch was created from master `bb4033d`. Untouched baseline library
checks passed all 32 tests (local-socket checks rerun outside the sandbox).
Migrated `cargo test --manifest-path server/Cargo.toml` passed 72 tests:
32 library, 26 binary, two logging tests, and 12 TLS/UDP/WebSocket tests.
The logging comparison invokes the production adapter in fresh processes and
checks 30 combinations of actual environment parsing and NO_COLOR policy,
including missing/empty/whitespace/invalid filters, levels, target and span
filters, structured values, stdout, stderr, colors, and legacy log records.
The existing lifecycle script passed all six controller/legacy WS/legacy TLS
SIGINT/SIGTERM startup, open-socket drain, and restart cases against the built
binary with isolated state. Strict Clippy is blocked by the unchanged
`derivable_impls` finding in `src/config.rs:117`; the full project also has
existing dead-code warnings. Only changed Rust files are rustfmt-checked;
unchanged source files have pre-existing formatting differences.
Browser, Android, release image, deployment, and push checks were not performed.
