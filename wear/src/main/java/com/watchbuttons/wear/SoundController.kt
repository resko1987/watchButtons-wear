package com.watchbuttons.wear

import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ModeState {
    var mode by mutableStateOf("normal")
}

class SoundController(private val context: Context) {
    private val nm = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = context.getSharedPreferences("sound_state", Context.MODE_PRIVATE)

    private val ringStreams = intArrayOf(
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION
    )

    private val mediaStreams = intArrayOf(
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_ALARM
    )

    private val allStreams = ringStreams + mediaStreams

    init {
        ModeState.mode = currentMode()
    }

    fun currentMode(): String {
        prefs.getString("applied_mode", null)?.let { return it }
        val filter = nm.currentInterruptionFilter
        return if (
            filter == NotificationManager.INTERRUPTION_FILTER_NONE ||
            filter == NotificationManager.INTERRUPTION_FILTER_ALARMS
        ) {
            "dnd"
        } else {
            "normal"
        }
    }

    fun setMode(mode: String): Boolean {
        val applied = try {
            when (mode) {
                "dnd" -> applyDnd()
                "vibrate" -> applyVibrate()
                "normal" -> applyNormal()
                else -> false
            }
        } catch (e: Exception) {
            false
        }
        prefs.edit().putString("applied_mode", mode).apply()
        ModeState.mode = mode
        return applied
    }

    private fun applyDnd(): Boolean {
        var ok = false
        if (nm.isNotificationPolicyAccessGranted) {
            ok = trySetInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE) || ok
        }
        ok = trySetRingerMode(AudioManager.RINGER_MODE_SILENT) || ok
        saveAndMute(allStreams)
        return ok || allMuted()
    }

    private fun applyVibrate(): Boolean {
        var ok = false
        if (nm.isNotificationPolicyAccessGranted) {
            ok = trySetInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) || ok
        }
        restore(mediaStreams)
        ok = trySetRingerMode(AudioManager.RINGER_MODE_VIBRATE) || ok
        saveAndMute(ringStreams)
        return ok || isMuted(AudioManager.STREAM_RING)
    }

    private fun applyNormal(): Boolean {
        var ok = false
        if (nm.isNotificationPolicyAccessGranted) {
            ok = trySetInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) || ok
        }
        val hadSavedMutes = hasSavedMutes()
        val restored = restore(allStreams)
        ok = trySetRingerMode(AudioManager.RINGER_MODE_NORMAL) || ok
        return ok || restored > 0 || !hadSavedMutes
    }

    private fun trySetInterruptionFilter(filter: Int): Boolean = try {
        nm.setInterruptionFilter(filter)
        true
    } catch (e: Exception) {
        false
    }

    private fun trySetRingerMode(mode: Int): Boolean = try {
        am.ringerMode = mode
        true
    } catch (e: Exception) {
        false
    }

    private fun isMuted(stream: Int): Boolean = try {
        am.getStreamVolume(stream) == 0
    } catch (e: Exception) {
        false
    }

    private fun allMuted(): Boolean = allStreams.all { isMuted(it) }

    private fun hasSavedMutes(): Boolean =
        prefs.all.keys.any { it.startsWith("stream_") }

    private fun saveAndMute(streams: IntArray) {
        val editor = prefs.edit()
        for (s in streams) {
            val v = try {
                am.getStreamVolume(s)
            } catch (e: Exception) {
                0
            }
            if (v > 0) editor.putInt("stream_$s", v)
            try {
                am.setStreamVolume(s, 0, 0)
            } catch (e: Exception) {
            }
        }
        editor.apply()
    }

    private fun restore(streams: IntArray): Int {
        var restored = 0
        for (s in streams) {
            val saved = prefs.getInt("stream_$s", -1)
            if (saved > 0) {
                try {
                    am.setStreamVolume(s, saved, 0)
                    restored++
                } catch (e: Exception) {
                }
                prefs.edit().remove("stream_$s").apply()
            }
        }
        return restored
    }
}