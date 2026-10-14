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
            "dnd" -> controller.doNotDisturb()
            "vibrate" -> controller.vibrateOnly()
            "normal" -> controller.normalMode()
        }
    }

    override fun onDataChanged(dataEvents: com.google.android.gms.wearable.DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.dataItem.uri.path == "/buttons") {
                store.load { buttons, layout, showModes, showResponse ->
                    ButtonState.buttons = buttons
                    ButtonState.layout = layout
                    ButtonState.showModes = showModes
                    ButtonState.showResponse = showResponse
                }
            }
        }
        dataEvents.release()
    }
}