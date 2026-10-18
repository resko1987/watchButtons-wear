package com.watchbuttons.wear

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray

data class SyncState(
    val buttons: List<ButtonData>,
    val layout: String,
    val showModes: Boolean,
    val showResponse: Boolean,
    val folders: List<String>
)

object ButtonState {
    var buttons by mutableStateOf(listOf<ButtonData>())
    var layout by mutableStateOf("1")
    var showModes by mutableStateOf(true)
    var showResponse by mutableStateOf(true)
    var folders by mutableStateOf(listOf(DEFAULT_FOLDER))

    fun apply(state: SyncState) {
        buttons = state.buttons
        layout = state.layout
        showModes = state.showModes
        showResponse = state.showResponse
        folders = state.folders
    }
}

class WearButtonStore(private val context: Context) {

    fun load(callback: (SyncState) -> Unit) {
        Wearable.getDataClient(context).dataItems.addOnSuccessListener { buffer ->
            val buttons = mutableListOf<ButtonData>()
            var layout = "1"
            var showModes = true
            var showResponse = true
            var folders = listOf(DEFAULT_FOLDER)
            for (item in buffer) {
                if (item.uri.path == "/buttons") {
                    val map = DataMapItem.fromDataItem(item).dataMap
                    val arr = JSONArray(map.getString("buttons_json", "[]"))
                    for (i in 0 until arr.length()) {
                        buttons.add(ButtonData.fromJson(arr.getJSONObject(i)))
                    }
                    layout = map.getString("layout", "1")
                    showModes = map.getBoolean("show_modes", true)
                    showResponse = map.getBoolean("show_response", true)
                    val foldersArr = JSONArray(map.getString("folders_json", "[]"))
                    val list = mutableListOf<String>()
                    for (i in 0 until foldersArr.length()) {
                        list.add(foldersArr.getString(i))
                    }
                    if (list.isNotEmpty()) folders = list
                }
            }
            buffer.release()
            callback(SyncState(buttons, layout, showModes, showResponse, folders))
        }
    }
}