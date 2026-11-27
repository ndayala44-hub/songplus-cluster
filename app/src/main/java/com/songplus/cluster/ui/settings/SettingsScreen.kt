package com.songplus.cluster.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.songplus.cluster.ClusterViewModel
import com.songplus.cluster.ImportResult
import com.songplus.cluster.R
import com.songplus.cluster.data.AnimLevel
import com.songplus.cluster.data.BatteryDisplay
import com.songplus.cluster.data.BlinkRate
import com.songplus.cluster.data.DataMode
import com.songplus.cluster.data.DistUnit
import com.songplus.cluster.data.DriveMode
import com.songplus.cluster.data.EffectiveSource
import com.songplus.cluster.data.Gear
import com.songplus.cluster.data.Lang
import com.songplus.cluster.data.OrientationPref
import com.songplus.cluster.data.ProbeStatus
import com.songplus.cluster.data.SpeedUnit
import com.songplus.cluster.data.SpeedDisplayMode
import com.songplus.cluster.data.Startup
import com.songplus.cluster.data.TempUnit
import com.songplus.cluster.data.ThemeMode
import com.songplus.cluster.ui.ChoiceRow
import com.songplus.cluster.ui.ColorSwatches
import com.songplus.cluster.ui.KeyValue
import com.songplus.cluster.ui.SectionTitle
import com.songplus.cluster.ui.SliderRow
import com.songplus.cluster.ui.SwitchRow
import com.songplus.cluster.vehicle.LightsInput
import com.songplus.cluster.vehicle.SignalInput

private enum class Cat(val title: Int) {
    GENERAL(R.string.cat_general), APPEARANCE(R.string.cat_appearance), CLUSTER(R.string.cat_cluster),
    VEHICLE(R.string.cat_vehicle), ADVANCED(R.string.cat_advanced)
}

@Composable
fun SettingsScreen(vm: ClusterViewModel, onOpenDesigner: () -> Unit, modifier: Modifier = Modifier, initialCategory: Int = 0) {
    var cat by remember { mutableStateOf(Cat.values().getOrElse(initialCategory) { Cat.GENERAL }) }
    Row(modifier.fillMaxSize()) {
        Column(Modifier.width(240.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(8.dp)) {
            for (c in Cat.values()) {
                Text(
                    stringResource(c.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (c == cat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().clickable { cat = c }
                        .background(if (c == cat) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
            when (cat) {
                Cat.GENERAL -> General(vm)
                Cat.APPEARANCE -> Appearance(vm)
                Cat.CLUSTER -> ClusterSettings(vm, onOpenDesigner)
                Cat.VEHICLE -> Vehicle(vm)
                Cat.ADVANCED -> Advanced(vm)
            }
        }
    }
}

@Composable
private fun General(vm: ClusterViewModel) {
    val s by vm.settings.collectAsState()
    SectionTitle(stringResource(R.string.cat_general))
    ChoiceRow(stringResource(R.string.language), Lang.values().toList(), s.language,
        { stringResource(when (it) { Lang.SYSTEM -> R.string.lang_system; Lang.EN -> R.string.lang_en; Lang.FR -> R.string.lang_fr }) }
    ) { v -> vm.updateSettings { it.copy(language = v) } }
    ChoiceRow(stringResource(R.string.speed_unit), SpeedUnit.values().toList(), s.speedUnit, { if (it == SpeedUnit.KMH) "km/h" else "mph" }) { v -> vm.updateSettings { it.copy(speedUnit = v) } }
    ChoiceRow(stringResource(R.string.dist_unit), DistUnit.values().toList(), s.distUnit, { if (it == DistUnit.KM) "km" else "mi" }) { v -> vm.updateSettings { it.copy(distUnit = v) } }
    ChoiceRow(stringResource(R.string.temp_unit), TempUnit.values().toList(), s.tempUnit, { if (it == TempUnit.C) "°C" else "°F" }) { v -> vm.updateSettings { it.copy(tempUnit = v) } }
    ChoiceRow(stringResource(R.string.startup_screen), Startup.values().toList(), s.startup,
        { stringResource(when (it) { Startup.CLUSTER -> R.string.startup_cluster; Startup.DESIGNER -> R.string.startup_designer; Startup.FULLSCREEN -> R.string.startup_fullscreen }) }
    ) { v -> vm.updateSettings { it.copy(startup = v) } }
    SwitchRow(stringResource(R.string.play_intro), s.playIntro) { v -> vm.updateSettings { it.copy(playIntro = v) } }
    ChoiceRow(stringResource(R.string.orientation), OrientationPref.values().toList(), s.orientation,
        { stringResource(if (it == OrientationPref.LANDSCAPE) R.string.orient_landscape else R.string.orient_auto) }
    ) { v -> vm.updateSettings { it.copy(orientation = v) } }
    SwitchRow(stringResource(R.string.hide_bars), s.hideSystemBars, stringResource(R.string.hide_bars_hint)) { v -> vm.updateSettings { it.copy(hideSystemBars = v) } }
    SwitchRow(stringResource(R.string.keep_on), s.keepScreenOn) { v -> vm.updateSettings { it.copy(keepScreenOn = v) } }
}

@Composable
private fun Appearance(vm: ClusterViewModel) {
    val s by vm.settings.collectAsState()
    SectionTitle(stringResource(R.string.cat_appearance))
    ChoiceRow(stringResource(R.string.theme), ThemeMode.values().toList(), s.themeMode,
        { stringResource(when (it) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark; ThemeMode.AUTO_TIME -> R.string.theme_auto }) }
    ) { v -> vm.updateSettings { it.copy(themeMode = v) } }
    if (s.themeMode == ThemeMode.AUTO_TIME) {
        SliderRow(stringResource(R.string.dark_from), s.autoDarkFromHour.toFloat(), 0f..23f, "%02d:00".format(s.autoDarkFromHour)) { v -> vm.updateSettings { it.copy(autoDarkFromHour = v.toInt()) } }
        SliderRow(stringResource(R.string.dark_to), s.autoDarkToHour.toFloat(), 0f..23f, "%02d:00".format(s.autoDarkToHour)) { v -> vm.updateSettings { it.copy(autoDarkToHour = v.toInt()) } }
    }
    SectionTitle(stringResource(R.string.mode_colours))
    ColorSwatches(stringResource(R.string.mode_eco), s.accentEco) { c -> vm.updateSettings { it.copy(accentEco = c) } }
    ColorSwatches(stringResource(R.string.mode_normal), s.accentNormal) { c -> vm.updateSettings { it.copy(accentNormal = c) } }
    ColorSwatches(stringResource(R.string.mode_sport), s.accentSport, com.songplus.cluster.ui.theme.Brand.SPORT_CHOICES) { c -> vm.updateSettings { it.copy(accentSport = c) } }
    SectionTitle(stringResource(R.string.motion))
    ChoiceRow(stringResource(R.string.animation), AnimLevel.values().toList(), s.animation,
        { stringResource(when (it) { AnimLevel.FULL -> R.string.anim_full; AnimLevel.REDUCED -> R.string.anim_reduced; AnimLevel.OFF -> R.string.anim_off }) },
        hint = stringResource(R.string.anim_hint)
    ) { v -> vm.updateSettings { it.copy(animation = v) } }
    ChoiceRow(stringResource(R.string.blink_rate), BlinkRate.values().toList(), s.blinkRate,
        { stringResource(when (it) { BlinkRate.SLOW -> R.string.blink_slow; BlinkRate.NORMAL -> R.string.blink_normal; BlinkRate.FAST -> R.string.blink_fast }) }
    ) { v -> vm.updateSettings { it.copy(blinkRate = v) } }
    SectionTitle(stringResource(R.string.brightness))
    SwitchRow(stringResource(R.string.brightness_system), s.brightness <= 0f, stringResource(R.string.brightness_hint)) { sys ->
        vm.updateSettings { it.copy(brightness = if (sys) 0f else 0.8f) }
    }
    if (s.brightness > 0f) {
        SliderRow(stringResource(R.string.brightness), s.brightness, 0.1f..1f, "${(s.brightness * 100).toInt()}%") { v -> vm.updateSettings { it.copy(brightness = v) } }
    }
}

@Composable
private fun ClusterSettings(vm: ClusterViewModel, onOpenDesigner: () -> Unit) {
    val s by vm.settings.collectAsState()
    val users by vm.userLayouts.collectAsState()
    SectionTitle(stringResource(R.string.cat_cluster))
    Text(stringResource(R.string.active_layout), style = MaterialTheme.typography.bodyLarge)
    for (l in vm.allLayouts()) {
        Row(Modifier.fillMaxWidth().clickable { vm.activateLayout(l.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.RadioButton(selected = l.id == s.activeLayoutId, onClick = { vm.activateLayout(l.id) })
            Text(l.name + if (users.any { it.id == l.id }) "" else " (" + stringResource(R.string.layout_builtin) + ")")
        }
    }
    Button(onClick = onOpenDesigner, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.open_designer)) }
    ChoiceRow(stringResource(R.string.battery_display), BatteryDisplay.values().toList(), s.batteryDisplay,
        { stringResource(when (it) { BatteryDisplay.PERCENT_RANGE -> R.string.bd_both; BatteryDisplay.PERCENT -> R.string.bd_percent; BatteryDisplay.RANGE -> R.string.bd_range }) }
    ) { v -> vm.updateSettings { it.copy(batteryDisplay = v) } }
    SwitchRow(stringResource(R.string.hazard_border), s.showHazardBanner) { v -> vm.updateSettings { it.copy(showHazardBanner = v) } }
    SwitchRow(stringResource(R.string.show_gear), s.showGear, stringResource(R.string.show_gear_hint)) { v -> vm.updateSettings { it.copy(showGear = v) } }
    SwitchRow(stringResource(R.string.factory_panel), s.factoryPanelOnly, stringResource(R.string.factory_panel_hint)) { v -> vm.updateSettings { it.copy(factoryPanelOnly = v) } }

    SectionTitle(stringResource(R.string.speed_calibration))
    ChoiceRow(stringResource(R.string.speed_display), SpeedDisplayMode.values().toList(), s.speedMode,
        { stringResource(when (it) { SpeedDisplayMode.REAL -> R.string.sd_real; SpeedDisplayMode.PERCENT -> R.string.sd_percent; SpeedDisplayMode.PLUS_KMH -> R.string.sd_plus }) },
        hint = stringResource(R.string.speed_display_hint)
    ) { v -> vm.updateSettings { it.copy(speedMode = v) } }
    if (s.speedMode == SpeedDisplayMode.PERCENT)
        SliderRow(stringResource(R.string.sd_percent_amount), s.speedPercent, 0f..10f, String.format(java.util.Locale.US, "+%.1f%%", s.speedPercent)) { v -> vm.updateSettings { it.copy(speedPercent = Math.round(v * 2f) / 2f) } }
    if (s.speedMode == SpeedDisplayMode.PLUS_KMH)
        SliderRow(stringResource(R.string.sd_plus_amount), s.speedPlusKmh, 0f..10f, String.format(java.util.Locale.US, "+%.0f km/h", s.speedPlusKmh)) { v -> vm.updateSettings { it.copy(speedPlusKmh = Math.round(v).toFloat()) } }
    Text(stringResource(R.string.cluster_projection_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
}

private val BYD_COMMON_PERMISSIONS = listOf(
    "android.permission.BYDAUTO_SPEED_COMMON", "android.permission.BYDAUTO_ENERGY_COMMON",
    "android.permission.BYDAUTO_GEARBOX_COMMON", "android.permission.BYDAUTO_LIGHT_COMMON",
    "android.permission.BYDAUTO_BODYWORK_COMMON", "android.permission.BYDAUTO_DOOR_LOCK_COMMON",
    "android.permission.BYDAUTO_TYRE_COMMON", "android.permission.BYDAUTO_INSTRUMENT_COMMON"
)

@Composable
private fun Vehicle(vm: ClusterViewModel) {
    val s by vm.settings.collectAsState()
    val st by vm.status.collectAsState()
    val sim by vm.simInputs.collectAsState()
    val ctx = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    SectionTitle(stringResource(R.string.connection))
    val (label, color) = when (st.effective) {
        EffectiveSource.REAL -> stringResource(R.string.badge_real) to Color(0xFF90FC9C)
        EffectiveSource.SIMULATION -> stringResource(R.string.badge_sim) to Color(0xFFFFB020)
        EffectiveSource.NONE -> stringResource(R.string.badge_none) to Color(0xFFFFB020)
    }
    KeyValue(stringResource(R.string.conn_state), label, color)
    Text(st.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    KeyValue(stringResource(R.string.conn_byd_classes), stringResource(if (st.bydClassesPresent) R.string.yes else R.string.no))
    KeyValue(stringResource(R.string.conn_rate), String.format(java.util.Locale.US, "%.1f Hz", st.updatesPerSecond))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = { vm.stopVehicle(); vm.startVehicle() }) { Text(stringResource(R.string.refresh_connection)) }
        OutlinedButton(onClick = { permLauncher.launch(BYD_COMMON_PERMISSIONS.toTypedArray()) }) { Text(stringResource(R.string.request_permissions)) }
        OutlinedButton(onClick = {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text(stringResource(R.string.app_permissions)) }
    }

    BydPermissionProbeSection(vm)

    BydComponentScanSection(vm)

    SectionTitle(stringResource(R.string.data_source))
    ChoiceRow(stringResource(R.string.data_mode), DataMode.values().toList(), s.dataMode,
        { stringResource(when (it) { DataMode.AUTO -> R.string.dm_auto; DataMode.SIMULATION -> R.string.dm_sim; DataMode.BYD_ONLY -> R.string.dm_byd; DataMode.OBD2 -> R.string.dm_obd2 }) },
        hint = stringResource(R.string.dm_hint)
    ) { v -> vm.updateSettings { it.copy(dataMode = v) } }
    SliderRow(stringResource(R.string.refresh_rate), s.refreshHz.toFloat(), 1f..30f, "${s.refreshHz} Hz") { v -> vm.updateSettings { it.copy(refreshHz = v.toInt()) } }

    Obd2Section(vm)

    MusicAndCameraSection(vm)

    SectionTitle(stringResource(R.string.trips_and_range))
    SwitchRow(stringResource(R.string.trips_record), s.tripsEnabled, stringResource(R.string.trips_record_hint)) { v -> vm.updateSettings { it.copy(tripsEnabled = v) } }
    SliderRow(stringResource(R.string.battery_capacity), s.capacityKwh, 40f..120f, String.format(java.util.Locale.US, "%.1f kWh", s.capacityKwh)) { v -> vm.updateSettings { it.copy(capacityKwh = Math.round(v * 2f) / 2f) } }
    Text(stringResource(R.string.battery_capacity_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ChoiceRow(stringResource(R.string.range_window), listOf(10, 100, 500), s.rangeWindowKm, { "$it km" }, hint = stringResource(R.string.range_window_hint)) { v -> vm.updateSettings { it.copy(rangeWindowKm = v) } }
    SliderRow(stringResource(R.string.trip_autostop), s.tripAutoStopSec.toFloat(), 30f..1800f, "${s.tripAutoStopSec / 60} min ${s.tripAutoStopSec % 60} s") { v -> vm.updateSettings { it.copy(tripAutoStopSec = (Math.round(v / 30f) * 30).coerceAtLeast(30)) } }
    SliderRow(stringResource(R.string.trip_min_km), s.tripMinKm, 0f..5f, String.format(java.util.Locale.US, "%.1f km", s.tripMinKm)) { v -> vm.updateSettings { it.copy(tripMinKm = Math.round(v * 10f) / 10f) } }
    SwitchRow(stringResource(R.string.record_sim_trips), s.recordSimTrips, stringResource(R.string.record_sim_hint)) { v -> vm.updateSettings { it.copy(recordSimTrips = v) } }

    SectionTitle(stringResource(R.string.diagnostics))
    if (st.devices.isEmpty()) Text(stringResource(R.string.not_probed), style = MaterialTheme.typography.bodySmall)
    if (st.devices.isNotEmpty()) {
        val ok = st.devices.count { it.status == ProbeStatus.OK }
        val sig = st.devices.count { it.status == ProbeStatus.SIGNATURE_RESTRICTED }
        val denied = st.devices.count { it.status == ProbeStatus.PERMISSION_DENIED }
        Text(
            stringResource(R.string.diag_summary, ok, sig, denied, st.devices.size),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        if (sig > 0) Text(stringResource(R.string.diag_summary_sig), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
    }
    for (d in st.devices) {
        val c = when (d.status) {
            ProbeStatus.OK -> Color(0xFF90FC9C)
            ProbeStatus.SIGNATURE_RESTRICTED -> Color(0xFF9B6BFF)
            ProbeStatus.PERMISSION_DENIED -> Color(0xFFFFB020)
            ProbeStatus.ERROR -> Color(0xFFFFB020)
            ProbeStatus.NO_DATA -> Color(0xFF00C6FC)
            else -> Color.Gray
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            KeyValue(d.device, d.status.name.replace('_', ' '), c)
            val perm = when (d.permissionGranted) {
                true -> stringResource(R.string.perm_granted)
                false -> stringResource(R.string.perm_not_granted)
                null -> stringResource(R.string.perm_none_documented)
            }
            Text(d.detail + " · " + perm, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text(stringResource(R.string.compat_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))

    SectionTitle(stringResource(R.string.simulator))
    Text(stringResource(R.string.sim_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SwitchRow(stringResource(R.string.sim_auto_drive), s.simAutoDrive, stringResource(R.string.sim_auto_hint)) { v -> vm.updateSettings { it.copy(simAutoDrive = v) } }
    if (!s.simAutoDrive) {
        SliderRow(stringResource(R.string.sim_speed), sim.targetSpeedKmh, 0f..200f, "${sim.targetSpeedKmh.toInt()} km/h") { v -> vm.updateSim { it.copy(targetSpeedKmh = v) } }
        ChoiceRow(stringResource(R.string.gear), Gear.values().toList(), sim.gear, { it.label }) { v -> vm.updateSim { it.copy(gear = v) } }
        ChoiceRow(stringResource(R.string.signals), SignalInput.values().toList(), sim.signal,
            { stringResource(when (it) { SignalInput.OFF -> R.string.sig_off; SignalInput.LEFT -> R.string.sig_left; SignalInput.RIGHT -> R.string.sig_right; SignalInput.HAZARD -> R.string.sig_hazard }) }
        ) { v -> vm.updateSim { it.copy(signal = v) } }
        SwitchRow(stringResource(R.string.brake), sim.brake) { v -> vm.updateSim { it.copy(brake = v) } }
    }
    ChoiceRow(stringResource(R.string.drive_mode), DriveMode.values().toList(), sim.mode,
        { stringResource(when (it) { DriveMode.ECO -> R.string.mode_eco; DriveMode.NORMAL -> R.string.mode_normal; DriveMode.SPORT -> R.string.mode_sport }) }
    ) { v -> vm.updateSim { it.copy(mode = v) } }
    ChoiceRow(stringResource(R.string.lights), LightsInput.values().toList(), sim.lights,
        { stringResource(when (it) { LightsInput.OFF -> R.string.lights_off; LightsInput.LOW -> R.string.lights_low; LightsInput.HIGH -> R.string.lights_high }) }
    ) { v -> vm.updateSim { it.copy(lights = v) } }
    SwitchRow(stringResource(R.string.belt_fastened), sim.beltFastened) { v -> vm.updateSim { it.copy(beltFastened = v) } }
    SwitchRow(stringResource(R.string.door_fl), sim.doorFL) { v -> vm.updateSim { it.copy(doorFL = v) } }
    SwitchRow(stringResource(R.string.door_fr), sim.doorFR) { v -> vm.updateSim { it.copy(doorFR = v) } }
    SwitchRow(stringResource(R.string.door_rl), sim.doorRL) { v -> vm.updateSim { it.copy(doorRL = v) } }
    SwitchRow(stringResource(R.string.door_rr), sim.doorRR) { v -> vm.updateSim { it.copy(doorRR = v) } }
    SwitchRow(stringResource(R.string.warn_trunk), sim.trunk) { v -> vm.updateSim { it.copy(trunk = v) } }
    SwitchRow(stringResource(R.string.locked), sim.locked) { v -> vm.updateSim { it.copy(locked = v) } }
    SwitchRow(stringResource(R.string.warn_charging), sim.charging, stringResource(R.string.charging_hint)) { v -> vm.updateSim { it.copy(charging = v) } }
    SwitchRow(stringResource(R.string.tyre_low), sim.tyreLow) { v -> vm.updateSim { it.copy(tyreLow = v) } }
    OutlinedButton(onClick = { vm.resetTrip() }) { Text(stringResource(R.string.reset_trip)) }
}

@Composable
private fun BydPermissionProbeSection(vm: ClusterViewModel) {
    var report by remember { mutableStateOf<com.songplus.cluster.vehicle.PermissionProbe.Report?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        report = vm.probeBydPermissions()
    }
    SectionTitle(stringResource(R.string.probe_title))
    val signing = remember { vm.signingInfo() }
    KeyValue(stringResource(R.string.signing_cert), signing.first.take(60))
    Text(stringResource(R.string.signing_sha) + " " + signing.second.take(47), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.signing_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
    Text(stringResource(R.string.probe_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = { report = vm.probeBydPermissions() }, modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.probe_run))
    }
    report?.let { r ->
        Text(r.verdict, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
        for (e in r.entries) {
            val short = e.name.removePrefix("android.permission.BYDAUTO_")
            val grant = when (e.granted) { true -> stringResource(R.string.perm_granted); false -> stringResource(R.string.perm_not_granted); null -> stringResource(R.string.probe_not_defined) }
            val c = when { e.granted == true -> Color(0xFF90FC9C); e.grantable -> Color(0xFF00C6FC); else -> Color(0xFFFFB020) }
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                KeyValue(short, grant, c)
                Text(e.protection, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (r.requestable.isNotEmpty()) {
            Button(onClick = { permLauncher.launch(r.requestable.toTypedArray()) }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.probe_request_grantable))
            }
        }
    }
}

@Composable
private fun BydComponentScanSection(vm: ClusterViewModel) {
    var report by remember { mutableStateOf<com.songplus.cluster.vehicle.BydComponentScan.Report?>(null) }
    val s by vm.settings.collectAsState()
    SectionTitle(stringResource(R.string.scan_title))
    Text(stringResource(R.string.scan_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (s.providerAuthority.isNotBlank()) {
        KeyValue(stringResource(R.string.scan_active_provider), s.providerAuthority, Color(0xFF90FC9C))
        Text("speed=${s.providerSpeedCol.ifBlank { "—" }} · soc=${s.providerBatteryCol.ifBlank { "—" }} · range=${s.providerRangeCol.ifBlank { "—" }} · gear=${s.providerGearCol.ifBlank { "—" }}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { vm.clearProviderSource() }, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.scan_clear_provider)) }
    }
    OutlinedButton(onClick = { report = vm.scanBydComponents() }, modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.scan_run))
    }
    report?.let { r ->
        Text(r.note, style = MaterialTheme.typography.bodyMedium,
            color = if (r.candidates > 0) Color(0xFF90FC9C) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.scan_counts, r.packages, r.comps.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Show the clean candidates first (capped), then note the rest exist.
        val shown = r.comps.filter { it.cleanCandidate }.take(40)
        for (c in shown) {
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                KeyValue("${c.kind.name.lowercase()} · ${c.key}", stringResource(R.string.scan_candidate), Color(0xFF90FC9C))
                Text("${c.pkg}\nguard: ${c.permission ?: "none"} (${c.protection})" + (c.probeResult?.let { "\nread probe: $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.kind == com.songplus.cluster.vehicle.BydComponentScan.Kind.PROVIDER && c.columns.isNotEmpty()) {
                    OutlinedButton(onClick = { vm.useProviderSource(c.key, c.columns) }, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.scan_use_provider))
                    }
                }
            }
        }
        if (r.candidates == 0 && r.comps.isNotEmpty()) {
            Text(stringResource(R.string.scan_none_clean), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Obd2Section(vm: ClusterViewModel) {
    val s by vm.settings.collectAsState()
    val state by vm.obd2State.collectAsState()
    val detail by vm.obd2Detail.collectAsState()
    val readings by vm.obd2Readings.collectAsState()
    var devices by remember { mutableStateOf<List<com.songplus.cluster.vehicle.Obd2Source.Dev>>(emptyList()) }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        devices = vm.obd2Bonded()
    }

    SectionTitle(stringResource(R.string.obd2_title))
    Text(stringResource(R.string.obd2_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SwitchRow(stringResource(R.string.obd2_enable), s.obd2Enabled, stringResource(R.string.obd2_enable_hint)) { v -> vm.updateSettings { it.copy(obd2Enabled = v) } }

    if (s.obd2Name.isNotBlank() || s.obd2Mac.isNotBlank()) {
        KeyValue(stringResource(R.string.obd2_adapter), s.obd2Name.ifBlank { s.obd2Mac })
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = {
            if (vm.obd2HasPermission()) devices = vm.obd2Bonded()
            else btLauncher.launch(arrayOf("android.permission.BLUETOOTH_CONNECT"))
        }) { Text(stringResource(R.string.obd2_scan)) }
        OutlinedButton(onClick = { if (s.obd2Mac.isNotBlank()) vm.obd2Connect(s.obd2Mac) }) { Text(stringResource(R.string.obd2_connect)) }
        OutlinedButton(onClick = { vm.obd2Stop() }) { Text(stringResource(R.string.obd2_disconnect)) }
    }

    for (d in devices) {
        Text(
            "${d.name}  ·  ${d.mac}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (d.mac == s.obd2Mac) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().clickable { vm.updateSettings { it.copy(obd2Mac = d.mac, obd2Name = d.name) } }.padding(vertical = 6.dp)
        )
    }

    KeyValue(stringResource(R.string.obd2_status), state.name)
    if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val spd = readings.speedKmh?.let { "$it km/h" } ?: "--"
    val rpm = readings.rpm?.let { "$it rpm" } ?: "--"
    val cool = readings.coolantC?.let { "$it °C" } ?: "--"
    KeyValue(stringResource(R.string.obd2_live), "$spd   ·   $rpm   ·   $cool")
    Text(stringResource(R.string.obd2_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun MusicAndCameraSection(vm: ClusterViewModel) {
    val s by vm.settings.collectAsState()
    val media by vm.media.collectAsState()
    val ctx = LocalContext.current
    var camGranted by remember { mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camGranted = it }
    val camIds = remember {
        try { (ctx.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager).cameraIdList.joinToString(", ") } catch (t: Throwable) { "" }
    }

    SectionTitle(stringResource(R.string.music_section))
    KeyValue(stringResource(R.string.music_access), stringResource(if (vm.hasMediaAccess()) R.string.yes else R.string.no))
    media?.let { KeyValue(stringResource(R.string.music_now), it.title.ifBlank { it.appLabel } + if (it.artist.isNotBlank()) " – " + it.artist else "") }
    OutlinedButton(onClick = {
        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }) { Text(stringResource(R.string.music_open_access)) }
    Text(stringResource(R.string.music_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    SectionTitle(stringResource(R.string.cam_section))
    SwitchRow(stringResource(R.string.cam_enable), s.reverseCamera, stringResource(R.string.cam_enable_hint)) { v -> vm.updateSettings { it.copy(reverseCamera = v) } }
    ChoiceRow(stringResource(R.string.cam_source), com.songplus.cluster.data.CamSource.values().toList(), s.camSource,
        { stringResource(when (it) { com.songplus.cluster.data.CamSource.AUTO -> R.string.cam_src_auto; com.songplus.cluster.data.CamSource.CAMERA2_ID -> R.string.cam_src_id; com.songplus.cluster.data.CamSource.MJPEG_URL -> R.string.cam_src_url }) }
    ) { v -> vm.updateSettings { it.copy(camSource = v) } }
    if (s.camSource == com.songplus.cluster.data.CamSource.CAMERA2_ID) {
        androidx.compose.material3.OutlinedTextField(
            value = s.camId, onValueChange = { v -> vm.updateSettings { it.copy(camId = v.take(16)) } },
            label = { Text(stringResource(R.string.cam_id_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
    }
    if (s.camSource == com.songplus.cluster.data.CamSource.MJPEG_URL) {
        androidx.compose.material3.OutlinedTextField(
            value = s.camUrl, onValueChange = { v -> vm.updateSettings { it.copy(camUrl = v.trim().take(300)) } },
            label = { Text(stringResource(R.string.cam_url_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
    }
    SwitchRow(stringResource(R.string.cam_mirror), s.camMirror) { v -> vm.updateSettings { it.copy(camMirror = v) } }
    SwitchRow(stringResource(R.string.cam_guides), s.camGuides) { v -> vm.updateSettings { it.copy(camGuides = v) } }
    KeyValue(stringResource(R.string.cam_permission), stringResource(if (camGranted) R.string.perm_granted else R.string.perm_not_granted))
    KeyValue(stringResource(R.string.cam_visible), camIds.ifBlank { stringResource(R.string.cam_none_visible) })
    if (!camGranted) OutlinedButton(onClick = { camLauncher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.cam_grant)) }
    Text(stringResource(R.string.cam_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Advanced(vm: ClusterViewModel) {
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val okImported = stringResource(R.string.import_ok)
    val failImported = stringResource(R.string.import_failed)
    val exported = stringResource(R.string.export_ok)
    val exportFailed = stringResource(R.string.export_failed)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            message = try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray(Charsets.UTF_8)) }
                exported
            } catch (e: Exception) {
                exportFailed + ": " + (e.message ?: "")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            message = try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                when (val r = vm.importJson(text)) {
                    is ImportResult.Ok -> okImported + " (" + r.layouts + ")"
                    is ImportResult.Failed -> failImported + ": " + r.reason
                }
            } catch (e: Exception) {
                failImported + ": " + (e.message ?: "")
            }
        }
    }

    SectionTitle(stringResource(R.string.import_export))
    Text(stringResource(R.string.import_export_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Button(onClick = { exportLauncher.launch("songplus-cluster-config.json") }) { Text(stringResource(R.string.export)) }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text(stringResource(R.string.import_)) }
    }
    if (message != null) Text(message!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))

    SectionTitle(stringResource(R.string.reset))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { confirm = "settings" }) { Text(stringResource(R.string.reset_settings)) }
        OutlinedButton(onClick = { confirm = "layouts" }) { Text(stringResource(R.string.reset_layouts)) }
    }

    SectionTitle(stringResource(R.string.debug))
    SwitchRow(stringResource(R.string.debug_overlay), s.debugOverlay, stringResource(R.string.debug_hint)) { v -> vm.updateSettings { it.copy(debugOverlay = v) } }

    SectionTitle(stringResource(R.string.exit_app))
    OutlinedButton(onClick = { vm.stopVehicle(); (ctx as? android.app.Activity)?.finishAffinity() }) { Text(stringResource(R.string.exit_app)) }
    Text(stringResource(R.string.exit_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    SectionTitle(stringResource(R.string.about))
    val ver = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }
    KeyValue(stringResource(R.string.version), ver)
    KeyValue(stringResource(R.string.package_name), ctx.packageName)
    KeyValue("Android", android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")")
    KeyValue(stringResource(R.string.device), android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)
    Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))

    if (confirm != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.confirm)) },
            text = { Text(stringResource(if (confirm == "settings") R.string.reset_settings_confirm else R.string.reset_layouts_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    if (confirm == "settings") vm.resetSettings() else vm.restoreAllLayouts()
                    confirm = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
