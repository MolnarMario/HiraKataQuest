package com.hive.hiragana.data.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.hive.hiragana.domain.audio.PronunciationPlayer
import com.hive.hiragana.domain.audio.VoiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Speaks kana via the device's built-in [TextToSpeech], set to Japanese. We
 * feed it the raw kana glyph (never the romaji) so pronunciation is authentic;
 * a single character and a whole word are both just glyph strings, so there is
 * one code path.
 *
 * Quality depends on the voice installed on the device. [status] reports
 * whether a Japanese voice is READY, merely NEEDS_DOWNLOAD (the app offers the
 * system install flow), or UNAVAILABLE.
 */
class TtsPronunciationPlayer(context: Context) : PronunciationPlayer {

    private val _status = MutableStateFlow(VoiceStatus.UNAVAILABLE)
    override val status: StateFlow<VoiceStatus> = _status.asStateFlow()

    // Created immediately; the callback fires asynchronously. Until then the
    // status is UNAVAILABLE, so play() no-ops — this is the init-race guard.
    private var tts: TextToSpeech? = null
    private var initialized = false

    init {
        tts = TextToSpeech(context.applicationContext) { code ->
            if (code == TextToSpeech.SUCCESS) {
                initialized = true
                evaluate()
            } else {
                Log.w(TAG, "TTS init failed (status=$code)")
                _status.value = VoiceStatus.UNAVAILABLE
            }
        }
    }

    /** Ask the engine whether Japanese is ready / downloadable / unsupported. */
    private fun evaluate() {
        val engine = tts ?: return
        _status.value = when (engine.setLanguage(Locale.JAPANESE)) {
            TextToSpeech.LANG_MISSING_DATA -> VoiceStatus.NEEDS_DOWNLOAD
            TextToSpeech.LANG_NOT_SUPPORTED -> VoiceStatus.UNAVAILABLE
            else -> VoiceStatus.READY
        }
    }

    override fun play(glyph: String) {
        if (_status.value != VoiceStatus.READY) return
        // QUEUE_FLUSH: a fast re-tap replaces the current utterance instead of
        // queueing several to play back to back.
        tts?.speak(glyph, TextToSpeech.QUEUE_FLUSH, null, glyph)
    }

    override fun refresh() {
        // After the user may have installed a voice, re-check so the button can
        // flip NEEDS_DOWNLOAD -> READY without an app restart.
        if (initialized) evaluate()
    }

    override fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        initialized = false
        _status.value = VoiceStatus.UNAVAILABLE
    }

    private companion object {
        const val TAG = "TtsPronunciation"
    }
}
