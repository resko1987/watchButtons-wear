package com.watchbuttons.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DoNotDisturbOnTotalSilence
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DOUBLE_CLICK_MS = 350L

private val PALETTE = listOf(
    "#5B8DEF", "#7E57C2", "#E53935", "#FB8C00",
    "#43A047", "#00897B", "#D81B60", "#546E7A"
)

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

private data class Entry(
    val title: String,
    val subtitle: String,
    val color: String,
    val icon: ImageVector?,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null
)

class MainActivity : ComponentActivity() {

    private val store by lazy { ButtonStore(this) }
    private val controller by lazy { SoundController(this) }

    private var buttons by mutableStateOf(listOf<ButtonData>())
    private var layout by mutableStateOf("1")
    private var showModes by mutableStateOf(true)
    private var showResponse by mutableStateOf(true)
    private var permissionGranted by mutableStateOf(false)

    private var pendingClick: Job? = null
    private var pendingButtonId: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Language.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buttons = store.load()
        layout = store.getLayout()
        showModes = store.getShowModes()
        showResponse = store.getShowResponse()
        permissionGranted = controller.isPolicyAccessGranted()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionGranted = controller.isPolicyAccessGranted()
    }

    private fun applyPreset(action: String) {
        val success = when (action) {
            "dnd" -> controller.doNotDisturb()
            "vibrate" -> controller.vibrateOnly()
            "normal" -> controller.normalMode()
            else -> false
        }
        sendCommand(this, action)
        val label = if (success) {
            when (action) {
                "dnd" -> getString(R.string.toast_dnd)
                "vibrate" -> getString(R.string.toast_vibrate)
                else -> getString(R.string.toast_normal)
            }
        } else {
            getString(R.string.toast_mode_failed)
        }
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
    }

    private fun onCustomButtonTap(button: ButtonData) {
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

    private fun cancelPendingClick() {
        pendingClick?.cancel()
        pendingClick = null
        pendingButtonId = null
    }

    private fun executeAction(button: ButtonData, action: ButtonAction) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { HttpExecutor.execute(action) }
            val text = formatResult(result)
            val duration = if (result.error == null && showResponse && result.body.isNotBlank()) {
                Toast.LENGTH_LONG
            } else {
                Toast.LENGTH_SHORT
            }
            Toast.makeText(this@MainActivity, text, duration).show()
        }
    }

    private fun formatResult(result: HttpResult): String {
        return when {
            result.error != null -> getString(R.string.toast_error, result.error)
            result.code in 200..399 -> {
                val base = getString(R.string.toast_done)
                if (showResponse && result.body.isNotBlank()) {
                    "$base\n${result.body.take(300)}"
                } else {
                    base
                }
            }
            else -> getString(R.string.toast_http_error, result.code)
        }
    }

    private fun changeLayout(value: String) {
        layout = value
        store.setLayout(value)
    }

    private fun changeLanguage(tag: String) {
        if (Language.get(this) != tag) {
            Language.set(this, tag)
            recreate()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    fun AppScreen() {
        var showAddDialog by remember { mutableStateOf(false) }
        var showSettingsDialog by remember { mutableStateOf(false) }
        var editCandidate by remember { mutableStateOf<ButtonData?>(null) }

        val presets = listOf(
            Entry(
                title = stringResource(R.string.mode_dnd),
                subtitle = stringResource(R.string.mode_dnd_desc),
                color = COLOR_DND,
                icon = Icons.Default.DoNotDisturbOnTotalSilence,
                onClick = { applyPreset("dnd") }
            ),
            Entry(
                title = stringResource(R.string.mode_vibrate),
                subtitle = stringResource(R.string.mode_vibrate_desc),
                color = COLOR_VIBRATE,
                icon = Icons.Default.Vibration,
                onClick = { applyPreset("vibrate") }
            ),
            Entry(
                title = stringResource(R.string.mode_normal),
                subtitle = stringResource(R.string.mode_normal_desc),
                color = COLOR_NORMAL,
                icon = Icons.Default.Notifications,
                onClick = { applyPreset("normal") }
            )
        )
        val custom = buttons.map {
            Entry(
                title = it.name,
                subtitle = actionSubtitle(it),
                color = it.color,
                icon = null,
                onClick = { onCustomButtonTap(it) },
                onLongClick = {
                    cancelPendingClick()
                    editCandidate = it
                }
            )
        }

        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.dialog_new))
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.app_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = stringResource(R.string.settings)
                            )
                        }
                    }
                }

                if (!permissionGranted) {
                    item { PermissionBanner() }
                }

                item { LayoutSelector() }

                if (showModes) {
                    item {
                        Text(
                            stringResource(R.string.section_modes),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    renderEntries(presets, false)
                }

                item {
                    Text(
                        stringResource(R.string.section_custom),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                if (custom.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.custom_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    item {
                        Text(
                            stringResource(R.string.custom_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    renderEntries(custom, true)
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }

        if (showAddDialog || editCandidate != null) {
            ButtonDialog(
                editing = editCandidate,
                onDismiss = {
                    showAddDialog = false
                    editCandidate = null
                },
                onSave = { name, color, single, double ->
                    val editing = editCandidate
                    buttons = if (editing == null) {
                        store.add(name, color, single, double)
                    } else {
                        store.update(editing.id, name, color, single, double)
                    }
                    showAddDialog = false
                    editCandidate = null
                },
                onDelete = { button ->
                    buttons = store.remove(button.id)
                    editCandidate = null
                    Toast.makeText(
                        this,
                        getString(R.string.toast_removed, button.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        if (showSettingsDialog) {
            SettingsDialog(
                currentLanguage = Language.get(this),
                showModes = showModes,
                showResponse = showResponse,
                onDismiss = { showSettingsDialog = false },
                onShowModesChange = { value ->
                    showModes = value
                    store.setShowModes(value)
                },
                onShowResponseChange = { value ->
                    showResponse = value
                    store.setShowResponse(value)
                },
                onLanguageSelect = { tag ->
                    showSettingsDialog = false
                    changeLanguage(tag)
                }
            )
        }
    }

    private fun actionSubtitle(button: ButtonData): String {
        val single = "${button.single.method}  ${button.single.url}"
        return if (button.double != null) "$single  ·  ×2" else single
    }

    private fun androidx.compose.foundation.lazy.LazyListScope.renderEntries(
        entries: List<Entry>,
        longClickable: Boolean
    ) {
        when (layout) {
            "2", "3" -> {
                entries.chunked(layout.toInt()).forEach { rowItems ->
                    item(entries.first().title + rowItems.first().title) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowItems.forEach { entry ->
                                EntryCard(
                                    entry = entry,
                                    compact = true,
                                    longClickable = longClickable,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
            "center" -> {
                entries.forEach { entry ->
                    item(entry.title) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            EntryCard(
                                entry = entry,
                                compact = false,
                                longClickable = longClickable,
                                modifier = Modifier.width(220.dp)
                            )
                        }
                    }
                }
            }
            else -> {
                entries.forEach { entry ->
                    item(entry.title) {
                        EntryCard(
                            entry = entry,
                            compact = false,
                            longClickable = longClickable,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun EntryCard(
        entry: Entry,
        compact: Boolean,
        longClickable: Boolean,
        modifier: Modifier = Modifier
    ) {
        val bg = hexToColor(entry.color)
        val content = contentColorFor(bg)
        Card(
            modifier = modifier.combinedClickable(
                onClick = entry.onClick,
                onLongClick = if (longClickable) entry.onLongClick else null
            ),
            colors = CardDefaults.cardColors(containerColor = bg)
        ) {
            if (compact) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        entry.icon?.let {
                            Icon(
                                it,
                                contentDescription = null,
                                tint = content,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            entry.title,
                            color = content,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (entry.subtitle.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            entry.subtitle,
                            color = content.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    entry.icon?.let {
                        Icon(
                            it,
                            contentDescription = null,
                            tint = content,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Column {
                        Text(
                            entry.title,
                            color = content,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            entry.subtitle,
                            color = content.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun LayoutSelector() {
        val options = listOf(
            "1" to stringResource(R.string.layout_1),
            "2" to stringResource(R.string.layout_2),
            "3" to stringResource(R.string.layout_3),
            "center" to stringResource(R.string.layout_center)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (id, label) ->
                FilterChip(
                    selected = layout == id,
                    onClick = { changeLayout(id) },
                    label = { Text(label) }
                )
            }
        }
    }

    @Composable
    private fun PermissionBanner() {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.banner_permission_text),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = { controller.requestPolicyAccess() }) {
                    Text(stringResource(R.string.banner_permission_button))
                }
            }
        }
    }

    @Composable
    private fun SettingsDialog(
        currentLanguage: String,
        showModes: Boolean,
        showResponse: Boolean,
        onDismiss: () -> Unit,
        onShowModesChange: (Boolean) -> Unit,
        onShowResponseChange: (Boolean) -> Unit,
        onLanguageSelect: (String) -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.settings_show_modes),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = showModes,
                            onCheckedChange = onShowModesChange
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.settings_show_response),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = showResponse,
                            onCheckedChange = onShowResponseChange
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(
                        stringResource(R.string.language),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Language.options.forEach { (tag, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLanguageSelect(tag) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentLanguage == tag,
                                onClick = { onLanguageSelect(tag) }
                            )
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_done)) }
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ButtonDialog(
        editing: ButtonData?,
        onDismiss: () -> Unit,
        onSave: (String, String, ButtonAction, ButtonAction?) -> Unit,
        onDelete: ((ButtonData) -> Unit)?
    ) {
        var name by remember { mutableStateOf(editing?.name ?: "") }
        var color by remember { mutableStateOf(editing?.color ?: PALETTE.first()) }
        var sUrl by remember { mutableStateOf(editing?.single?.url ?: "") }
        var sMethod by remember { mutableStateOf(editing?.single?.method ?: "GET") }
        var sBody by remember { mutableStateOf(editing?.single?.body ?: "") }
        var hasDouble by remember { mutableStateOf(editing?.double != null) }
        var dUrl by remember { mutableStateOf(editing?.double?.url ?: "") }
        var dMethod by remember { mutableStateOf(editing?.double?.method ?: "GET") }
        var dBody by remember { mutableStateOf(editing?.double?.body ?: "") }
        var confirmDelete by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    if (editing == null) stringResource(R.string.dialog_new)
                    else stringResource(R.string.dialog_edit)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.field_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.label_color),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PALETTE.forEach { hex ->
                            val selected = color == hex
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(hexToColor(hex), CircleShape)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { color = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Box(
                                        Modifier
                                            .size(12.dp)
                                            .background(contentColorFor(hexToColor(hex)), CircleShape)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.label_single),
                        style = MaterialTheme.typography.labelLarge
                    )
                    ActionFields(
                        url = sUrl,
                        onUrl = { sUrl = it },
                        method = sMethod,
                        onMethod = { sMethod = it },
                        body = sBody,
                        onBody = { sBody = it }
                    )

                    FilterChip(
                        selected = hasDouble,
                        onClick = { hasDouble = !hasDouble },
                        label = { Text(stringResource(R.string.label_double)) }
                    )
                    if (hasDouble) {
                        ActionFields(
                            url = dUrl,
                            onUrl = { dUrl = it },
                            method = dMethod,
                            onMethod = { dMethod = it },
                            body = dBody,
                            onBody = { dBody = it }
                        )
                    }

                    if (editing != null && onDelete != null) {
                        Spacer(Modifier.height(4.dp))
                        if (!confirmDelete) {
                            TextButton(onClick = { confirmDelete = true }) {
                                Text(
                                    stringResource(R.string.btn_delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.confirm_delete, editing.name),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { confirmDelete = false }) {
                                    Text(stringResource(R.string.btn_cancel))
                                }
                                TextButton(onClick = { onDelete(editing) }) {
                                    Text(
                                        stringResource(R.string.btn_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val single = ButtonAction(sUrl.trim(), sMethod, sBody.trim())
                        val double = if (hasDouble && dUrl.isNotBlank()) {
                            ButtonAction(dUrl.trim(), dMethod, dBody.trim())
                        } else {
                            null
                        }
                        onSave(name.trim(), color, single, double)
                    },
                    enabled = name.isNotBlank() && sUrl.isNotBlank() && (!hasDouble || dUrl.isNotBlank())
                ) {
                    Text(
                        if (editing == null) stringResource(R.string.btn_save)
                        else stringResource(R.string.btn_done)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ActionFields(
        url: String,
        onUrl: (String) -> Unit,
        method: String,
        onMethod: (String) -> Unit,
        body: String,
        onBody: (String) -> Unit
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrl,
                label = { Text(stringResource(R.string.field_url)) },
                placeholder = { Text("http://192.168.1.10/api/light") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = method == "GET",
                    onClick = { onMethod("GET") },
                    label = { Text("GET") }
                )
                FilterChip(
                    selected = method == "POST",
                    onClick = { onMethod("POST") },
                    label = { Text("POST") }
                )
            }
            if (method == "POST") {
                OutlinedTextField(
                    value = body,
                    onValueChange = onBody,
                    label = { Text(stringResource(R.string.field_body)) },
                    placeholder = { Text("{\"state\": \"on\"}") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}