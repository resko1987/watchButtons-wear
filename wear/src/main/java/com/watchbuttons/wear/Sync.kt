package com.watchbuttons.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

fun sendCommandWithAck(
    context: Context,
    action: String,
    id: String? = null,
    onResult: (Boolean) -> Unit
) {
    val client = Wearable.getMessageClient(context)
    val payload = JSONObject().apply {
        put("action", action)
        if (id != null) put("id", id)
    }.toString().toByteArray()

    Wearable.getNodeClient(context).connectedNodes
        .addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                onResult(false)
                return@addOnSuccessListener
            }
            val remaining = AtomicInteger(nodes.size)
            val sent = AtomicBoolean(false)
            nodes.forEach { node ->
                client.sendMessage(node.id, "/command", payload)
                    .addOnSuccessListener {
                        sent.set(true)
                        if (remaining.decrementAndGet() == 0) onResult(sent.get())
                    }
                    .addOnFailureListener {
                        if (remaining.decrementAndGet() == 0) onResult(sent.get())
                    }
            }
        }
        .addOnFailureListener { onResult(false) }
}