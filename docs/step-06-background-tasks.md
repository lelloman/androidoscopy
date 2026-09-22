# Step 06 background work

Starting master `a6648cd`; shared source `06c7531dfcd4149a0947b4b74a79e96bf245639c`.

06a adopts named WorkTracker reservations in both legacy WebSocket handlers and
the v2 controller: connection attempts, TLS readers, event upgrades and action
execution. Guards are acquired before mutation/spawn or upgrade. Closed admission
returns 503 for upgrades and rejects connection creation without leaving phantom
device state. TLS reader AbortOnDropHandle behavior is retained. Existing shutdown
cancellation, HTTP-first drain order and lifecycle deadline remain unchanged.

06b is N/A: heartbeat/discovery/session cleanup and fixed reconnect waits are
protocol loops, already directly awaited or lifecycle-owned; no independent
cron/job trigger or resource-pool scheduler is configured. 06c is N/A for job
execution policies: pairing/call/transport timeouts and reconnect waits are
protocol behavior, not independently managed job retries/circuits/pause controls.

Baseline 72 Rust tests; final 74 pass. Added real WebSocket coverage verifies
both legacy routes drain accepted sockets and reject late upgrades with 503;
controller coverage verifies closed admission cannot mutate devices and pending
reservations survive interrupted waiting. Existing TLS/UDP/controller tests pass.
`ANDROIDOSCOPY_BINARY=... python3 scripts/test-lifecycle.py` passes all six real
process scenarios: v2, legacy WebSocket and legacy TLS, each with SIGINT/SIGTERM,
open sockets, graceful completion and listener reuse. All-target Clippy completes
with warnings. Changed-file formatting and diff checks pass. Android/device tests
and dashboard browser tests were not rerun. Temporary test data/loopback ports used.
