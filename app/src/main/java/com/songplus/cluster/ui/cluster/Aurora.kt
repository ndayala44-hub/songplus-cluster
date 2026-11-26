package com.songplus.cluster.ui.cluster

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.songplus.cluster.data.AnimLevel
import com.songplus.cluster.data.DistUnit
import com.songplus.cluster.data.DriveMode
import com.songplus.cluster.data.Gear
import com.songplus.cluster.data.Units
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val MINT = Color(0xFF2FE3A0)
private val CRIMSON = Color(0xFFFF2D55)
private val ORANGE = Color(0xFFFF8A1F)
private val AMBER = Color(0xFFFFB020)
private val ICE = Color(0xFF7CC8FF)
private val WHITE = Color(0xFFF4F6FB)

private const val ROAD_VP_Y = 250f
private const val ROAD_BOTTOM_Y = 650f
private const val ROAD_HALF = 340f
private const val DASHES = 9

private fun mix(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k,
        a.blue + (b.blue - a.blue) * k, a.alpha + (b.alpha - a.alpha) * k
    )
}

private fun pointOn(c: Offset, r: Float, deg: Float): Offset {
    val a = deg * PI.toFloat() / 180f
    return Offset(c.x + r * cos(a), c.y + r * sin(a))
}

/**
 * "Aurora": a full-canvas, slick reactive scene. Everything in it is driven by live vehicle state:
 * the road scrolls with speed, the glow follows motor power and braking, the accent colour follows the drive mode,
 * low battery pulses, charging replaces the road with radiating rings, cold weather adds a frost warning,
 * and the whole scene dims at night or when the headlights are on.
 * Values the data source cannot supply are drawn empty with "--", never invented.
 */
@Composable
internal fun AuroraWidget(c: WCtx) {
    val s = c.state
    val lvl = c.settings.animation
    val measurer = rememberTextMeasurer()

    // ---- time base: one clock for pulses, one phase for the moving road ----
    val clock = remember { mutableFloatStateOf(0f) }
    val roadPhase = remember { mutableFloatStateOf(0f) }
    val speedNow by rememberUpdatedState(if (s.speedKmh.available) s.speedKmh.value else 0f)
    LaunchedEffect(lvl) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { n ->
                if (last != 0L && lvl != AnimLevel.OFF) {
                    val dt = ((n - last) / 1_000_000_000f).coerceAtMost(0.1f)
                    val k = if (lvl == AnimLevel.REDUCED) 0.5f else 1f
                    clock.floatValue += dt * k
                    roadPhase.floatValue = (roadPhase.floatValue + dt * k * speedNow / 34f) % 1f
                }
                last = n
            }
        }
    }

    // ---- start-up reveal (rings sweep in, text fades in) ----
    val reveal = remember { Animatable(if (lvl == AnimLevel.OFF) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reveal.value < 1f) reveal.animateTo(1f, tween(1900, easing = FastOutSlowInEasing))
    }

    // ---- date / time ----
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val hour = remember(now / 60000L) { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val night = hour >= 19 || hour < 6
    val lightsOn = s.lowBeam.available && s.lowBeam.value

    // ---- smoothed vehicle values ----
    val speedUnit = c.settings.speedUnit
    val shownKmh = if (s.speedKmh.available)
        Units.displaySpeedKmh(s.speedKmh.value, c.settings.speedMode, c.settings.speedPercent, c.settings.speedPlusKmh) else 0f
    val shownSpeed = Units.speed(shownKmh, speedUnit)
    val speedFrac by smoothF(if (s.speedKmh.available) shownSpeed / Units.speedMax(speedUnit) else 0f, lvl, 380)
    val soc by smoothF(if (s.socPct.available) s.socPct.value / 100f else 0f, lvl, 700)
    val power by smoothF(if (s.powerNorm.available) s.powerNorm.value.coerceIn(-1f, 1f) else 0f, lvl, 320)
    val brake by smoothF(if (s.brakePedal.available && s.brakePedal.value) 1f else 0f, lvl, 140)
    val ambient by smoothF(if (night || lightsOn) 1f else 0f, lvl, 2200)
    val lightsK by smoothF(if (lightsOn) 1f else 0f, lvl, 450)
    val highK by smoothF(if (s.highBeam.available && s.highBeam.value) 1f else 0f, lvl, 450)
    val modeIdx by smoothF(
        if (!s.mode.available) 1f else when (s.mode.value) { DriveMode.ECO -> 0f; DriveMode.NORMAL -> 1f; DriveMode.SPORT -> 2f },
        lvl, 420
    )
    val modeColor = if (!s.mode.available) CRIMSON else when (s.mode.value) {
        DriveMode.ECO -> MINT; DriveMode.NORMAL -> CRIMSON; DriveMode.SPORT -> ORANGE
    }
    val acc by animateColorAsState(modeColor, if (lvl == AnimLevel.OFF) tween(0) else tween(650), label = "auroraAccent")

    // ---- rolling power history for the little graph ----
    val hist = remember { mutableStateListOf<Float>() }
    val powerNow by rememberUpdatedState(power)
    LaunchedEffect(Unit) {
        while (true) {
            hist.add(powerNow)
            if (hist.size > 56) hist.removeAt(0)
            delay(250)
        }
    }

    // ---- derived texts (never invented) ----
    val du: DistUnit = c.settings.distUnit
    val rangeTxt = if (s.rangeKm.available) Units.distance(s.rangeKm.value, du).toInt().toString() else "--"
    val socTxt = if (s.socPct.available) s.socPct.value.toInt().toString() else "--"
    val odoTxt = if (s.odoKm.available) Units.distance(s.odoKm.value, du).toInt().toString() else "--"
    val tripTxt = if (s.tripKm.available) String.format(Locale.US, "%.1f", Units.distance(s.tripKm.value, du)) else "--"
    val avgWh = if (s.avgKwhPer100.available) (Units.consumption(s.avgKwhPer100.value, du) * 10f).toInt().toString() else "--"
    val avgUnit = if (du == DistUnit.MI) "Wh/mi" else "Wh/km"
    val tempOk = s.outsideTempC.available
    val tempC = s.outsideTempC.value
    val tempTxt = if (tempOk) "${Units.temp(tempC, c.settings.tempUnit)}${Units.tempLabel(c.settings.tempUnit)}" else "--"
    val dateTxt = remember(now / 60000L) { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(now)) }
    val timeTxt = remember(now / 60000L) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val parked = s.gear.available && s.gear.value == Gear.P && shownSpeed == 0
    val charging = s.charging.available && s.charging.value
    val chargeKw = if (s.chargeKw.available) s.chargeKw.value else 0f
    val leftOn = (s.leftSignal.available && s.leftSignal.value) || (s.hazard.available && s.hazard.value)
    val rightOn = (s.rightSignal.available && s.rightSignal.value) || (s.hazard.available && s.hazard.value)
    val blink = c.blinkOn
    val unitLabel = Units.speedLabel(speedUnit)
    val gearSel = if (s.gear.available) s.gear.value else null

    Canvas(Modifier.fillMaxSize()) {
        val u = size.height / 720f
        val cx = size.width / 2f
        fun X(dx: Float) = cx + dx * u
        fun Y(dy: Float) = dy * u
        val ease = reveal.value
        val fa = ((ease - 0.25f) / 0.6f).coerceIn(0f, 1f)          // overall fade-in
        val sweepK = (ease / 0.85f).coerceIn(0f, 1f)               // arcs draw in over the first part
        val pulse = 0.5f + 0.5f * sin(clock.floatValue * 5.2f)
        fun a(col: Color, k: Float = 1f) = col.copy(alpha = (col.alpha * k * fa).coerceIn(0f, 1f))

        drawBackground(u, cx, acc, power, ambient, brake)

        // ================= centre: road / charging scene =================
        val vp = Offset(X(0f), Y(ROAD_VP_Y))
        val bottomY = Y(ROAD_BOTTOM_Y)
        if (!charging) {
            drawRoad(u, cx, acc, roadPhase.floatValue, speedFrac, leftOn && blink, rightOn && blink, lightsK, highK, brake, fa, clock.floatValue, s.outsideTempC.available && tempC <= 3f)
        } else {
            for (i in 0 until 3) {
                val tk = ((clock.floatValue * 0.45f) + i / 3f) % 1f
                val rx = (90f + 360f * tk) * u
                val ry = rx * 0.26f
                drawOval(
                    a(MINT, (1f - tk) * 0.55f), Offset(X(0f) - rx, Y(588f) - ry), Size(rx * 2f, ry * 2f),
                    style = Stroke(width = 2.5f * u)
                )
            }
        }

        // ================= left: battery arc + reach ring =================
        run {
            val lc = Offset(X(-640f), Y(368f))
            val rIn = 112f * u
            val rOut = 208f * u
            val lowK = if (s.socPct.available) ((20f - s.socPct.value) / 10f).coerceIn(0f, 1f) else 0f
            val critical = s.socPct.available && s.socPct.value < 10f
            var battCol = mix(acc, AMBER, lowK)
            if (critical) battCol = mix(AMBER, Color(0xFFFF3B30), pulse)
            if (charging) battCol = mix(MINT, WHITE, pulse * 0.25f)

            // reach ring
            drawCircle(a(acc, 0.16f), rIn, lc, style = Stroke(16f * u))
            drawCircle(a(acc, 0.95f), rIn, lc, style = Stroke(2.6f * u))
            t(measurer, "REACH", lc.x, lc.y - 50f * u, 14f * u, a(acc), FontWeight.Medium, 0, 2f * u)
            t(measurer, rangeTxt, lc.x - 8f * u, lc.y + 4f * u, 58f * u, a(WHITE), FontWeight.Light)
            t(measurer, Units.distanceLabel(du), lc.x, lc.y + 52f * u, 17f * u, a(WHITE, 0.75f), FontWeight.Normal)

            // outer arc: track, dashed fill, glow
            val start = 100f
            val full = 170f
            drawArc(a(WHITE, 0.20f), start, full, false, Offset(lc.x - rOut, lc.y - rOut), Size(rOut * 2, rOut * 2), style = Stroke(2.2f * u, cap = StrokeCap.Round))
            val fill = full * soc * sweepK
            if (fill > 0.5f) {
                drawArc(a(battCol, 0.14f), start, fill, false, Offset(lc.x - rOut, lc.y - rOut), Size(rOut * 2, rOut * 2), style = Stroke(26f * u))
                drawArc(
                    a(battCol, 0.95f), start, fill, false, Offset(lc.x - rOut, lc.y - rOut), Size(rOut * 2, rOut * 2),
                    style = Stroke(9f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(11f * u, 6f * u), 0f))
                )
                val ri = rOut - 14f * u
                drawArc(a(battCol, 0.9f), start, fill, false, Offset(lc.x - ri, lc.y - ri), Size(ri * 2, ri * 2), style = Stroke(2f * u))
                if (charging) {
                    // light travelling up the arc while charging
                    val head = start + fill * ((clock.floatValue * 0.6f) % 1f)
                    val p = pointOn(lc, rOut, head)
                    drawCircle(Brush.radialGradient(listOf(a(WHITE, 0.95f), Color.Transparent), p, 24f * u), 24f * u, p)
                }
                val end = pointOn(lc, rOut, start + fill)
                drawCircle(a(battCol), 4.5f * u, end)
            }
            // scale marks and labels
            for (i in 0..4) {
                val p = pointOn(lc, rOut + 22f * u, start + full * i / 4f)
                drawCircle(a(WHITE, if (i == 0 || i == 4) 0.9f else 0.45f), 2.4f * u, p)
            }
            val tp = pointOn(lc, rOut + 44f * u, 262f)
            t(measurer, "100", tp.x + 12f * u, tp.y + 8f * u, 13f * u, a(WHITE, 0.8f))
            val bp = pointOn(lc, rOut + 40f * u, 104f)
            t(measurer, "0", bp.x - 4f * u, bp.y + 4f * u, 13f * u, a(WHITE, 0.8f))
            // lightning bolt (pulses while charging)
            val bolt = pointOn(lc, rOut + 20f * u, 268f)
            drawBolt(bolt.x - 4f * u, bolt.y - 30f * u, 20f * u, a(if (charging) MINT else WHITE, if (charging) 0.6f + 0.4f * pulse else 0.85f))
            // percent
            val px = lc.x - (rIn + rOut) / 2f - 2f * u
            t(measurer, socTxt, px - 4f * u, lc.y - 8f * u, 32f * u, a(if (critical || lowK > 0.5f) battCol else WHITE), FontWeight.Normal)
            t(measurer, "%", px + 24f * u, lc.y - 14f * u, 15f * u, a(WHITE, 0.8f))
            t(measurer, "BATTERY", px, lc.y + 16f * u, 10.5f * u, a(WHITE, 0.6f), FontWeight.Medium, 0, 1f * u)
            if (charging) {
                t(measurer, String.format(Locale.US, "%.1f kW", chargeKw), lc.x, lc.y + 86f * u, 18f * u, a(MINT), FontWeight.Medium)
            }
        }

        // ================= right: speed ring + power arcs =================
        run {
            val rc = Offset(X(640f), Y(368f))
            val rIn = 112f * u
            val rOut = 208f * u
            // soft halo that breathes with power
            drawCircle(
                Brush.radialGradient(listOf(a(acc, 0.10f + 0.16f * abs(power)), Color.Transparent), rc, rOut * 1.25f),
                rOut * 1.25f, rc
            )
            drawCircle(a(acc, 0.16f), rIn, rc, style = Stroke(16f * u))
            drawCircle(a(acc, 0.95f), rIn, rc, style = Stroke(2.6f * u))

            // outer speed arc: top (270 deg) clockwise down the right side to 80 deg
            val start = 270f
            val full = 170f
            drawArc(a(WHITE, 0.30f), start, full, false, Offset(rc.x - rOut, rc.y - rOut), Size(rOut * 2, rOut * 2), style = Stroke(2.2f * u, cap = StrokeCap.Round))
            val sw = full * speedFrac * sweepK
            if (sw > 0.3f) {
                drawArc(a(WHITE, 0.10f), start, sw, false, Offset(rc.x - rOut, rc.y - rOut), Size(rOut * 2, rOut * 2), style = Stroke(22f * u))
                drawArc(a(WHITE, 0.95f), start, sw, false, Offset(rc.x - rOut, rc.y - rOut), Size(rOut * 2, rOut * 2), style = Stroke(4f * u, cap = StrokeCap.Round))
            }
            val dot = pointOn(rc, rOut, start + sw)
            drawCircle(Brush.radialGradient(listOf(a(WHITE, 0.7f), Color.Transparent), dot, 20f * u), 20f * u, dot)
            drawCircle(a(WHITE), 6f * u, dot)
            for (i in 0..8) {
                val p = pointOn(rc, rOut + 22f * u, start + full * i / 8f)
                drawCircle(a(WHITE, if (i % 2 == 0) 0.7f else 0.35f), if (i % 2 == 0) 2.4f * u else 1.6f * u, p)
            }

            // power arcs hugging the inner ring: boost above, charge below
            val rp = rIn + 15f * u
            val boost = max(power, 0f)
            val regen = max(-power, 0f).let { if (charging) max(it, 0.4f) else it }
            val pe = 88f * sweepK
            if (boost > 0.01f) {
                drawArc(a(acc, 0.20f), -pe * boost, pe * boost, false, Offset(rc.x - rp, rc.y - rp), Size(rp * 2, rp * 2), style = Stroke(18f * u))
                drawArc(a(acc), -pe * boost, pe * boost, false, Offset(rc.x - rp, rc.y - rp), Size(rp * 2, rp * 2), style = Stroke(5f * u, cap = StrokeCap.Butt))
            }
            if (regen > 0.01f) {
                drawArc(a(MINT, 0.22f), 0f, pe * regen, false, Offset(rc.x - rp, rc.y - rp), Size(rp * 2, rp * 2), style = Stroke(18f * u))
                drawArc(a(MINT), 0f, pe * regen, false, Offset(rc.x - rp, rc.y - rp), Size(rp * 2, rp * 2), style = Stroke(5f * u))
            }
            val bl = pointOn(rc, rIn + 46f * u, -52f)
            t(measurer, "BOOST", bl.x, bl.y, 10.5f * u, a(acc), FontWeight.Medium, 0, 1.2f * u)
            val cl = pointOn(rc, rIn + 46f * u, 52f)
            t(measurer, "CHARGE", cl.x, cl.y, 10.5f * u, a(WHITE, 0.75f), FontWeight.Medium, 0, 1.2f * u)
            drawCircle(a(MINT), 3f * u, Offset(cl.x + 34f * u, cl.y))

            // readout: P when parked, otherwise speed
            if (!s.speedKmh.available) {
                t(measurer, "--", rc.x, rc.y - 6f * u, 90f * u, a(WHITE), FontWeight.Light)
            } else if (parked) {
                t(measurer, "P", rc.x, rc.y - 6f * u, 96f * u, a(WHITE), FontWeight.Light)
            } else {
                t(measurer, shownSpeed.toString(), rc.x, rc.y - 6f * u, 92f * u, a(WHITE), FontWeight.Light)
            }
            t(measurer, unitLabel, rc.x, rc.y + 58f * u, 17f * u, a(WHITE, 0.65f), FontWeight.Normal)

            // gear selector
            val letters = listOf("P", "R", "N", "D")
            for ((i, g) in letters.withIndex()) {
                val gx = rc.x + (i - 1.5f) * 36f * u
                val on = gearSel != null && gearSel.label == g
                t(measurer, g, gx, rc.y + 152f * u, if (on) 24f * u else 17f * u, a(if (on) WHITE else WHITE, if (on) 1f else 0.35f), if (on) FontWeight.Bold else FontWeight.Normal)
                if (on) drawLine(a(acc), Offset(gx - 9f * u, rc.y + 168f * u), Offset(gx + 9f * u, rc.y + 168f * u), 3f * u, StrokeCap.Round)
            }
        }

        // ================= top: mode tabs =================
        run {
            val line = Path().apply {
                moveTo(X(-470f), Y(96f)); quadraticBezierTo(X(-390f), Y(78f), X(-310f), Y(78f))
                lineTo(X(310f), Y(78f)); quadraticBezierTo(X(390f), Y(78f), X(470f), Y(96f))
            }
            drawPath(line, a(WHITE, 0.30f), style = Stroke(1.6f * u))
            val names = listOf("ECO", "NORMAL", "SPORT")
            val xs = listOf(-130f, 0f, 130f)
            for (i in 0..2) {
                val on = abs(modeIdx - i) < 0.5f
                t(measurer, names[i], X(xs[i]), Y(54f), 15f * u, a(WHITE, if (on) 1f else 0.42f), if (on) FontWeight.Bold else FontWeight.Medium, 0, 2.2f * u)
            }
            val ix = X(-130f + 130f * modeIdx)
            drawLine(a(acc, 0.28f), Offset(ix - 42f * u, Y(78f)), Offset(ix + 42f * u, Y(78f)), 10f * u, StrokeCap.Round)
            drawLine(a(acc), Offset(ix - 38f * u, Y(78f)), Offset(ix + 38f * u, Y(78f)), 3f * u, StrokeCap.Round)
        }

        // ================= centre-left: efficiency graph =================
        run {
            val x0 = X(-355f); val x1 = X(-150f); val y0 = Y(300f)
            val h = 46f * u
            drawLine(a(WHITE, 0.10f), Offset(x0, y0), Offset(x1, y0), 1f * u)
            if (hist.size > 2) {
                val path = Path()
                for (i in hist.indices) {
                    val px = x0 + (x1 - x0) * i / 55f
                    val py = y0 - hist[i].coerceIn(-1f, 1f) * h * 0.9f
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, a(acc, 0.9f), style = Stroke(1.8f * u, cap = StrokeCap.Round))
                val last = hist.last().coerceIn(-1f, 1f)
                drawCircle(a(if (last < 0f) MINT else acc), 3.5f * u, Offset(x0 + (x1 - x0) * (hist.size - 1) / 55f, y0 - last * h * 0.9f))
            }
            t(measurer, "AVG", (x0 + x1) / 2f, Y(366f), 11f * u, a(WHITE, 0.55f), FontWeight.Medium, 0, 1.5f * u)
            t(measurer, "$avgWh $avgUnit", (x0 + x1) / 2f, Y(388f), 17f * u, a(WHITE, 0.92f), FontWeight.Medium)
        }

        // ================= centre-right: trip / odometer =================
        run {
            val xc = X(255f)
            t(measurer, "TRIP", xc, Y(300f), 11f * u, a(WHITE, 0.55f), FontWeight.Medium, 0, 1.5f * u)
            t(measurer, "$tripTxt ${Units.distanceLabel(du)}", xc, Y(324f), 19f * u, a(WHITE, 0.95f), FontWeight.Medium)
            t(measurer, "ODO", xc, Y(366f), 11f * u, a(WHITE, 0.55f), FontWeight.Medium, 0, 1.5f * u)
            t(measurer, "$odoTxt ${Units.distanceLabel(du)}", xc, Y(390f), 19f * u, a(WHITE, 0.95f), FontWeight.Medium)
        }

        // ================= bottom: date, time, temperature =================
        run {
            val line = Path().apply {
                moveTo(X(-300f), Y(668f)); lineTo(X(300f), Y(668f))
            }
            drawPath(line, Brush.horizontalGradient(listOf(Color.Transparent, a(acc, 0.9f), Color.Transparent), X(-300f), X(300f)), style = Stroke(2f * u))
            t(measurer, dateTxt, X(-170f), Y(696f), 17f * u, a(WHITE, 0.9f))
            t(measurer, timeTxt, X(0f), Y(696f), 22f * u, a(WHITE), FontWeight.Medium)
            val frost = tempOk && tempC <= 3f
            val hot = tempOk && tempC >= 35f
            val tcol = if (frost) ICE else if (hot) AMBER else WHITE
            t(measurer, tempTxt, X(150f), Y(696f), 17f * u, a(tcol, 0.95f))
            if (frost) t(measurer, "FROST", X(222f), Y(696f), 12f * u, a(ICE), FontWeight.Bold, 0, 1.5f * u)
            if (hot) t(measurer, "HOT", X(215f), Y(696f), 12f * u, a(AMBER), FontWeight.Bold, 0, 1.5f * u)
        }
    }
}

@Composable
private fun smoothF(target: Float, lvl: AnimLevel, ms: Int): androidx.compose.runtime.State<Float> =
    androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = if (lvl == AnimLevel.OFF) androidx.compose.animation.core.snap() else tween(ms),
        label = "auroraSmooth"
    )

// ---------------------------------------------------------------------------------------------------------------------
// drawing helpers
// ---------------------------------------------------------------------------------------------------------------------

private fun DrawScope.t(
    m: TextMeasurer, text: String, x: Float, y: Float, sizePx: Float, color: Color,
    weight: FontWeight = FontWeight.Normal, align: Int = 0, spacingPx: Float = 0f
) {
    if (color.alpha < 0.01f) return
    val style = TextStyle(
        fontSize = sizePx.toSp(), color = color, fontWeight = weight, fontFamily = FontFamily.SansSerif,
        letterSpacing = spacingPx.toSp()
    )
    val r = m.measure(text, style)
    val tx = when (align) { 0 -> x - r.size.width / 2f; 1 -> x; else -> x - r.size.width }
    drawText(r, topLeft = Offset(tx, y - r.size.height / 2f))
}

private fun DrawScope.drawBolt(x: Float, y: Float, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(x + 0.62f * s, y)
        lineTo(x + 0.12f * s, y + 0.58f * s)
        lineTo(x + 0.46f * s, y + 0.58f * s)
        lineTo(x + 0.32f * s, y + 1.1f * s)
        lineTo(x + 0.9f * s, y + 0.46f * s)
        lineTo(x + 0.54f * s, y + 0.46f * s)
        close()
    }
    drawPath(p, color, style = Fill)
}

private fun DrawScope.drawBackground(u: Float, cx: Float, acc: Color, power: Float, ambient: Float, brake: Float) {
    val top = mix(Color(0xFF1E2038), Color(0xFF0E0F1D), ambient)
    val bot = mix(Color(0xFF12131F), Color(0xFF06060D), ambient)
    drawRect(Brush.verticalGradient(listOf(top, bot)))
    // vignette
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
            Offset(cx, 360f * u), 1250f * u
        )
    )
    // accent glow under the car reacts to power; red flash when braking
    val k = 0.10f + 0.22f * abs(power)
    drawCircle(
        Brush.radialGradient(listOf(acc.copy(alpha = k), Color.Transparent), Offset(cx, 600f * u), 430f * u),
        430f * u, Offset(cx, 600f * u)
    )
    if (brake > 0.01f) {
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFF2A2A).copy(alpha = 0.40f * brake), Color.Transparent), Offset(cx, 600f * u), 330f * u),
            330f * u, Offset(cx, 600f * u)
        )
    }
    drawRoundRect(
        Color.White.copy(alpha = 0.06f), Offset(2f * u, 2f * u), Size(size.width - 4f * u, size.height - 4f * u),
        androidx.compose.ui.geometry.CornerRadius(34f * u, 34f * u), style = Stroke(1.5f * u)
    )
}

private fun DrawScope.drawRoad(
    u: Float, cx: Float, acc: Color, phase: Float, speedFrac: Float,
    leftSig: Boolean, rightSig: Boolean, lights: Float, high: Float, brake: Float, fade: Float, clock: Float, cold: Boolean
) {
    val vp = Offset(cx, ROAD_VP_Y * u)
    val by = ROAD_BOTTOM_Y * u
    val tint = if (cold) ICE else Color.White
    // road surface
    val road = Path().apply {
        moveTo(vp.x - 6f * u, vp.y); lineTo(vp.x + 6f * u, vp.y)
        lineTo(cx + ROAD_HALF * u, by); lineTo(cx - ROAD_HALF * u, by); close()
    }
    drawPath(road, Brush.verticalGradient(listOf(Color.Transparent, acc.copy(alpha = 0.10f * fade)), vp.y, by))
    // headlight cone
    if (lights > 0.01f) {
        val spread = (ROAD_HALF * (0.55f + 0.35f * high)) * u
        val cone = Path().apply {
            moveTo(vp.x - 4f * u, vp.y + 20f * u); lineTo(vp.x + 4f * u, vp.y + 20f * u)
            lineTo(cx + spread, by); lineTo(cx - spread, by); close()
        }
        val lc = mix(Color.White, Color(0xFFBFD8FF), high)
        drawPath(cone, Brush.verticalGradient(listOf(Color.Transparent, lc.copy(alpha = (0.07f + 0.08f * high) * lights * fade)), vp.y, by))
    }
    // road edges (turn signal tints the edge on that side)
    val edgeL = if (leftSig) MINT else tint
    val edgeR = if (rightSig) MINT else tint
    drawLine(
        Brush.verticalGradient(listOf(Color.Transparent, edgeL.copy(alpha = (if (leftSig) 0.95f else 0.38f) * fade)), vp.y, by),
        vp, Offset(cx - ROAD_HALF * u, by), (if (leftSig) 5f else 2f) * u
    )
    drawLine(
        Brush.verticalGradient(listOf(Color.Transparent, edgeR.copy(alpha = (if (rightSig) 0.95f else 0.38f) * fade)), vp.y, by),
        vp, Offset(cx + ROAD_HALF * u, by), (if (rightSig) 5f else 2f) * u
    )
    // moving lane dashes (two lane lines) — they scroll with speed
    for (lane in listOf(-112f, 112f)) {
        for (k in 0 until DASHES) {
            val t0 = ((k + phase) % DASHES) / DASHES.toFloat()
            val t1 = t0 + 0.55f / DASHES
            val s0 = t0 * t0
            val s1 = min(t1 * t1, 1f)
            val p0 = Offset(cx + lane * u * s0, vp.y + (by - vp.y) * s0)
            val p1 = Offset(cx + lane * u * s1, vp.y + (by - vp.y) * s1)
            val al = (s0 * 1.6f).coerceIn(0f, 0.85f) * fade
            val col = mix(tint, Color(0xFFFF4040), brake * 0.7f)
            drawLine(col.copy(alpha = al), p0, p1, (1.5f + 5f * s1) * u, StrokeCap.Round)
        }
    }
    // speed streaks radiate from the vanishing point
    if (speedFrac > 0.12f) {
        for (k in 0 until 14) {
            val ang = (20f + (k * 137.5f) % 140f) * PI.toFloat() / 180f
            val tt = ((clock * (0.35f + speedFrac * 1.4f)) + k * 0.173f) % 1f
            val r0 = (30f + 760f * tt * tt) * u
            val r1 = r0 + (14f + 120f * speedFrac * tt) * u
            val d = Offset(cos(ang), sin(ang))
            drawLine(
                tint.copy(alpha = 0.32f * speedFrac * tt * fade),
                Offset(vp.x + d.x * r0, vp.y + d.y * r0 * 0.55f), Offset(vp.x + d.x * r1, vp.y + d.y * r1 * 0.55f),
                1.6f * u, StrokeCap.Round
            )
        }
    }
}
