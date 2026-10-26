package com.example

import android.speech.tts.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class TtsVoicePickerTest {

    @Test
    fun testQualityWinsOverNetworkRequirement() {
        // Case a: Prefers quality 500 offline over quality 400 network (quality wins)
        val highOffline = Voice(
            "en-us-high-offline",
            Locale.US,
            500,
            Voice.LATENCY_NORMAL,
            false,
            emptySet()
        )
        val normalNetwork = Voice(
            "en-us-med-network",
            Locale.US,
            400,
            Voice.LATENCY_NORMAL,
            true,
            emptySet()
        )

        val best = pickBestVoice(listOf(normalNetwork, highOffline), null)
        assertEquals("Quality 500 offline should win over quality 400 network", highOffline, best)
    }

    @Test
    fun testNetworkTiebreakWhenQualityIsEqual() {
        // Case b: Prefers quality 400 network over quality 400 offline (network tiebreak)
        val offlineVoice = Voice(
            "en-us-offline",
            Locale.US,
            400,
            Voice.LATENCY_NORMAL,
            false,
            emptySet()
        )
        val networkVoice = Voice(
            "en-us-network",
            Locale.US,
            400,
            Voice.LATENCY_NORMAL,
            true,
            emptySet()
        )

        val best = pickBestVoice(listOf(offlineVoice, networkVoice), null)
        assertEquals("Quality 400 network should win over quality 400 offline", networkVoice, best)
    }

    @Test
    fun testSavedVoiceNameIsPrioritized() {
        // Case c: Saved voice with matching name is returned first even if it's lower quality
        val lowQualitySaved = Voice(
            "en-gb-custom",
            Locale.UK,
            200,
            Voice.LATENCY_NORMAL,
            false,
            emptySet()
        )
        val veryHighQualityVoice = Voice(
            "en-us-neural-network",
            Locale.US,
            500,
            Voice.LATENCY_NORMAL,
            true,
            emptySet()
        )

        val best = pickBestVoice(listOf(veryHighQualityVoice, lowQualitySaved), "en-gb-custom")
        assertEquals("Saved voice should be returned even if lower quality", lowQualitySaved, best)
    }

    @Test
    fun testCompactVoiceIsExcluded() {
        // Case d: Compact voice (name contains 'compact') is excluded
        val compactVoice = Voice(
            "en-us-compact-robotic",
            Locale.US,
            500,
            Voice.LATENCY_NORMAL,
            false,
            emptySet()
        )

        val best = pickBestVoice(listOf(compactVoice), null)
        assertNull("Compact voice must be excluded", best)
    }
}
