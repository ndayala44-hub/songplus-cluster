package com.songplus.cluster.ui.designer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.songplus.cluster.ClusterViewModel
import com.songplus.cluster.R
import com.songplus.cluster.data.AccentSource
import com.songplus.cluster.data.BgStyle
import com.songplus.cluster.data.ClusterConfig
import com.songplus.cluster.data.FontStyle
import com.songplus.cluster.data.GaugeStyle
import com.songplus.cluster.data.WidgetId
import com.songplus.cluster.data.WidgetSpec
import com.songplus.cluster.ui.ChoiceRow
import com.songplus.cluster.ui.ColorSwatches
import com.songplus.cluster.ui.SectionTitle
import com.songplus.cluster.ui.SliderRow
import com.songplus.cluster.ui.SwitchRow
import com.songplus.cluster.ui.cluster.ClusterCanvas
import com.songplus.cluster.ui.theme.Palettes
import com.songplus.cluster.ui.theme.resolveDark
import androidx.compose.foundation.isSystemInDarkTheme

@Composable
fun widgetName(id: WidgetId): String = stringResource(
    when (id) {
        WidgetId.SPEED -> R.string.wn_speed
        WidgetId.POWER -> R.string.wn_power
        WidgetId.BATTERY -> R.string.wn_battery
        WidgetId.RANGE -> R.string.wn_range
        WidgetId.GEAR -> R.string.wn_gear
        WidgetId.MODE -> R.string.wn_mode
        WidgetId.TELLTALES -> R.string.wn_telltales
        WidgetId.CAR -> R.string.wn_car
        WidgetId.TRIP -> R.string.wn_trip
        WidgetId.TYRES -> R.string.wn_tyres
        WidgetId.CLOCK -> R.string.wn_clock
        WidgetId.WARNINGS -> R.string.wn_warnings
        WidgetId.CONSUMPTION -> R.string.wn_consumption
        WidgetId.PROJECTION -> R.string.wn_projection
    }
)

@Composable
fun DesignerScreen(vm: ClusterViewModel, modifier: Modifier = Modifier) {
    val draft by vm.draft.collectAsState()
    val dirty by vm.dirty.collectAsState()
    val settings by vm.settings.collectAsState()
    val state by vm.vehicle.collectAsState()
    val status by vm.status.collectAsState()
    val users by vm.userLayouts.collectAsState()
    val projection by vm.projection.collectAsState()
    var selected by remember { mutableStateOf(WidgetId.SPEED) }
    var previewDark by remember { mutableStateOf(resolveDark(settings, false)) }
    val sysDark = isSystemInDarkTheme()
    var tab by remember { mutableIntStateOf(0) }

    Row(modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // ---- preview with drag / resize handles ----
        Column(Modifier.weight(1.6f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            val border = if (dirty) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
            ClusterCanvas(
                state, draft, settings, status, previewDark,
                Modifier.fillMaxWidth().aspectRatio(8f / 3f).border(2.dp, border, RoundedCornerShape(10.dp)),
                projection = projection,
                overlay = { w, h, _ ->
                    val density = LocalDensity.current
                    val pxW = with(density) { w.toPx() }
                    val pxH = with(density) { h.toPx() }
                    val draftNow by rememberUpdatedState(draft)
                    for (id in WidgetId.values()) {
                        val sp = draftNow.spec(id)
                        if (!sp.visible) continue
                        val isSel = id == selected
                        Box(
                            Modifier.offset(w * sp.x, h * sp.y).size(w * sp.w, h * sp.h)
                                .border(if (isSel) 2.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.18f))
                                .pointerInput(id, pxW, pxH) {
                                    detectDragGestures(onDragStart = { selected = id }) { change, drag ->
                                        change.consume()
                                        vm.edit { c ->
                                            val s = c.spec(id)
                                            c.withSpec(id, s.copy(x = s.x + drag.x / pxW, y = s.y + drag.y / pxH))
                                        }
                                    }
                                }
                        ) {
                            if (isSel) {
                                Box(
                                    Modifier.align(Alignment.BottomEnd).size(26.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 8.dp))
                                        .pointerInput(id, pxW, pxH) {
                                            detectDragGestures { change, drag ->
                                                change.consume()
                                                vm.edit { c ->
                                                    val s = c.spec(id)
                                                    c.withSpec(id, s.copy(w = s.w + drag.x / pxW, h = s.h + drag.y / pxH))
                                                }
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.designer_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { previewDark = !previewDark }) {
                    Text(stringResource(if (previewDark) R.string.preview_light else R.string.preview_dark))
                }
            }
            // preview with any drive mode is covered by the simulator; keep palette note short
            Text(
                stringResource(R.string.designer_preview_note, draft.name),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // ---- side panel ----
        Column(Modifier.width(380.dp).fillMaxHeight()) {
            TabRow(selectedTabIndex = tab) {
                listOf(R.string.tab_presets, R.string.tab_widgets, R.string.tab_style).forEachIndexed { i, r ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(r)) })
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                when (tab) {
                    0 -> PresetsTab(vm, draft, dirty, users, settings.activeLayoutId)
                    1 -> WidgetsTab(vm, draft, selected) { selected = it }
                    else -> StyleTab(vm, draft)
                }
            }
        }
    }
}

@Composable
private fun PresetsTab(vm: ClusterViewModel, draft: ClusterConfig, dirty: Boolean, users: List<ClusterConfig>, activeId: String) {
    var dialog by remember { mutableStateOf<String?>(null) }   // "saveas" | "rename"
    var confirmDelete by remember { mutableStateOf(false) }
    val all = vm.allLayouts()
    SectionTitle(stringResource(R.string.layouts))
    for (l in all) {
        val isDraft = l.id == draft.id
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp)
                .background(if (isDraft) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(l.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(if (l.builtin) R.string.layout_builtin else R.string.layout_user) +
                        if (l.id == activeId) " · " + stringResource(R.string.layout_active) else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { vm.loadLayout(l.id) }) { Text(stringResource(R.string.edit)) }
            TextButton(onClick = { vm.activateLayout(l.id) }, enabled = l.id != activeId) { Text(stringResource(R.string.use)) }
        }
    }
    SectionTitle(stringResource(R.string.current_layout, draft.name))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.saveDraft() }, enabled = dirty || draft.builtin) { Text(stringResource(R.string.save)) }
        OutlinedButton(onClick = { dialog = "saveas" }) { Text(stringResource(R.string.save_as)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = { vm.duplicateLayout(draft.id) }) { Text(stringResource(R.string.duplicate)) }
        OutlinedButton(onClick = { dialog = "rename" }, enabled = !draft.builtin) { Text(stringResource(R.string.rename)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = { confirmDelete = true }, enabled = !draft.builtin) { Text(stringResource(R.string.delete)) }
        OutlinedButton(onClick = { vm.restoreDefault() }) { Text(stringResource(R.string.restore_default)) }
    }
    if (draft.builtin) Text(stringResource(R.string.builtin_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))

    if (dialog != null) {
        var name by remember(dialog) { mutableStateOf(if (dialog == "rename") draft.name else draft.name + " 2") }
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(if (dialog == "rename") R.string.rename else R.string.save_as)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.layout_name)) }) },
            confirmButton = {
                TextButton(onClick = {
                    if (dialog == "rename") vm.renameLayout(draft.id, name) else vm.saveDraftAs(name)
                    dialog = null
                }, enabled = name.isNotBlank()) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_confirm, draft.name)) },
            confirmButton = { TextButton(onClick = { vm.deleteLayout(draft.id); confirmDelete = false }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun WidgetsTab(vm: ClusterViewModel, draft: ClusterConfig, selected: WidgetId, onSelect: (WidgetId) -> Unit) {
    SectionTitle(stringResource(R.string.widgets))
    for (id in WidgetId.values()) {
        val sp = draft.spec(id)
        Row(
            Modifier.fillMaxWidth()
                .background(if (id == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = sp.visible, onCheckedChange = { v -> vm.edit { c -> c.withSpec(id, c.spec(id).copy(visible = v)) } })
            TextButton(onClick = { onSelect(id) }, modifier = Modifier.weight(1f)) {
                Text(widgetName(id), modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    val s = draft.spec(selected)
    SectionTitle(widgetName(selected))
    SliderRow(stringResource(R.string.pos_x), s.x, 0f..1f, pct(s.x)) { v -> vm.edit { c -> c.withSpec(selected, c.spec(selected).copy(x = v)) } }
    SliderRow(stringResource(R.string.pos_y), s.y, 0f..1f, pct(s.y)) { v -> vm.edit { c -> c.withSpec(selected, c.spec(selected).copy(y = v)) } }
    SliderRow(stringResource(R.string.size_w), s.w, WidgetSpec.MIN_SIZE..1f, pct(s.w)) { v -> vm.edit { c -> c.withSpec(selected, c.spec(selected).copy(w = v)) } }
    SliderRow(stringResource(R.string.size_h), s.h, WidgetSpec.MIN_SIZE..1f, pct(s.h)) { v -> vm.edit { c -> c.withSpec(selected, c.spec(selected).copy(h = v)) } }
}

private fun pct(f: Float) = "${(f * 100).toInt()}%"

@Composable
private fun StyleTab(vm: ClusterViewModel, draft: ClusterConfig) {
    SectionTitle(stringResource(R.string.style))
    ChoiceRow(
        stringResource(R.string.gauge_style), GaugeStyle.values().toList(), draft.gauge,
        { stringResource(when (it) { GaugeStyle.RING -> R.string.gauge_ring; GaugeStyle.BAR -> R.string.gauge_bar; GaugeStyle.DIGITAL -> R.string.gauge_digital; GaugeStyle.DIAL -> R.string.gauge_dial }) }
    ) { v -> vm.edit { it.copy(gauge = v) } }
    ChoiceRow(
        stringResource(R.string.font_style), FontStyle.values().toList(), draft.font,
        { stringResource(when (it) { FontStyle.SANS -> R.string.font_sans; FontStyle.LIGHT -> R.string.font_light; FontStyle.MONO -> R.string.font_mono }) }
    ) { v -> vm.edit { it.copy(font = v) } }
    ChoiceRow(
        stringResource(R.string.background), BgStyle.values().toList(), draft.background,
        { stringResource(when (it) { BgStyle.SOLID -> R.string.bg_solid; BgStyle.GRADIENT -> R.string.bg_gradient; BgStyle.GRID -> R.string.bg_grid }) }
    ) { v -> vm.edit { it.copy(background = v) } }
    SwitchRow(stringResource(R.string.glow), draft.glow) { v -> vm.edit { it.copy(glow = v) } }
    SliderRow(stringResource(R.string.digit_scale), draft.digitScale, 0.7f..1.3f, "${(draft.digitScale * 100).toInt()}%") { v -> vm.edit { it.copy(digitScale = v) } }

    SectionTitle(stringResource(R.string.palettes))
    ChoiceRow(
        stringResource(R.string.palette_dark), Palettes.darkSet.indices.toList(), draft.darkPalette, { Palettes.darkSet[it].name }
    ) { v -> vm.edit { it.copy(darkPalette = v) } }
    ChoiceRow(
        stringResource(R.string.palette_light), Palettes.lightSet.indices.toList(), draft.lightPalette, { Palettes.lightSet[it].name }
    ) { v -> vm.edit { it.copy(lightPalette = v) } }

    SectionTitle(stringResource(R.string.accent))
    ChoiceRow(
        stringResource(R.string.accent_source), AccentSource.values().toList(), draft.accentSource,
        { stringResource(if (it == AccentSource.MODE) R.string.accent_by_mode else R.string.accent_fixed) },
        hint = stringResource(R.string.accent_hint)
    ) { v -> vm.edit { it.copy(accentSource = v) } }
    if (draft.accentSource == AccentSource.FIXED) {
        ColorSwatches(stringResource(R.string.accent_colour), draft.fixedAccent) { c -> vm.edit { it.copy(fixedAccent = c) } }
    }
}
