# Step 03b: request correlation assessment

Status: N/A. Reviewed simple-server correlation contract at
`52e1922bcfff44b4ab55a1b5374b35c6089b6e37` against active `master` at `1caa8d5`.

The legacy HTTP/dashboard and WebSocket listeners in `server/src/main.rs`,
the test router in `server/src/lib.rs`, and the v2 controller router in
`server/src/control.rs` do not select or propagate HTTP request IDs. The
controller's `call`/`cancel_call` request identifiers bind device protocol
operations and cancellation; they are not HTTP correlation headers. The v2
router installs authentication middleware, with no request-ID layer or scoped
HTTP ID consumed by errors or logs. MCP and device session IDs remain owned
by their protocols.

No correlation feature is enabled and no new response headers or ID generation
are introduced. The existing simple-server source pin remains unchanged.
This is a source-only applicability assessment: the router, middleware and ID
uses were inspected and a repository-wide Rust search found no HTTP correlation
header or layer. No application tests were rerun because executable code and
build inputs are unchanged. `git diff --check` validates this documentation.

The assessment was committed in a dedicated worktree branch based on `master`;
integration rebases `master` onto that branch, verifies identical trees, and
removes the temporary worktree and branch. No push or deployment is performed.
