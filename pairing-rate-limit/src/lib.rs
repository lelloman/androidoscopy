use simple_server::rate_limit::{Budget, Quota};
use std::{ffi::c_void, num::NonZeroU32, time::Duration};

// Matches SessionRuntime's uninitialized timestamp. The runtime owns this state,
// so stopping and restarting a diagnostic session cannot reset the gate.
const NEVER_ADMITTED: i64 = i64::MIN / 2;
const INTERVAL: Duration = Duration::from_secs(5);

fn admit(last_admitted_ms: i64, now_ms: i64) -> bool {
    if now_ms < 0 || (last_admitted_ms != NEVER_ADMITTED && last_admitted_ms < 0) {
        return false;
    }
    let quota = Quota::replenishing(INTERVAL, NonZeroU32::new(1).unwrap()).unwrap();
    let mut budget = Budget::new(quota);
    let unit = NonZeroU32::new(1).unwrap();
    if last_admitted_ms != NEVER_ADMITTED
        && budget
            .check_at(Duration::from_millis(last_admitted_ms as u64), unit)
            .is_err()
    {
        return false;
    }
    budget
        .check_at(Duration::from_millis(now_ms as u64), unit)
        .is_ok()
}

// Java object method: the JNI environment and receiver are unused. No native
// handle survives a call, and Kotlin retains the timestamp only after admission.
#[no_mangle]
pub extern "system" fn Java_com_lelloman_androidoscopy_session_PairingRateLimit_nativeAdmit(
    _env: *mut c_void,
    _receiver: *mut c_void,
    last_admitted_ms: i64,
    now_ms: i64,
) -> u8 {
    u8::from(admit(last_admitted_ms, now_ms))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn admitted_attempts_are_exactly_five_seconds_apart() {
        assert!(admit(NEVER_ADMITTED, 100));
        assert!(!admit(100, 100));
        assert!(!admit(100, 5_099));
        assert!(admit(100, 5_100));
        assert!(!admit(5_100, 5_099));
    }

    #[test]
    fn a_denial_does_not_advance_caller_owned_state() {
        let last = 20_000;
        assert!(!admit(last, 24_999));
        assert!(admit(last, 25_000));
    }
}
