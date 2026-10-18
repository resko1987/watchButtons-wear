package com.watchbuttons.wear

import org.json.JSONObject

const val DEFAULT_FOLDER = "default"

data class ButtonAction(
    val url: String,
    val method: String = "GET",
    val body: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("url", url)
        .put("method", method)
        .put("body", body)

    companion object {
        fun fromJson(o: JSONObject) = ButtonAction(
            url = o.optString("url"),
            method = o.optString("method", "GET"),
            body = o.optString("body")
        )
    }
}

data class ButtonData(
    val id: String,
    val name: String,
    val color: String = "#5B8DEF",
    val single: ButtonAction,
    val double: ButtonAction? = null,
    val folder: String = DEFAULT_FOLDER
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
            .put("id", id)
            .put("name", name)
            .put("color", color)
            .put("single", single.toJson())
            .put("folder", folder)
        double?.let { o.put("double", it.toJson()) }
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): ButtonData {
            val single = if (o.has("single")) {
                ButtonAction.fromJson(o.getJSONObject("single"))
            } else {
                ButtonAction(o.optString("url"), o.optString("method", "GET"), o.optString("body"))
            }
            val double = if (o.has("double") && !o.isNull("double")) {
                ButtonAction.fromJson(o.getJSONObject("double"))
            } else {
                null
            }
            return ButtonData(
                id = o.optString("id"),
                name = o.optString("name"),
                color = o.optString("color", "#5B8DEF"),
                single = single,
                double = double,
                folder = o.optString("folder", DEFAULT_FOLDER)
            )
        }
    }
}