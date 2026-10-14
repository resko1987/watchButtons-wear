package com.watchbuttons.app

import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings

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

    fun isPolicyAccessGranted(): Boolean = nm.isNotificationPolicyAccessGranted

    fun requestPolicyAccess(): Boolean {
        try {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return true
        } catch (e: Exception) {
        }
        return try {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            false
        }
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
        if (isPolicyAccessGranted()) {
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
        return ok || ringMuted()
    }

    fun vibrateOnly(): Boolean {
        var ok = false
        if (isPolicyAccessGranted()) {
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
        return ok || ringMuted()
    }

    fun normalMode(): Boolean {
        var ok = false
        if (isPolicyAccessGranted()) {
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
        return ok || ringOn
    }
}