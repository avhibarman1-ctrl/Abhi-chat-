package com.example

import com.example.data.model.AudiencePrivacy
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.FriendRequestPrivacy
import com.example.data.model.MessageType
import com.example.data.model.PrivacyLevel
import com.example.util.LocationHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `enum fallbacks handle unknown raw strings safely`() {
        assertEquals(PrivacyLevel.EVERYONE, PrivacyLevel.fromRaw("UNKNOWN"))
        assertEquals(PrivacyLevel.FRIENDS, PrivacyLevel.fromRaw("FRIENDS"))
        assertEquals(FriendRequestPrivacy.EVERYONE, FriendRequestPrivacy.fromRaw(null))
        assertEquals(FriendRequestPrivacy.NOBODY, FriendRequestPrivacy.fromRaw("NOBODY"))
        assertEquals(AudiencePrivacy.ONLY_ME, AudiencePrivacy.fromRaw("ONLY_ME"))
        assertEquals(MessageType.TEXT, MessageType.fromRaw("BAD"))
        assertEquals(MessageType.LOCATION, MessageType.fromRaw("LOCATION"))
        assertEquals(CallType.VOICE, CallType.fromRaw(null))
        assertEquals(CallType.VIDEO, CallType.fromRaw("VIDEO"))
        assertEquals(CallStatus.ENDED, CallStatus.fromRaw("STALE"))
    }

    @Test
    fun `location formatting uses 5 decimal places`() {
        assertEquals("28.61390, 77.20900", LocationHelper.formatCoordinates(28.6139, 77.209))
    }
}
