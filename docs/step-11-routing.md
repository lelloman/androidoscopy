# Step 11: shared HTTP routing

Reviewed shared revision: `cdb9e6304811d7cb0ae8de8333d4975a00597f42` (`simple-server.rev`).
Active development branch: `master`, baseline `ca813fe`.

## Adoption

- Controller API: shared Router, method routing, State/Path/Json, response tuples
  and auth middleware. Bearer/cookie fallback, loopback Host/Origin checks and
  login-cookie attributes retain their existing contracts.
- Legacy app/dashboard WebSockets: shared route registration and upgrade
  extraction. Protocol messages/sockets remain under explicit compatibility.
- Embedded dashboard: shared raw Request/Body and responses; asset MIME types,
  SPA fallback and HEAD behavior preserved.
- Public `create_router`, HTTP startup and test helpers return/use shared Router.
  TLS consumes `Router::into_make_service()` directly. No backend-router conversion
  is used; certificates, TLS policy and shutdown handles remain application-owned.
- Remaining exposure: backend WebSocket Message/WebSocket types and the
  `axum-server` TLS adapter/configuration/handle, including its TLS test fixture.
  Step 11 is complete; complete Axum abstraction is still pending those protocols.

## Verification

Baseline server: **76 tests passed**. Two additional regression tests passed before
production changes (commit `1f19ad0`): controller cookie/JSON/method contracts and
embedded assets/SPA/HEAD/upgrade rejection. Final server: **78 passed**. Separate
full-stack crate: **12 passed before and after**, using mock Android and dashboard
clients. The existing real TLS test verifies a generated certificate, WebSocket
upgrade and app registration through the production adapter. Existing ownership
checks cover drain and late-upgrade rejection.

Shared library: **234 tests/doctests**, strict all-target/all-feature Clippy and
**3 minimal-web tests** pass. Added tests cover generic request bodies/service
factory cloning, header-array cookies/replacement/metadata and differential
invalid-header responses.

Normal all-target server Clippy passes with existing warnings. Strict Clippy
remains blocked by unchanged derivable Default, dead code, service PathBuf and
WebSocket test conversion warnings. Unrelated formatting in protocol.rs,
session.rs and tls.rs is retained; changed Rust files pass formatting checks.
Diff and dependency checks pass. Builds use offline resolution, isolated targets,
two jobs and disabled dev debug information. The full-stack crate has no tracked
lockfile; its lockfile was generated locally offline.

Android SDK/device and browser suites were not run; their sources are unchanged.
The committed dashboard assets were used. No push or deployment. Central trackers
record integration into master and cleanup of owned worktrees/branches/build files.
