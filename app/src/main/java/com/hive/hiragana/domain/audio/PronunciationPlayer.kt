package com.hive.hiragana.domain.audio

import kotlinx.coroutines.flow.StateFlow

/** Whether the device can speak Japanese, and if not, why. */
enum class VoiceStatus {
    /** The engine has no Japanese voice at all — nothing the app can do in-app. */
    UNAVAILABLE,

    /** Japanese is supported but the voice data isn't downloaded — offer the
     *  system install flow (see PronunciationButton). */
    NEEDS_DOWNLOAD,

    /** A Japanese voice is installed and ready to speak. */
    READY,
}

/**
 * Speaks a kana glyph (a single character OR a whole word) aloud via the
 * device's built-in text-to-speech. The input is always the raw kana glyph
 * string, never the romaji.
 */
interface PronunciationPlayer {

    /**
     * The Japanese-voice status, driving the UI: [VoiceStatus.READY] enables
     * playback, [VoiceStatus.NEEDS_DOWNLOAD] routes a tap to the install flow,
     * [VoiceStatus.UNAVAILABLE] disables the button. Flips from its initial
     * value once the async engine init completes.
     */
    val status: StateFlow<VoiceStatus>

    /**
     * Play [glyph] (a kana or a whole word). Main-thread safe and
     * fire-and-forget: a rapid re-tap cancels the in-flight sound rather than
     * queueing, and a call while not [VoiceStatus.READY] simply no-ops.
     */
    fun play(glyph: String)

    /**
     * Re-evaluate [status] against the engine — call after the user may have
     * installed voice data (e.g. on returning to the app) so a freshly
     * downloaded voice flips the button from NEEDS_DOWNLOAD to READY.
     */
    fun refresh()

    /** Free native resources. Idempotent — safe to call more than once. */
    fun release()
}
