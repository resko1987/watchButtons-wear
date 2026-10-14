package com.watchbuttons.wear

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray

object ButtonState {
    var buttons by mutableStateOf(listOf<ButtonData>())
    var layout by mutableStateOf("1")
    var showModes by mutableStateOf(true)
    var showResponse by mutableStateOf(true)
}

class WearButtonStore(private val context: Context) {

    fun load(callback: (List<ButtonData>, String, Boolean, Boolean) -> Unit) {
        Wearable.getDataClient(context).dataItems.addOnSuccessListener { buffer ->
            val result = mutableListOf<ButtonData>()
            var layout = "1"
            var showModes = true
            var showResponse = true
            for (item in buffer) {
                if (item.uri.path == "/buttons") {
                    val map = DataMapItem.fromDataItem(item).dataMap
                    val arr = JSONArray(map.getString("buttons_json", "[]"))
                    for (i in 0 until arr.length()) {
                        result.add(ButtonData.fromJson(arr.getJSONObject(i)))
                    }
                    layout = map.getString("layout", "1")
                    showModes = map.getBoolean("show_modes", true)
                    showResponse = map.getBoolean("show_response", true)
                }
            }
            buffer.release()
            callback(result, layout, showModes, showResponse)
        }
    }
}