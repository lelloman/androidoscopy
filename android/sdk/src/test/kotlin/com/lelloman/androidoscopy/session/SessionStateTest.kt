package com.lelloman.androidoscopy.session

import java.io.EOFException
import java.net.SocketException
import org.junit.Assert.*
import org.junit.Test

class SessionStateTest {
    @Test fun `automatic approvals are never remembered even in debug builds`() {
        assertTrue(shouldRememberPeer(true, PairingDecision.MANUAL))
        assertFalse(shouldRememberPeer(false, PairingDecision.MANUAL))
        assertFalse(shouldRememberPeer(true, PairingDecision.AUTOMATIC))
        assertFalse(shouldRememberPeer(false, PairingDecision.AUTOMATIC))
        assertFalse(shouldRememberPeer(true, PairingDecision.REJECTED))
    }
    @Test fun `automatic approval defaults off and requires active foreground session`() {
        assertFalse(SessionState().acceptAll)
        assertThrows(IllegalStateException::class.java) { SessionState().withAcceptAll(true, true) }
        assertThrows(IllegalStateException::class.java) { SessionState(active = true).withAcceptAll(true, false) }
        assertTrue(SessionState(active = true).withAcceptAll(true, true).acceptAll)
    }

    @Test fun `enabling clears pending dialog and disabling does not disconnect approved peer`() {
        val state = SessionState(active = true, peer = "pc", pairing = PairingRequest("id", "123", "address"))
        val enabled = state.withAcceptAll(true, true)
        assertNull(enabled.pairing)
        val disabled = enabled.withAcceptAll(false, false)
        assertFalse(disabled.acceptAll)
        assertEquals("pc", disabled.peer)
        assertFalse(SessionState(active = true, sessionId = "next").acceptAll)
    }

    @Test fun `successful connection clears stale disconnect reason and pairing`() {
        val connected = SessionState(active = true, reason = "PC disconnected", acceptAll = true,
            pairing = PairingRequest("id", "123", "address")).withConnectedPeer("pc")
        assertNull(connected.reason)
        assertNull(connected.pairing)
        assertEquals("pc", connected.peer)
        assertTrue(connected.acceptAll)
    }

    @Test fun `disconnects and failures have friendly messages instead of exception class names`() {
        assertEquals("PC disconnected. Waiting for a connection.", connectionEndedReason(EOFException()))
        assertEquals("PC disconnected. Waiting for a connection.", connectionEndedReason(SocketException()))
        assertEquals("Pairing declined. Waiting for a connection.", connectionEndedReason(IllegalArgumentException("PAIRING_REJECTED")))
        assertEquals("Connection failed. Try connecting again.", connectionEndedReason(IllegalStateException("internal details")))
    }
}
