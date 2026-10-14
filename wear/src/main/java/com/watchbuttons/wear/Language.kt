package com.watchbuttons.wear

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

object Language {
    private const val PREFS = "settings"
    private const val KEY = "language"

    val options = listOf(
        "en" to "English",
        "ru" to "Русский",
        "zh" to "中文"
    )

    fun displayName(tag: String): String =
        options.firstOrNull { it.first == tag }?.second ?: "English"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "en") ?: "en"

    fun set(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, tag).apply()
    }

    fun wrapContext(context: Context): Context {
        val tag = get(context)
        val localeList = LocaleList.forLanguageTags(tag)
        Locale.setDefault(localeList.get(0))
        val config = Configuration(context.resources.configuration)
        config.setLocales(localeList)
        return context.createConfigurationContext(config)
    }
}