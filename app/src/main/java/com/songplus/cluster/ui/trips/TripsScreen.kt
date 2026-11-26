package com.songplus.cluster.ui.trips

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.songplus.cluster.ClusterViewModel
import com.songplus.cluster.R
import com.songplus.cluster.data.TripRecord
import com.songplus.cluster.data.TripSort
import com.songplus.cluster.data.filterSortTrips
import com.songplus.cluster.data.Units
import com.songplus.cluster.ui.ChoiceRow
import com.songplus.cluster.ui.KeyValue
import com.songplus.cluster.ui.SectionTitle
import com.songplus.cluster.ui.SliderRow
import com.songplus.cluster.ui.SwitchRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmtDate(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
private fun fmtDur(sec: Int): String = if (sec >= 3600) "${sec / 3600} h ${(sec % 3600) / 60} min" else "${sec / 60} min ${sec % 60} s"

@Composable
fun TripsScreen(vm: ClusterViewModel, modifier: Modifier = Modifier) {
    val all by vm.trips.collectAsState()
    val settings by vm.settings.collectAsState()
    val active by vm.tripActive.collectAsState()
    val ctx = LocalContext.current
    var sort by remember { mutableStateOf(TripSort.DATE) }
    var asc by remember { mutableStateOf(false) }
    var minKm by remember { mutableStateOf(0f) }
    var showSim by remember { mutableStateOf(true) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val list = remember(all, sort, asc, minKm, showSim) { filterSortTrips(all, minKm, showSim, sort, asc) }
    val selected = list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()
    val du = settings.distUnit
    val exportedOk = stringResource(R.string.export_ok)
    val exportFailed = stringResource(R.string.export_failed)

    var pendingCsv by remember { mutableStateOf(true) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) {
            message = try {
                val text = if (pendingCsv) vm.tripsCsv(list) else vm.tripsJson(list)
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                exportedOk
            } catch (e: Exception) {
                exportFailed + ": " + (e.message ?: "")
            }
        }
    }

    Row(modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(430.dp).fillMaxHeight()) {
            Text(
                stringResource(if (active) R.string.trip_recording else R.string.trip_idle),
                style = MaterialTheme.typography.titleSmall,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.verticalScroll(rememberScrollState()).weight(0.45f)) {
                ChoiceRow(stringResource(R.string.sort_by), TripSort.values().toList(), sort, {
                    stringResource(when (it) {
                        TripSort.DATE -> R.string.sort_date; TripSort.DISTANCE -> R.string.sort_distance; TripSort.DURATION -> R.string.sort_duration
                        TripSort.EFFICIENCY -> R.string.sort_efficiency; TripSort.AVG_SPEED -> R.string.sort_speed; TripSort.ENERGY -> R.string.sort_energy
                    })
                }) { sort = it }
                SwitchRow(stringResource(R.string.sort_ascending), asc) { asc = it }
                SliderRow(stringResource(R.string.filter_min_km), minKm, 0f..50f, "${minKm.toInt()} km") { minKm = it }
                SwitchRow(stringResource(R.string.show_sim_trips), showSim) { showSim = it }
            }
            Text(stringResource(R.string.trips_count, list.size, all.size), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
            LazyColumn(Modifier.weight(0.55f).fillMaxWidth()) {
                items(list, key = { it.id }) { t ->
                    val sel = t.id == selected?.id
                    Column(
                        Modifier.fillMaxWidth().clickable { selectedId = t.id }
                            .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(fmtDate(t.startMs), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                String.format(Locale.US, "%.1f %s", Units.distance(t.distanceKm, du), Units.distanceLabel(du)),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            fmtDur(t.durationSec) + (t.kwhPer100?.let { " · " + String.format(Locale.US, "%.1f %s", Units.consumption(it, du), Units.consumptionLabel(du)) } ?: "") +
                                if (t.simulated) " · " + stringResource(R.string.badge_sim) else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (t.simulated) Color(0xFFFFB020) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            if (selected == null) {
                Text(stringResource(R.string.trips_empty), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 24.dp))
            } else {
                TripDetail(selected, settings.distUnit, settings.capacityKwh)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    OutlinedButton(onClick = { vm.deleteTrip(selected.id) }) { Text(stringResource(R.string.delete)) }
                }
            }
            SectionTitle(stringResource(R.string.export))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pendingCsv = true; exportLauncher.launch("songplus-trips.csv") }, enabled = list.isNotEmpty()) { Text("CSV") }
                OutlinedButton(onClick = { pendingCsv = false; exportLauncher.launch("songplus-trips.json") }, enabled = list.isNotEmpty()) { Text("JSON") }
                OutlinedButton(onClick = { confirmClear = true }, enabled = all.isNotEmpty()) { Text(stringResource(R.string.trips_clear)) }
            }
            if (message != null) Text(message!!, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.trips_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.trips_clear)) },
            text = { Text(stringResource(R.string.trips_clear_confirm)) },
            confirmButton = { TextButton(onClick = { vm.clearTrips(); confirmClear = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { vm.deleteSimulatedTrips(); confirmClear = false }) { Text(stringResource(R.string.trips_clear_sim)) } }
        )
    }
}

@Composable
private fun TripDetail(t: TripRecord, du: com.songplus.cluster.data.DistUnit, capacityKwh: Float) {
    SectionTitle(fmtDate(t.startMs) + if (t.simulated) "  ·  " + stringResource(R.string.badge_sim) else "")
    KeyValue(stringResource(R.string.trip_distance), String.format(Locale.US, "%.2f %s", Units.distance(t.distanceKm, du), Units.distanceLabel(du)))
    KeyValue(stringResource(R.string.trip_duration), fmtDur(t.durationSec))
    KeyValue(stringResource(R.string.trip_moving), fmtDur(t.movingSec))
    KeyValue(stringResource(R.string.trip_avg), String.format(Locale.US, "%.0f km/h", t.avgKmh))
    KeyValue(stringResource(R.string.trip_max), String.format(Locale.US, "%.0f km/h", t.maxKmh))
    KeyValue(
        stringResource(R.string.trip_soc),
        if (t.socStart != null && t.socEnd != null) String.format(Locale.US, "%.0f%% → %.0f%%", t.socStart, t.socEnd) else "--"
    )
    KeyValue(stringResource(R.string.trip_energy), t.energyKwh?.let { String.format(Locale.US, "%.1f kWh", it) } ?: "--")
    KeyValue(
        stringResource(R.string.trip_efficiency),
        t.kwhPer100?.let { String.format(Locale.US, "%.1f %s", Units.consumption(it, du), Units.consumptionLabel(du)) } ?: "--"
    )
    val total = (t.ecoSec + t.normalSec + t.sportSec).coerceAtLeast(1)
    KeyValue(stringResource(R.string.trip_modes), "Eco ${t.ecoSec * 100 / total}% · Normal ${t.normalSec * 100 / total}% · Sport ${t.sportSec * 100 / total}%")
    Text(stringResource(R.string.trip_energy_note, String.format(Locale.US, "%.1f", capacityKwh)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SectionTitle(stringResource(R.string.trip_speed_chart))
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height))
        val sp = t.speeds
        if (sp.size >= 2) {
            val maxV = (sp.maxOrNull() ?: 1).coerceAtLeast(20).toFloat()
            val p = Path()
            sp.forEachIndexed { i, v ->
                val x = size.width * i / (sp.size - 1)
                val y = size.height * (1f - v / maxV)
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, line, style = Stroke(width = 3f))
        }
    }
}
