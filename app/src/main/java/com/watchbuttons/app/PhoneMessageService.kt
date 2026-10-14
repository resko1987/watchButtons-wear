package com.watchbuttons.app

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

class PhoneMessageService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/command") return
        val json = JSONObject(String(event.data))
        val controller = SoundController(this)
        when (json.optString("action")) {
            "dnd" -> controller.doNotDisturb()
            "vibrate" -> controller.vibrateOnly()
            "normal" -> controller.normalMode()
        }
    }
}