# Step 10 rate limiting and admission migration

Status: **Done** for the Androidoscopy consumer's device pairing attempt limit.
The production Android SDK now calls a JNI bridge backed by
`simple_server::rate_limit::Budget` with a strict, single-unit replenishing
quota of five seconds. The bridge uses `simple-server` with default features
disabled and only `rate-limit` enabled. The desktop controller and explicit
legacy server have no request quota to migrate; those Rust entry points remain
N/A for Step 10.

`SessionRuntime.serve` invokes its `PairingAttemptGate` only for `PAIR`, after
sending `HELLO` and recognizing the frame type, before parsing or validating
the commitment and before sending `CHALLENGE`. `RESUME` remains on its separate
credential path. The gate is a field on `SessionRuntime`; `start` and `stop` do
not replace it. It stores only the last admitted `SystemClock.elapsedRealtime()`
sample. The JNI call reconstructs a caller-owned shared `Budget` from that
sample, checks the current sample, and retains no native handle or global state.
A denied check does not advance the timestamp. Denial still raises
`PAIRING_RATE_LIMITED`, closes the socket, and uses the existing “Too many
pairing attempts” message. The limit stays global to the runtime, independent
of peer or IP. The controller is still a client of the phone's enforcement.

The Gradle SDK module builds the small Rust `cdylib` for `armeabi-v7a`,
`arm64-v8a`, `x86`, and `x86_64` with NDK 27.0.12077973 and packages them in
the AAR. The native code uses 16 KiB ELF page alignment; the demo APK's JNI
entries also have 16 KiB ZIP data offsets. Consumer shrinker rules retain the
JNI class and method name. SDK CI and JitPack install the Android Rust targets,
NDK, and the exact shared source revision before Gradle builds. Active README
instructions and `simple-server.rev` use the same pin. No network access is
needed when an installed SDK uses the pairing gate.

Starting development branch: clean `master` at
`2c6f9a8` (`Document Step 10 pairing rate limit migration gap`). Reviewed
shared source: `66b5259b22c6c48687f822beca497f03ad12c2f7`, frozen in an
isolated sibling source snapshot during implementation. The original assessment
reviewed `b8a53f877f37eb762950aa4f85e0b9a914ea891d`; that revision was not
adopted. The bridge has its own `Cargo.lock` and does not change the shared
library.

Baseline `:sdk:testDebugUnitTest` passed. Final bridge Rust tests passed (2),
and `:sdk:testDebugUnitTest` passed 118 tests, including three tests that load
the actual host JNI library and exercise the production gate's admission and
timestamp updates. `:sdk:assembleDebug`, `:sdk:assembleRelease`, and
`:app:assembleDebug` passed. Artifact inspection confirmed four ABI libraries
in the release AAR and demo APK, 16 KiB ELF PT_LOAD alignment, and aligned
uncompressed APK entries. The Android instrumented gate test is added and
compiled, but no device was attached to run it. Placement, lifecycle retention,
and `RESUME` bypass were inspected in `SessionRuntime.serve`; a full TLS pairing
flow was not run on a device. `git diff --check` passed. No push or deployment.
