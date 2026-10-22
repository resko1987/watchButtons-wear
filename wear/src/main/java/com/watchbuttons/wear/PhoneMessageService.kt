package com.watchbuttons.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

class PhoneMessageService : WearableListenerService() {

    private val controller by lazy { SoundController(this) }
    private val store by lazy { WearButtonStore(this) }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/command") return
        val json = JSONObject(String(event.data))
        when (json.optString("action")) {
            "dnd" -> controller.setMode("dnd")
            "vibrate" -> controller.setMode("vibrate")
            "normal" -> controller.setMode("normal")
        }
    }

    override fun onDataChanged(dataEvents: com.google.android.gms.wearable.DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.dataItem.uri.path == "/buttons") {
                store.load { state -> ButtonState.apply(state) }
            }
        }
        dataEvents.release()
    }
}