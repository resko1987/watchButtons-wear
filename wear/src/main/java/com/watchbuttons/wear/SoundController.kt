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

    private val allStreams = intArrayOf(
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_ALARM
    )

    init {
        ModeState.mode = currentMode()
    }

    fun currentMode(): String {
        val saved = prefs.getString("applied_mode", null)
        if (saved != null) return saved
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

    private fun saveAppliedMode(mode: String) {
        prefs.edit().putString("applied_mode", mode).apply()
        ModeState.mode = mode
    }

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

    private fun restore() {
        for (s in allStreams) {
            val saved = prefs.getInt("stream_$s", -1)
            if (saved > 0) {
                try {
                    am.setStreamVolume(s, saved, 0)
                } catch (e: Exception) {
                }
                prefs.edit().remove("stream_$s").apply()
            }
        }
    }

    private fun ringMuted(): Boolean = try {
        am.getStreamVolume(AudioManager.STREAM_RING) == 0
    } catch (e: Exception) {
        false
    }

    fun doNotDisturb(): Boolean {
        var ok = false
        if (nm.isNotificationPolicyAccessGranted) {
            try {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                ok = true
            } catch (e: Exception) {
            }
        }
        try {
            am.ringerMode = AudioManager.RINGER_MODE_SILENT
            ok = true
        } catch (e: Exception) {
        }
        saveAndMute(allStreams)
        val success = ok || ringMuted()
        if (success) saveAppliedMode("dnd")
        return success
    }

    fun vibrateOnly(): Boolean {
        var ok = false
        if (nm.isNotificationPolicyAccessGranted) {
            try {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                ok = true
            } catch (e: Exception) {
            }
        }
        try {
            am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            ok = true
        } catch (e: Exception) {
        }
        saveAndMute(intArrayOf(AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION))
        val success = ok || ringMuted()
        if (success) saveAppliedMode("vibrate")
        return success
    }

    fun normalMode(): Boolean {
        var ok = false
        if (nm.isNotificationPolicyAccessGranted) {
            try {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                ok = true
            } catch (e: Exception) {
            }
        }
        try {
            am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            ok = true
        } catch (e: Exception) {
        }
        restore()
        val ringOn = try {
            am.ringerMode == AudioManager.RINGER_MODE_NORMAL ||
                am.getStreamVolume(AudioManager.STREAM_RING) > 0
        } catch (e: Exception) {
            false
        }
        val success = ok || ringOn
        if (success) saveAppliedMode("normal")
        return success
    }
}