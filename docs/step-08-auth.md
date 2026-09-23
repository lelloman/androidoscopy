# Steps 08/09: shared authentication and authorization

Androidoscopy's v2 controller uses `simple_server::auth::Access` for its
loopback HTTP API and WebSocket upgrade gate. The application still accepts an
exact `Bearer` token or its `androidoscopy` cookie, then enforces its existing
Host and Origin rules. An invalid Authorization value still permits a valid
cookie, and repeated credential headers retain the first-value behavior. The
gate returns the same empty 401 response and remains mounted on every v2 API
route, including login and events. The static dashboard fallback is still
outside that gate.

The LAN client also evaluates the `AUTHORIZED` frame through shared `Access`:
its verifier requires the current session and a 32-byte credential; its check
compares the credential to the pinned secret on resumed connections. TLS
handshake signatures, pairing, exporter/HMAC proof, certificate pinning,
timeout, credential storage and errors remain application-owned. The old v1
server has no auth gate and remains a separate, explicitly selected mode.

Reviewed library revision: `0a629da7b5eb5aeeb0ed64aac2c5f96cd4d9717b`.
`simple-server.rev` selects the same revision for active CI checkout scripts.
The sibling path dependency uses the `auth` feature in production source.

## Verification

The starting development branch was clean `master` at
`49bfea41196b6c9946d7e2f62465f682b0c3ef14`. Its existing controller
HTTP test compiled but could not bind `127.0.0.1:0` in the restricted sandbox
(`Operation not permitted`) before any assertion ran. An unrestricted
pre-edit HTTP baseline was therefore not available. The prior implementation
and its expectations were inspected directly before migration.

The final server `cargo test --locked` run outside the sandbox passed all 76
tests: 35 library, 26 binary, two logging, one TLS WebSocket, one UDP discovery
and 11 WebSocket integration tests. The new controller matrix covers exact
Bearer syntax, first-header selection, malformed/incorrect bearer fallback to
cookie, and Host/Origin denial. The HTTP test covers both credential sources,
fallback, protected login and events routes, and unchanged 401/404 behavior.
The new LAN matrix covers a valid frame, stale session, missing/short
credential and wrong pinned secret; the existing TLS pairing test also passes.

`cargo test --locked --no-run` built all server test targets. Strict
all-target Clippy remains blocked by pre-existing `config.rs` derivable-impl
lint and other existing test/binary warnings. Library Clippy passes with that
one known lint allowed (`-D warnings -A clippy::derivable_impls`). Repository
`cargo fmt --check` still reports formatting in untouched `protocol.rs`,
`session.rs` and `tls.rs`; the changed `control.rs` and `lan.rs` files were
formatted individually. Android SDK, dashboard and E2E suites were not rerun;
they were not changed by this migration.
