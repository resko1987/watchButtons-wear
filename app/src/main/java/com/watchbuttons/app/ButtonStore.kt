package com.watchbuttons.app

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray
import java.util.UUID

class ButtonStore(context: Context) {
    private val prefs = context.getSharedPreferences("buttons", Context.MODE_PRIVATE)
    private val dataClient: DataClient = Wearable.getDataClient(context)

    fun load(): List<ButtonData> {
        val arr = JSONArray(prefs.getString("custom", "[]"))
        return (0 until arr.length()).map { i -> ButtonData.fromJson(arr.getJSONObject(i)) }
    }

    fun add(name: String, color: String, single: ButtonAction, double: ButtonAction?, folder: String): List<ButtonData> {
        val safeFolder = if (folder.isBlank()) DEFAULT_FOLDER else folder
        val list = load() + ButtonData(UUID.randomUUID().toString(), name, color, single, double, safeFolder)
        save(list)
        return list
    }

    fun remove(id: String): List<ButtonData> {
        val list = load().filter { it.id != id }
        save(list)
        return list
    }

    fun update(id: String, name: String, color: String, single: ButtonAction, double: ButtonAction?, folder: String): List<ButtonData> {
        val safeFolder = if (folder.isBlank()) DEFAULT_FOLDER else folder
        val list = load().map {
            if (it.id == id) it.copy(name = name, color = color, single = single, double = double, folder = safeFolder) else it
        }
        save(list)
        return list
    }

    fun getFolders(): List<String> {
        val arr = JSONArray(prefs.getString("folders", "[]"))
        val result = mutableListOf(DEFAULT_FOLDER)
        for (i in 0 until arr.length()) {
            val f = arr.getString(i)
            if (f != DEFAULT_FOLDER) result.add(f)
        }
        return result
    }

    fun addFolder(name: String): List<String> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return getFolders()
        val folders = getFolders()
        if (folders.any { it.equals(trimmed, ignoreCase = true) }) return folders
        val result = folders + trimmed
        saveFolders(result)
        return result
    }

    fun removeFolder(name: String): List<String> {
        if (name == DEFAULT_FOLDER) return getFolders()
        val buttons = load().map {
            if (it.folder == name) it.copy(folder = DEFAULT_FOLDER) else it
        }
        save(buttons)
        val result = getFolders().filter { it != name }
        saveFolders(result)
        return result
    }

    private fun saveFolders(folders: List<String>) {
        val arr = JSONArray()
        folders.filter { it != DEFAULT_FOLDER }.forEach { arr.put(it) }
        prefs.edit().putString("folders", arr.toString()).apply()
        syncToWear(load(), getLayout())
    }

    fun save(buttons: List<ButtonData>) {
        val arr = JSONArray()
        buttons.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("custom", arr.toString()).apply()
        syncToWear(buttons, getLayout())
    }

    fun getLayout(): String = prefs.getString("layout", "1") ?: "1"

    fun setLayout(layout: String) {
        prefs.edit().putString("layout", layout).apply()
        syncToWear(load(), layout)
    }

    fun getShowModes(): Boolean = prefs.getBoolean("show_modes", true)

    fun setShowModes(value: Boolean) {
        prefs.edit().putBoolean("show_modes", value).apply()
        syncToWear(load(), getLayout())
    }

    fun getShowResponse(): Boolean = prefs.getBoolean("show_response", true)

    fun setShowResponse(value: Boolean) {
        prefs.edit().putBoolean("show_response", value).apply()
        syncToWear(load(), getLayout())
    }

    private fun syncToWear(buttons: List<ButtonData>, layout: String) {
        val arr = JSONArray()
        buttons.forEach { arr.put(it.toJson()) }
        val foldersArr = JSONArray()
        getFolders().forEach { foldersArr.put(it) }
        val request = PutDataMapRequest.create("/buttons").apply {
            dataMap.putString("buttons_json", arr.toString())
            dataMap.putString("folders_json", foldersArr.toString())
            dataMap.putString("layout", layout)
            dataMap.putBoolean("show_modes", getShowModes())
            dataMap.putBoolean("show_response", getShowResponse())
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }
        dataClient.putDataItem(request.asPutDataRequest())
    }
}