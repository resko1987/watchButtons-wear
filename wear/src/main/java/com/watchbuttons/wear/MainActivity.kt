package com.watchbuttons.wear

import android.content.Context
import android.content.Context.VIBRATOR_MANAGER_SERVICE
import android.content.Context.VIBRATOR_SERVICE
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TitleCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DOUBLE_CLICK_MS = 350L

private val COLOR_DND = "#D32F2F"
private val COLOR_VIBRATE = "#F57C00"
private val COLOR_NORMAL = "#388E3C"

private fun hexToColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: IllegalArgumentException) {
    Color(0xFF5B8DEF)
}

private fun contentColorFor(color: Color): Color =
    if (color.luminance() > 0.5f) Color(0xFF141414) else Color.White

class MainActivity : ComponentActivity() {

    private val store by lazy { WearButtonStore(this) }
    private val controller by lazy { SoundController(this) }

    private var pendingClick: Job? = null
    private var pendingButtonId: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Language.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WearApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        store.load { state -> ButtonState.apply(state) }
    }

    private fun switchLanguage() {
        val tags = Language.options.map { it.first }
        val next = tags[(tags.indexOf(Language.get(this)) + 1) % tags.size]
        Language.set(this, next)
        recreate()
    }

    private fun hapticTick() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } catch (e: Exception) {
        }
    }

    private fun applyPreset(action: String) {
        hapticTick()
        val localOk = when (action) {
            "dnd" -> controller.doNotDisturb()
            "vibrate" -> controller.vibrateOnly()
            "normal" -> controller.normalMode()
            else -> false
        }
        sendCommandWithAck(this, action) { delivered ->
            val label = if (delivered || localOk) {
                getString(R.string.toast_mode_enabled)
            } else {
                getString(R.string.toast_mode_failed)
            }
            Toast.makeText(this@MainActivity, label, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onCustomButtonTap(button: ButtonData) {
        hapticTick()
        val double = button.double
        if (double == null) {
            executeAction(button, button.single)
            return
        }
        if (pendingClick?.isActive == true && pendingButtonId == button.id) {
            pendingClick?.cancel()
            pendingClick = null
            executeAction(button, double)
        } else {
            pendingClick?.cancel()
            pendingButtonId = button.id
            pendingClick = lifecycleScope.launch {
                delay(DOUBLE_CLICK_MS)
                executeAction(button, button.single)
            }
        }
    }

    private fun executeAction(button: ButtonData, action: ButtonAction) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { HttpExecutor.execute(action) }
            val text = when {
                result.error != null -> getString(R.string.toast_error, result.error)
                result.code in 200..399 -> {
                    val base = getString(R.string.toast_done)
                    if (ButtonState.showResponse && result.body.isNotBlank()) {
                        "$base\n${result.body.take(120)}"
                    } else {
                        base
                    }
                }
                else -> getString(R.string.toast_http_error, result.code)
            }
            Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
        }
    }

    @Composable
    fun WearApp() {
        val layout = ButtonState.layout
        val buttons = ButtonState.buttons
        val showModes = ButtonState.showModes
        val listState = rememberScalingLazyListState()
        val scope = rememberCoroutineScope()
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
        }

        val presets = listOf(
            Triple("dnd", stringResource(R.string.wear_dnd), stringResource(R.string.wear_dnd_desc)),
            Triple("vibrate", stringResource(R.string.wear_vibrate), stringResource(R.string.wear_vibrate_desc)),
            Triple("normal", stringResource(R.string.wear_normal), stringResource(R.string.wear_normal_desc))
        )
        val presetColors = mapOf(
            "dnd" to COLOR_DND,
            "vibrate" to COLOR_VIBRATE,
            "normal" to COLOR_NORMAL
        )

        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onPreRotaryScrollEvent { event ->
                    scope.launch { listState.scrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            if (showModes) {
                if (layout == "2" || layout == "3") {
                    presets.chunked(layout.toInt()).forEach { rowItems ->
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                rowItems.forEach { (action, title, _) ->
                                    val bg = hexToColor(presetColors[action]!!)
                                    Chip(
                                        onClick = { applyPreset(action) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp),
                                        colors = ChipDefaults.chipColors(
                                            backgroundColor = bg,
                                            contentColor = contentColorFor(bg)
                                        ),
                                        label = {
                                            Text(
                                                title,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    presets.forEach { (action, title, desc) ->
                        item {
                            val bg = hexToColor(presetColors[action]!!)
                            Chip(
                                onClick = { applyPreset(action) },
                                modifier = if (layout == "center") Modifier.width(120.dp) else Modifier.fillMaxWidth(),
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = bg,
                                    contentColor = contentColorFor(bg)
                                ),
                                label = {
                                    Column {
                                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (layout != "center") {
                                            Text(desc, style = MaterialTheme.typography.caption2)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (buttons.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.wear_custom_empty),
                        style = MaterialTheme.typography.caption2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            } else {
                val folderNames = (ButtonState.folders + buttons.map { it.folder }).distinct()
                val showHeaders = folderNames.size > 1
                folderNames.forEach { folder ->
                    if (showHeaders) {
                        item {
                            Text(
                                if (folder == DEFAULT_FOLDER) stringResource(R.string.folder_default) else folder,
                                style = MaterialTheme.typography.caption1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    val folderButtons = buttons.filter { it.folder == folder }
                    if (layout == "2" || layout == "3") {
                        folderButtonRows(folderButtons, layout)
                    } else {
                        folderButtonList(folderButtons, layout)
                    }
                }
            }

            item {
                CompactChip(
                    onClick = { switchLanguage() },
                    modifier = Modifier.fillMaxWidth(0.8f),
                    label = {
                        Text(
                            stringResource(
                                R.string.lang_chip,
                                Language.displayName(Language.get(this@MainActivity))
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }

    private fun androidx.wear.compose.foundation.lazy.ScalingLazyListScope.folderButtonRows(
        folderButtons: List<ButtonData>,
        layout: String
    ) {
        folderButtons.chunked(layout.toInt()).forEach { rowItems ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rowItems.forEach { button ->
                        val bg = hexToColor(button.color)
                        val label = if (button.double != null) {
                            button.name + " ×2"
                        } else {
                            button.name
                        }
                        CompactChip(
                            onClick = { onCustomButtonTap(button) },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            colors = ChipDefaults.chipColors(
                                backgroundColor = bg,
                                contentColor = contentColorFor(bg)
                            ),
                            label = {
                                Text(
                                    label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    private fun androidx.wear.compose.foundation.lazy.ScalingLazyListScope.folderButtonList(
        folderButtons: List<ButtonData>,
        layout: String
    ) {
        folderButtons.forEach { button ->
            item {
                val bg = hexToColor(button.color)
                val content = contentColorFor(bg)
                TitleCard(
                    onClick = { onCustomButtonTap(button) },
                    title = {
                        Text(
                            if (button.double != null) "${button.name} ×2" else button.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = if (layout == "center") Modifier.width(130.dp) else Modifier.fillMaxWidth(),
                    backgroundPainter = ColorPainter(bg),
                    contentColor = content,
                    titleColor = content
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(button.single.method, style = MaterialTheme.typography.caption2)
                        if (layout != "center") {
                            Text(
                                button.single.url,
                                style = MaterialTheme.typography.caption2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (button.double != null) {
                                Text(
                                    stringResource(R.string.wear_double),
                                    style = MaterialTheme.typography.caption2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}