package com.stay22.capacitor

import com.stay22.sdk.NotificationTiming
import com.stay22.sdk.Stay22Event
import org.junit.Assert.*
import org.junit.Test

class NotificationBridgeCodecTest {
    @Test fun `event facts survive without parsing descriptions or truncating delay`() {
        val scheduled = NotificationBridgeCodec.map(Stay22Event.NotificationScheduled("Paris", 1.9))
        assertEquals("Paris", scheduled["destination"])
        assertEquals(1.9, scheduled["delay"])
        assertEquals("blocked-proof", NotificationBridgeCodec.map(Stay22Event.NotificationBlocked("blocked-proof"))["reason"])
        assertEquals(false, NotificationBridgeCodec.map(Stay22Event.EnabledChanged(false))["isEnabled"])
        val clicked = NotificationBridgeCodec.map(Stay22Event.NotificationClicked("Paris", "https://example.com/saved"))
        assertEquals("Paris", clicked["destination"])
        assertEquals("https://example.com/saved", clicked["url"])
    }

    @Test fun `invalid business values reach native without normalization`() {
        val request = NotificationBridgeCodec.request(mapOf("trip" to mapOf("latitude" to 91.0, "adults" to -1,
            "checkinDate" to "2026-02-30", "context" to mapOf("purpose" to "concert")),
            "timing" to mapOf("type" to "after", "delayMs" to 123L)))
        assertEquals(91.0, request.trip.latitude)
        assertEquals(-1, request.trip.adults)
        assertEquals("2026-02-30", request.trip.checkinDate)
        assertEquals(mapOf("purpose" to "concert"), request.trip.context)
        assertEquals(NotificationTiming.After(123), request.timing)
    }

    @Test fun `transport never truncates deadlines revisions or guest counts`() {
        for (value in listOf(1.5, true, Double.NaN, Double.POSITIVE_INFINITY, 9007199254740992L)) {
            assertThrows(IllegalArgumentException::class.java) {
                NotificationBridgeCodec.reference(mapOf("id" to "original", "revision" to value, "mode" to "normal"))
            }
        }
        assertThrows(IllegalArgumentException::class.java) { NotificationBridgeCodec.trip(mapOf("adults" to 2147483648L)) }
        assertEquals(" original ", NotificationBridgeCodec.reference(mapOf("id" to " original ", "revision" to 4, "mode" to "diagnostic")).id)
    }

    @Test fun `missing timing is default but malformed timing and diagnostics options reject`() {
        assertEquals(NotificationTiming.AccountDefault, NotificationBridgeCodec.request(mapOf("trip" to emptyMap<String, String>())).timing)
        for (value in listOf(null, mapOf("type" to "after"), mapOf("type" to "accountDefault", "delayMs" to 5))) {
            assertThrows(IllegalArgumentException::class.java) {
                NotificationBridgeCodec.request(mapOf("trip" to emptyMap<String, String>(), "timing" to value))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            NotificationBridgeCodec.command("scheduleDiagnosticNotification", mapOf("trip" to emptyMap<String, String>(), "force" to true))
        }
    }

    @Test fun `preinit remains a native acknowledgment with explicit null receipt`() {
        val result = NotificationBridgeCodec.command("ensureNotification", mapOf("trip" to mapOf("address" to "Paris")))
        assertEquals("rejected", result["outcome"])
        assertEquals("not_initialized", result["reason"])
        assertEquals("uncertain", result["previousEffect"])
        assertTrue(result.containsKey("receipt"))
        assertNull(result["receipt"])
        assertNotNull(result["capabilities"])
    }
}
