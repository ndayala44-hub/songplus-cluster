package com.songplus.cluster.ui.cluster

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.songplus.cluster.R
import com.songplus.cluster.data.BatteryDisplay
import com.songplus.cluster.data.DriveMode
import com.songplus.cluster.data.GaugeStyle
import com.songplus.cluster.data.Gear
import com.songplus.cluster.data.Sig
import com.songplus.cluster.data.TyreState
import com.songplus.cluster.data.Units
import com.songplus.cluster.data.WidgetId
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val MAX_KMH = 200f

/** Draws one widget by id. Unavailable values show "--", never a made-up number. */
@Composable
fun ClusterWidget(id: WidgetId, c: WCtx, modifier: Modifier = Modifier) {
    Box(modifier) {
        when (id) {
            WidgetId.AURORA -> AuroraWidget(c)
            WidgetId.MUSIC -> MusicWidget(c)
            WidgetId.SPEED -> SpeedWidget(c)
            WidgetId.POWER -> PowerWidget(c)
            WidgetId.BATTERY -> BatteryWidget(c)
            WidgetId.RANGE -> RangeWidget(c)
            WidgetId.GEAR -> GearWidget(c)
            WidgetId.MODE -> ModeWidget(c)
            WidgetId.TELLTALES -> TelltaleWidget(c)
            WidgetId.CAR -> CarWidget(c)
            WidgetId.TRIP -> TripWidget(c)
            WidgetId.TYRES -> TyresWidget(c)
            WidgetId.CLOCK -> ClockWidget(c)
            WidgetId.WARNINGS -> WarningsWidget(c)
            WidgetId.CONSUMPTION -> ConsumptionWidget(c)
            WidgetId.PROJECTION -> ProjectionWidget(c)
        }
    }
}

private fun DrawScope.ringArc(frac: Float, color: Color, track: Color, width: Float, glow: Boolean) {
    val d = size.minDimension - 2f * width
    val sz = Size(d, d)
    val off = Offset((size.width - d) / 2f, (size.height - d) / 2f)
    val start = 150f
    val sweep = 240f
    drawArc(track, start, sweep, false, off, sz, style = Stroke(width, cap = StrokeCap.Round))
    val f = frac.coerceIn(0f, 1f)
    if (f > 0.002f) {
        if (glow) drawArc(color.copy(alpha = 0.25f), start, sweep * f, false, off, sz, style = Stroke(width * 2.2f, cap = StrokeCap.Round))
        drawArc(color, start, sweep * f, false, off, sz, style = Stroke(width, cap = StrokeCap.Round))
    }
}

private fun DrawScope.barFill(frac: Float, color: Color, track: Color, glow: Boolean) {
    val h = size.height
    val r = h / 2f
    drawRoundRect(track, Offset.Zero, Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(r, r))
    val w = size.width * frac.coerceIn(0f, 1f)
    if (w > 1f) {
        if (glow) drawRoundRect(color.copy(alpha = 0.25f), Offset(0f, -h * 0.4f), Size(w, h * 1.8f), androidx.compose.ui.geometry.CornerRadius(r * 2f, r * 2f))
        drawRoundRect(color, Offset.Zero, Size(w, h), androidx.compose.ui.geometry.CornerRadius(r, r))
    }
}

@Composable
private fun SpeedWidget(c: WCtx) {
    val su = c.settings.speedUnit
    val sp = c.state.speedKmh
    val shownKmh = if (sp.available) Units.displaySpeedKmh(sp.value, c.settings.speedMode, c.settings.speedPercent, c.settings.speedPlusKmh) else 0f
    val frac = smooth(if (sp.available) shownKmh / MAX_KMH else 0f, c.settings.animation)
    val shown = if (sp.available) Units.speed(shownKmh, su).toString() else "--"
    val ringW = c.scale.px(14f)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (c.cfg.gauge) {
            GaugeStyle.DIAL -> {
                val dialMax = Units.speedMax(su)
                val dialFrac = smooth(if (sp.available) Units.speed(shownKmh, su) / dialMax else 0f, c.settings.animation)
                DialGauge(dialFrac, dialMax, c, shown, Units.speedLabel(su), sp.available, Modifier.weight(1f).fillMaxWidth())
            }
            GaugeStyle.RING -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { ringArc(frac, c.accent, c.colors.line, ringW, c.cfg.glow) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(shown, style = c.text(150f), textAlign = TextAlign.Center)
                    Text(Units.speedLabel(su), style = c.label(26f))
                }
            }
            GaugeStyle.BAR, GaugeStyle.DIGITAL -> Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(shown, style = c.text(190f), textAlign = TextAlign.Center)
                Text(Units.speedLabel(su), style = c.label(28f))
                if (c.cfg.gauge == GaugeStyle.BAR) {
                    Canvas(Modifier.padding(top = c.scale.dp(14f)).fillMaxWidth().height(c.scale.dp(10f))) {
                        barFill(frac, c.accent, c.colors.line, c.cfg.glow)
                    }
                }
            }
        }
    }
}

/** Analogue needle gauge: 240-degree sweep, a major tick every 20 units, numbers, needle and a digital readout. */
@Composable
private fun DialGauge(frac: Float, maxVal: Float, c: WCtx, valueText: String, unit: String, available: Boolean, modifier: Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val tickStyle = c.label(18f, c.colors.textDim)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val d = size.minDimension
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = d / 2f - c.scale.px(10f)
            val start = 150f
            val sweep = 240f
            drawArc(c.colors.line, start, sweep, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r), style = Stroke(c.scale.px(4f), cap = StrokeCap.Round))
            val f = frac.coerceIn(0f, 1f)
            if (f > 0.003f) drawArc(c.accent, start, sweep * f, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r), style = Stroke(c.scale.px(8f), cap = StrokeCap.Round))
            var v = 0f
            while (v <= maxVal + 0.01f) {
                val a = Math.toRadians((start + sweep * (v / maxVal)).toDouble())
                val major = true
                val r1 = r - c.scale.px(if (major) 22f else 12f)
                val x0 = cx + (r1 * Math.cos(a)).toFloat(); val y0 = cy + (r1 * Math.sin(a)).toFloat()
                val x1 = cx + ((r - c.scale.px(6f)) * Math.cos(a)).toFloat(); val y1 = cy + ((r - c.scale.px(6f)) * Math.sin(a)).toFloat()
                drawLine(c.colors.textDim, Offset(x0, y0), Offset(x1, y1), strokeWidth = c.scale.px(3f))
                val label = v.toInt().toString()
                val lr = r1 - c.scale.px(20f)
                val m = measurer.measure(label, tickStyle)
                drawText(m, topLeft = Offset(cx + (lr * Math.cos(a)).toFloat() - m.size.width / 2f, cy + (lr * Math.sin(a)).toFloat() - m.size.height / 2f))
                v += 20f
            }
            // needle
            val na = Math.toRadians((start + sweep * f).toDouble())
            val nl = r - c.scale.px(30f)
            val needle = if (available) c.accent else c.colors.textDim.copy(alpha = 0.3f)
            drawLine(needle, Offset(cx, cy), Offset(cx + (nl * Math.cos(na)).toFloat(), cy + (nl * Math.sin(na)).toFloat()), strokeWidth = c.scale.px(7f), cap = StrokeCap.Round)
            drawCircle(c.colors.panel, c.scale.px(16f), Offset(cx, cy))
            drawCircle(needle, c.scale.px(9f), Offset(cx, cy))
        }
        Column(Modifier.padding(top = c.scale.dp(210f)), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(valueText, style = c.text(72f))
            Text(unit, style = c.label(20f))
        }
    }
}

@Composable
private fun BatteryWidget(c: WCtx) {
    val soc = c.state.socPct
    val range = c.state.rangeKm
    val frac = smooth(if (soc.available) soc.value / 100f else 0f, c.settings.animation)
    val low = soc.available && soc.value <= 20f
    val color = if (low) c.colors.warn else if (c.state.charging.value) c.colors.ok else c.accent
    val socTxt = if (soc.available) "${soc.value.toInt()}" else "--"
    val rangeTxt = if (range.available) "${Units.distance(range.value, c.settings.distUnit).toInt()} ${Units.distanceLabel(c.settings.distUnit)}" else "-- ${Units.distanceLabel(c.settings.distUnit)}"
    val bd = c.settings.batteryDisplay
    val ringW = c.scale.px(14f)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (c.cfg.gauge == GaugeStyle.RING || c.cfg.gauge == GaugeStyle.DIAL) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { ringArc(frac, color, c.colors.line, ringW, c.cfg.glow) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (bd != BatteryDisplay.RANGE) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(socTxt, style = c.text(110f))
                            Text("%", style = c.label(34f), modifier = Modifier.padding(bottom = c.scale.dp(16f)))
                        }
                    }
                    if (bd != BatteryDisplay.PERCENT) Text(rangeTxt, style = c.text(if (bd == BatteryDisplay.RANGE) 80f else 40f))
                    Text(stringResource(R.string.w_battery), style = c.label(22f))
                }
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(socTxt, style = c.text(80f))
                    Text("%", style = c.label(30f), modifier = Modifier.padding(bottom = c.scale.dp(10f)))
                }
                Canvas(Modifier.padding(vertical = c.scale.dp(10f)).fillMaxWidth().height(c.scale.dp(12f))) {
                    barFill(frac, color, c.colors.line, c.cfg.glow)
                }
                if (bd != BatteryDisplay.PERCENT) Text(rangeTxt, style = c.text(36f))
            }
        }
    }
}

@Composable
private fun RangeWidget(c: WCtx) {
    val r = c.state.rangeKm
    val t = if (r.available) "${Units.distance(r.value, c.settings.distUnit).toInt()}" else "--"
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(t, style = c.text(72f))
            Text(" " + Units.distanceLabel(c.settings.distUnit), style = c.label(26f), modifier = Modifier.padding(bottom = c.scale.dp(10f)))
        }
        Text(stringResource(R.string.w_range), style = c.label(22f))
    }
}

@Composable
private fun ConsumptionWidget(c: WCtx) {
    val v = c.state.avgKwhPer100
    val t = if (v.available) String.format(Locale.US, "%.1f", Units.consumption(v.value, c.settings.distUnit)) else "--"
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(t, style = c.text(54f))
        Text(Units.consumptionLabel(c.settings.distUnit), style = c.label(20f))
    }
}

@Composable
private fun PowerWidget(c: WCtx) {
    val p = c.state.powerNorm
    val v = smooth(if (p.available) p.value else 0f, c.settings.animation)
    val kw = c.state.powerKw
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(c.scale.dp(12f)).alpha(if (p.available) 1f else 0.3f)) {
            val h = size.height
            val mid = size.width / 2f
            drawRoundRect(c.colors.line, Offset.Zero, Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f))
            val w = abs(v) * mid
            val col = if (v >= 0f) c.accent else c.colors.ok
            if (w > 1f) drawRoundRect(col, Offset(if (v >= 0f) mid else mid - w, 0f), Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f))
            drawRect(c.colors.text.copy(alpha = 0.6f), Offset(mid - 1f, -h * 0.2f), Size(2f, h * 1.4f))
        }
        val label = when {
            !p.available -> "--"
            kw.available -> "${kw.value.toInt()} kW"
            v < -0.03f -> stringResource(R.string.w_regen)
            else -> ""
        }
        Text(label, style = c.label(20f), modifier = Modifier.padding(top = c.scale.dp(4f)))
    }
}

@Composable
private fun GearWidget(c: WCtx) {
    val g = c.state.gear
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        for (x in Gear.values()) {
            val active = g.available && g.value == x
            Text(
                x.label,
                style = c.label(if (active) 40f else 28f, if (active) c.accent else c.colors.textDim.copy(alpha = if (g.available) 0.7f else 0.25f), if (active) FontWeight.Bold else FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun ModeWidget(c: WCtx) {
    val m = c.state.mode
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        for (x in DriveMode.values()) {
            val active = m.available && m.value == x
            val name = when (x) {
                DriveMode.ECO -> stringResource(R.string.mode_eco)
                DriveMode.NORMAL -> stringResource(R.string.mode_normal)
                DriveMode.SPORT -> stringResource(R.string.mode_sport)
            }
            Text(
                name.uppercase(),
                style = c.label(if (active) 26f else 20f, if (active) c.accent else c.colors.textDim.copy(alpha = if (m.available) 0.7f else 0.25f), if (active) FontWeight.Bold else FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun TripWidget(c: WCtx) {
    val odo = c.state.odoKm
    val trip = c.state.tripKm
    val du = c.settings.distUnit
    fun fmt(s: Sig<Float>, dec: Int): String =
        if (s.available) String.format(Locale.US, "%.${dec}f", Units.distance(s.value, du)) else "--"
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${fmt(odo, 0)} ${Units.distanceLabel(du)}", style = c.text(30f))
            Text(stringResource(R.string.w_odo), style = c.label(16f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${fmt(trip, 1)} ${Units.distanceLabel(du)}", style = c.text(30f))
            Text(stringResource(R.string.w_trip), style = c.label(16f))
        }
    }
}

@Composable
private fun TyresWidget(c: WCtx) {
    val tyres = listOf(c.state.tyreFL, c.state.tyreFR, c.state.tyreRL, c.state.tyreRR)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Icon16(R.drawable.ic_tyre, if (tyres.any { it.available }) c.colors.textDim else c.colors.textDim.copy(alpha = 0.3f), c)
        for ((i, t) in tyres.withIndex()) {
            val col = when {
                !t.available -> c.colors.textDim.copy(alpha = 0.3f)
                t.value.state == TyreState.OK -> c.colors.ok
                t.value.state == TyreState.UNKNOWN -> c.colors.textDim
                else -> c.colors.warn
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(listOf("FL", "FR", "RL", "RR")[i], style = c.label(15f))
                Text(t.value.bar?.let { Units.bar(it) } ?: "•", style = c.label(22f, col, FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun Icon16(res: Int, tint: Color, c: WCtx, size: Float = 30f) {
    Image(painterResource(res), null, Modifier.size(c.scale.dp(size)), colorFilter = ColorFilter.tint(tint))
}

@Composable
private fun ClockWidget(c: WCtx) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
    val temp = c.state.outsideTempC
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(t, style = c.label(26f, c.colors.text))
        if (temp.available) Text("   ${Units.temp(temp.value, c.settings.tempUnit)}${Units.tempLabel(c.settings.tempUnit)}", style = c.label(22f))
    }
}

private fun DrawScope.arrow(left: Boolean, color: Color) {
    val w = size.width
    val h = size.height
    val p = Path()
    if (left) {
        p.moveTo(0f, h / 2f); p.lineTo(w * 0.5f, 0f); p.lineTo(w * 0.5f, h * 0.3f); p.lineTo(w, h * 0.3f)
        p.lineTo(w, h * 0.7f); p.lineTo(w * 0.5f, h * 0.7f); p.lineTo(w * 0.5f, h)
    } else {
        p.moveTo(w, h / 2f); p.lineTo(w * 0.5f, 0f); p.lineTo(w * 0.5f, h * 0.3f); p.lineTo(0f, h * 0.3f)
        p.lineTo(0f, h * 0.7f); p.lineTo(w * 0.5f, h * 0.7f); p.lineTo(w * 0.5f, h)
    }
    p.close()
    drawPath(p, color, style = Fill)
}

@Composable
private fun TelltaleWidget(c: WCtx) {
    val s = c.state
    val off = c.colors.textDim.copy(alpha = 0.18f)
    val na = c.colors.textDim.copy(alpha = 0.07f)
    fun col(sig: Sig<Boolean>, on: Color) = if (!sig.available) na else if (sig.value) on else off
    val l = s.leftSignal.value && s.leftSignal.available
    val r = s.rightSignal.value && s.rightSignal.available
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(c.scale.dp(44f), c.scale.dp(34f))) {
            arrow(true, if (!s.leftSignal.available) na else if (l && c.blinkOn) c.colors.ok else off)
        }
        Icon16(R.drawable.ic_low, col(s.lowBeam, c.colors.ok), c)
        Icon16(R.drawable.ic_high, col(s.highBeam, c.colors.info), c)
        Icon16(R.drawable.ic_park, col(s.parkBrake, c.colors.alert), c)
        Icon16(R.drawable.ic_belt, if (!s.beltDriver.available) na else if (!s.beltDriver.value) c.colors.alert else off, c)
        Icon16(if (s.locked.value && s.locked.available) R.drawable.ic_lock else R.drawable.ic_unlock, col(s.locked, c.colors.ok), c)
        Icon16(R.drawable.ic_plug, col(s.charging, c.colors.ok), c)
        Icon16(R.drawable.ic_regen, col(s.regenActive, c.colors.ok), c)
        Canvas(Modifier.size(c.scale.dp(44f), c.scale.dp(34f))) {
            arrow(false, if (!s.rightSignal.available) na else if (r && c.blinkOn) c.colors.ok else off)
        }
    }
}

@Composable
private fun CarWidget(c: WCtx) {
    if (c.reverse) {
        // Reverse: the camera replaces the car picture (no overlay, no animation).
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ReverseCameraPanel(c, Modifier.fillMaxSize()) }
        return
    }
    val s = c.state
    val hazard = s.hazard.value && s.hazard.available
    val l = (s.leftSignal.value && s.leftSignal.available) || hazard
    val r = (s.rightSignal.value && s.rightSignal.available) || hazard
    val glowAlpha = smooth(if (s.lowBeam.value) 1f else 0f, c.settings.animation)
    // the rear squats a little under power and lifts under regeneration
    val bob = smooth(if (s.powerNorm.available) s.powerNorm.value.coerceIn(-1f, 1f) else 0f, c.settings.animation)
    val bobPx = c.scale.px(5f)
    Box(Modifier.fillMaxSize().graphicsLayer { translationY = bob * bobPx }, contentAlignment = Alignment.Center) {
        // soft accent glow under the car
        Canvas(Modifier.fillMaxSize()) {
            drawOval(c.accent.copy(alpha = if (c.cfg.glow) 0.16f else 0.06f), Offset(size.width * 0.1f, size.height * 0.78f), Size(size.width * 0.8f, size.height * 0.14f))
        }
        val m = Modifier.fillMaxSize()
        Image(painterResource(R.drawable.car_rear_base), null, m, contentScale = ContentScale.Fit)
        Image(painterResource(R.drawable.car_rear_tail), null, m.alpha(glowAlpha), contentScale = ContentScale.Fit)
        if (s.brakePedal.value && s.brakePedal.available) Image(painterResource(R.drawable.car_rear_brake), null, m, contentScale = ContentScale.Fit)
        if (l && c.blinkOn) Image(painterResource(R.drawable.car_rear_turn_l), null, m, contentScale = ContentScale.Fit)
        if (r && c.blinkOn) Image(painterResource(R.drawable.car_rear_turn_r), null, m, contentScale = ContentScale.Fit)
    }
}

private data class Warn(val text: String, val color: Color, val icon: Int)

@Composable
private fun WarningsWidget(c: WCtx) {
    val s = c.state
    val list = ArrayList<Warn>()
    if (s.hazard.available && s.hazard.value) list.add(Warn(stringResource(R.string.warn_hazard), c.colors.warn, R.drawable.ic_warn))
    if (s.anyDoorOpen) {
        val names = ArrayList<String>()
        if (s.doorFL.value) names.add("FL"); if (s.doorFR.value) names.add("FR")
        if (s.doorRL.value) names.add("RL"); if (s.doorRR.value) names.add("RR")
        if (names.isNotEmpty()) list.add(Warn(stringResource(R.string.warn_door) + " · " + names.joinToString(" "), c.colors.alert, R.drawable.ic_door))
        if (s.trunk.value) list.add(Warn(stringResource(R.string.warn_trunk), c.colors.alert, R.drawable.ic_trunk))
    }
    if (s.beltDriver.available && !s.beltDriver.value) list.add(Warn(stringResource(R.string.warn_belt), c.colors.alert, R.drawable.ic_belt))
    if (listOf(s.tyreFL, s.tyreFR, s.tyreRL, s.tyreRR).any { it.available && (it.value.state == TyreState.LOW || it.value.state == TyreState.HIGH) })
        list.add(Warn(stringResource(R.string.warn_tyre), c.colors.warn, R.drawable.ic_tyre))
    if (s.socPct.available && s.socPct.value <= 20f && !(s.charging.available && s.charging.value))
        list.add(Warn(stringResource(R.string.warn_lowbat), c.colors.warn, R.drawable.ic_bolt))
    if (s.parkBrake.available && s.parkBrake.value && s.moving)
        list.add(Warn(stringResource(R.string.warn_parkbrake), c.colors.alert, R.drawable.ic_park))
    if (s.charging.available && s.charging.value)
        list.add(Warn(stringResource(R.string.warn_charging) + (if (s.chargeKw.available && s.chargeKw.value > 0f) " · ${String.format(Locale.US, "%.0f", s.chargeKw.value)} kW" else ""), c.colors.ok, R.drawable.ic_plug))
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        for (w in list.take(3)) {
            Row(
                Modifier.padding(horizontal = c.scale.dp(6f)).background(w.color.copy(alpha = 0.16f), RoundedCornerShape(c.scale.dp(20f)))
                    .padding(horizontal = c.scale.dp(14f), vertical = c.scale.dp(6f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon16(w.icon, w.color, c, 22f)
                Text("  " + w.text, style = c.label(20f, w.color, FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun ProjectionWidget(c: WCtx) {
    val p = c.projection
    val du = c.settings.distUnit
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.w_projected), style = c.label(20f))
        if (p == null) {
            Text("--", style = c.text(76f, color = c.colors.textDim))
            Text(stringResource(R.string.proj_need_history), style = c.label(15f), textAlign = TextAlign.Center)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${Units.distance(p.rangeKm, du).toInt()}", style = c.text(76f, color = c.accent))
                Text(" " + Units.distanceLabel(du), style = c.label(24f), modifier = Modifier.padding(bottom = c.scale.dp(10f)))
            }
            val d = p.deltaKm
            val delta = if (d == null) "" else String.format(Locale.US, "%+d", Units.distance(d, du).toInt()) + " " + stringResource(R.string.proj_vs_car)
            Text(
                String.format(Locale.US, "%.1f", Units.consumption(p.rateKwhPer100, du)) + " " + Units.consumptionLabel(du) +
                    " · " + stringResource(R.string.proj_last, Units.distance(p.basisKm, du).toInt()) + (if (delta.isNotEmpty()) " · $delta" else ""),
                style = c.label(15f), textAlign = TextAlign.Center
            )
        }
    }
}
