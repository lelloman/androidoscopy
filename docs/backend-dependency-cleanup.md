# Backend dependency cleanup — 4 October 2026

The server's direct `tower-http` 0.5 dependency with `fs` was unused. Source review
found no Tower HTTP import or directory/file service. Dashboard assets come from
`rust_embed::Embed` in `server/src/dashboard.rs`, and the handler already uses
owned simple-server request/body/response types with MIME detection and SPA
fallback. Disk-backed `static-files` is not applicable; no feature is enabled
solely for adoption and no embedded assets are moved to runtime filesystem paths.

Removed the declaration and refreshed the tracked server lockfile. This removes
Tower HTTP 0.5.2 and its unused http-range-header package. Tower HTTP 0.6.11 remains
transitive through reqwest; outbound clients, rust-embed packaging, TLS clients,
logging filter strings and domain libraries are retained. The legacy
`tower_http=debug` filter text is not an API/dependency reference and remains for
configuration compatibility.

The existing public simple-server 0.1.0 pin and all its enabled features remain
unchanged; this cleanup requires no API upgrade. Shared disk serving was reviewed
at the public 0.1.3 implementation `dbc7f68`, but is not used by this embedded
frontend. The pairing JNI crate has no disk-serving scope and is unchanged.

Verification in an isolated master-based worktree starting at `fdb16f7`:

- Full server tests before and after: 79 passed each, including dashboard SPA,
  assets/HEAD, logging, UDP, WebSocket and TLS integration contracts.
- Locked all-target check and binary build pass.
- Six real-process shutdown cases pass: controller, legacy WS and legacy TLS,
  each under SIGINT and SIGTERM with live sockets.
- Standalone E2E tests: five unit and seven full-stack scenarios pass. Its
  intentionally ignored lockfile is not committed.
- Strict production Clippy fails on the same existing derivable Config Default
  implementation before/after. Formatting output exactly matches baseline
  failures in untouched Rust files. No fully green formatting/Clippy suite is
  claimed; these unrelated issues are not changed in this dependency cleanup.

Master is rebased onto the committed cleanup branch; ancestry/tree equality is
verified before removing the owned worktree/branch. No push or deployment is part
of this change. The shared HTML/Markdown trackers record no remaining direct
backend exposure for the Rust server scope.
