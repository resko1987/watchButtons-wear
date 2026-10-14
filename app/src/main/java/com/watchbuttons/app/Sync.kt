package com.watchbuttons.app

import android.content.Context
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

fun sendCommand(context: Context, action: String, id: String? = null) {
    val client = Wearable.getMessageClient(context)
    val payload = JSONObject().apply {
        put("action", action)
        if (id != null) put("id", id)
    }.toString().toByteArray()
    Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
        nodes.forEach { client.sendMessage(it.id, "/command", payload) }
    }
}