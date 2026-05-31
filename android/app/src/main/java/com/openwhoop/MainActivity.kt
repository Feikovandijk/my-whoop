package com.openwhoop

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.openwhoop.ble.WhoopBleService
import com.openwhoop.ble.protocol.*
import com.openwhoop.database.*
import com.openwhoop.sync.MetricExplanation
import com.openwhoop.sync.MetricExplanationCopy
import com.openwhoop.sync.MetricExplanationFallback
import com.openwhoop.sync.ServerSync
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun Double?.orZero(): Double = if (this == null || this.isNaN()) 0.0 else this

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            startWhoopService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            OpenWhoopAppTheme {
                MainScreen()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            startWhoopService()
        }
    }

    private fun startWhoopService() {
        val intent = Intent(this, WhoopBleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

// PREMIUM ATHLETIC DARK THEME TOKENS
object OWColor {
    val bg0 = Color(0xFF07090B)
    val bg1 = Color(0xFF0E1216)
    val bg2 = Color(0xFF161B20)
    val bg3 = Color(0xFF1F262C)
    
    val line1 = Color(0x12FFFFFF) // 7% alpha white border
    val line2 = Color(0x1FFFFFFF) // 12% alpha white divider
    val line3 = Color(0x0AFFFFFF) // 4% alpha white chart gridline
    
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFF9AA4AD)
    val textTertiary = Color(0xFF5D666E)
    
    val green = Color(0xFF2BF07A)
    val greenDim = Color(0xFF1AB659)
    val yellow = Color(0xFFFFD23F)
    val red = Color(0xFFFF4D6A)
    val blue = Color(0xFF2C9BFF)
    val blueDim = Color(0xFF1C6FBE)
    val teal = Color(0xFF2EE6C6)
    val purple = Color(0xFFA66BFF)
    val indigo = Color(0xFF3D5AFE)
    
    // Sleep stages
    val stageDeep = Color(0xFF3D5AFE)
    val stageRem = Color(0xFFA66BFF)
    val stageLight = Color(0xFF2C9BFF)
    val stageAwake = Color(0xFFFF4D6A)
    
    // Zones
    val zone0 = Color(0xFF3F5450)
    val zone1 = Color(0xFF2EE6C6)
    val zone2 = Color(0xFF39D6FF)
    val zone3 = Color(0xFFFFD23F)
    val zone4 = Color(0xFFFF9F43)
    val zone5 = Color(0xFFFF5A6E)
    
    fun recoveryColor(v: Double): Color = when {
        v >= 0.66 -> green
        v >= 0.34 -> yellow
        else -> red
    }
    
    fun recoveryWord(v: Double): String = when {
        v >= 0.66 -> "High"
        v >= 0.34 -> "Moderate"
        else -> "Low"
    }
}

// PREMIUM SLEEK DARK THEME
@Composable
fun OpenWhoopAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = OWColor.green,
            secondary = OWColor.blue,
            background = OWColor.bg0,
            surface = OWColor.bg1,
            onPrimary = Color.Black,
            onBackground = OWColor.textPrimary,
            onSurface = OWColor.textPrimary
        ),
        content = content
    )
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Today") },
                    label = { Text("Today") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Sleep") },
                    label = { Text("Sleep") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Trends") },
                    label = { Text("Trends") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Workouts") },
                    label = { Text("Workouts") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Device") },
                    label = { Text("Device") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> TodayTabScreen()
                1 -> SleepTabScreen()
                2 -> TrendsTabScreen()
                3 -> WorkoutsTabScreen()
                4 -> DeviceTabScreen()
            }
        }
    }
}

@Composable
fun SparklineChart(
    points: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val maxVal = points.maxOrNull() ?: 1.0
        val minVal = points.minOrNull() ?: 0.0
        val diff = if (maxVal == minVal) 1.0 else maxVal - minVal
        
        val width = size.width
        val height = size.height
        val step = width / (points.size - 1)

        val path = Path()
        points.forEachIndexed { i, p ->
            val x = i * step
            val y = height - 2.dp.toPx() - (((p - minVal) / diff) * (height - 4.dp.toPx())).toFloat()
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

data class SleepStageSegment(
    val start: Long,
    val end: Long,
    val stage: String
)

@Composable
fun SleepHypnogram(
    stagesJSON: String?,
    startTs: Long,
    endTs: Long,
    modifier: Modifier = Modifier
) {
    val segments = remember(stagesJSON) {
        if (stagesJSON.isNullOrEmpty()) {
            listOf(
                SleepStageSegment(0, 18, "light"),
                SleepStageSegment(18, 60, "deep"),
                SleepStageSegment(60, 82, "light"),
                SleepStageSegment(82, 110, "rem"),
                SleepStageSegment(110, 140, "light"),
                SleepStageSegment(140, 174, "deep"),
                SleepStageSegment(174, 180, "wake"),
                SleepStageSegment(180, 206, "light"),
                SleepStageSegment(206, 244, "rem"),
                SleepStageSegment(244, 268, "light"),
                SleepStageSegment(268, 286, "deep"),
                SleepStageSegment(286, 316, "rem"),
                SleepStageSegment(316, 336, "light"),
                SleepStageSegment(336, 341, "wake"),
                SleepStageSegment(341, 363, "rem"),
                SleepStageSegment(363, 389, "light")
            )
        } else {
            try {
                val listType = object : com.google.gson.reflect.TypeToken<List<SleepStageSegment>>() {}.type
                com.google.gson.Gson().fromJson<List<SleepStageSegment>>(stagesJSON, listType)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    if (segments.isEmpty()) return

    val totalDuration = if (stagesJSON.isNullOrEmpty()) {
        segments.sumOf { (it.end - it.start) }.toDouble()
    } else {
        (endTs - startTs).toDouble().coerceAtLeast(1.0)
    }

    val levels = mapOf("wake" to 0, "awake" to 0, "rem" to 1, "light" to 2, "deep" to 3)
    val colors = mapOf(
        "wake" to OWColor.stageAwake,
        "awake" to OWColor.stageAwake,
        "rem" to OWColor.stageRem,
        "light" to OWColor.stageLight,
        "deep" to OWColor.stageDeep
    )

    Column(modifier = modifier) {
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
        ) {
            val width = size.width
            val height = size.height
            val rowH = height / 4f

            for (i in 0..3) {
                val y = i * rowH + rowH / 2f
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            var currentX = 0f
            segments.forEach { seg ->
                val segDuration = (seg.end - seg.start).toDouble()
                val segW = (segDuration / totalDuration * width).toFloat()
                
                val level = levels[seg.stage.lowercase()] ?: 2
                val color = colors[seg.stage.lowercase()] ?: OWColor.stageLight

                val y = level * rowH + 3.dp.toPx()
                val h = rowH - 6.dp.toPx()

                drawRoundRect(
                    color = color,
                    topLeft = Offset(currentX, y),
                    size = Size(Math.max(1f, segW - 1.5f), h),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                currentX += segW
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val startTimeStr = if (stagesJSON.isNullOrEmpty()) "23:18" else formatter.format(Date(startTs * 1000))
            val endTimeStr = if (stagesJSON.isNullOrEmpty()) "07:06" else formatter.format(Date(endTs * 1000))
            
            val mid1Str = if (stagesJSON.isNullOrEmpty()) "02:30" else formatter.format(Date((startTs + (endTs - startTs) / 3) * 1000))
            val mid2Str = if (stagesJSON.isNullOrEmpty()) "05:00" else formatter.format(Date((startTs + 2 * (endTs - startTs) / 3) * 1000))

            Text(startTimeStr, color = OWColor.textTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(mid1Str, color = OWColor.textTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(mid2Str, color = OWColor.textTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(endTimeStr, color = OWColor.textTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// 1. TODAY TAB: Hero Recovery Ring, Strain, Last Night Sleep, HRV/RHR
@Composable
fun TodayTabScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var dailyMetrics by remember { mutableStateOf<List<DailyMetric>>(emptyList()) }
    var explanation by remember { mutableStateOf<MetricExplanation?>(null) }
    var selectedExplanationKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val db = WhoopDatabase.getDatabase(context)
        val store = WhoopStore(db)
        val fetched = store.dailyMetrics("my-whoop", "2000-01-01", "2030-01-01")
        dailyMetrics = fetched
        fetched.lastOrNull()?.let { metric ->
            explanation = MetricExplanationFallback.fromDailyMetric("my-whoop", metric)
            ServerSync(context, store, "my-whoop").getDailyExplanation(metric.day)?.let { serverExplanation ->
                explanation = serverExplanation
            }
        }
    }

    val latestMetric = dailyMetrics.lastOrNull()
    val prevMetric = dailyMetrics.getOrNull(dailyMetrics.size - 2)

    val recovery = latestMetric?.recovery.orZero()
    val strain = latestMetric?.strain.orZero()
    val totalSleepMin = latestMetric?.totalSleepMin.orZero()
    val efficiency = latestMetric?.efficiency.orZero()
    val avgHrv = latestMetric?.avgHrv.orZero()
    val restingHr = latestMetric?.restingHr ?: 0
    val respRateBpm = latestMetric?.respRateBpm.orZero()
    val spo2Pct = latestMetric?.spo2Pct.orZero()
    val skinTempDevC = latestMetric?.skinTempDevC.orZero()

    val liveState = WhoopBleService.liveState
    val connected by liveState.connected
    val hr by liveState.heartRate
    val battery by liveState.batteryPct

    fun openExplanation(key: String) {
        selectedExplanationKey = key
        val metric = dailyMetrics.lastOrNull() ?: return
        if (explanation == null || explanation?.date != metric.day) {
            explanation = MetricExplanationFallback.fromDailyMetric("my-whoop", metric)
        }
        coroutineScope.launch {
            val db = WhoopDatabase.getDatabase(context)
            val store = WhoopStore(db)
            ServerSync(context, store, "my-whoop").getDailyExplanation(metric.day)?.let { serverExplanation ->
                explanation = serverExplanation
            }
        }
    }

    selectedExplanationKey?.let { key ->
        CalculationSheet(
            metricKey = key,
            explanation = explanation,
            onDismiss = { selectedExplanationKey = null }
        )
    }

    val animatedPercent = animateFloatAsState(
        targetValue = recovery.toFloat(),
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "recoveryArc"
    )

    fun formatDurationMin(min: Double): String {
        val h = (min / 60).toInt()
        val m = (min % 60).toInt()
        return "${h}h ${m}m"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // SCREEN HEADER
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "Today",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = latestMetric?.day ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    fontSize = 13.sp,
                    color = OWColor.textTertiary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // BLE CONNECTION STRIP
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (connected) {
                    BadgeChip(text = "Strap connected", color = OWColor.green)
                    BadgeChip(text = "${hr ?: "--"} bpm live", color = OWColor.red)
                    BadgeChip(text = "${battery?.toInt() ?: "--"}%", color = OWColor.blue)
                } else {
                    BadgeChip(text = "Strap offline · showing synced data", color = OWColor.textTertiary)
                }
            }
        }

        // HERO RECOVERY RING CARD
        item {
            val rc = OWColor.recoveryColor(recovery)
            val glowModifier = if (recovery >= 0.66) {
                Modifier.border(1.dp, Color(0x332BF07A), RoundedCornerShape(20.dp))
            } else {
                Modifier.border(1.dp, OWColor.line1, RoundedCornerShape(20.dp))
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(glowModifier)
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recovery · ${OWColor.recoveryWord(recovery)}",
                            color = rc,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        )
                        IconButton(
                            onClick = { openExplanation("recovery") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("?", color = rc, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(top = 6.dp)
                    ) {
                        Box(modifier = Modifier.size(196.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Base ring background
                                drawArc(
                                    color = OWColor.bg3,
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round)
                                )
                                // Animated Recovery progress
                                drawArc(
                                    color = rc,
                                    startAngle = -90f,
                                    sweepAngle = animatedPercent.value * 360f,
                                    useCenter = false,
                                    style = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(recovery * 100).toInt()}%",
                                fontSize = 60.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                            )
                            Text(
                                text = "readiness",
                                fontSize = 11.sp,
                                color = OWColor.textTertiary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp
                            )
                        }
                    }

                    // Drivers Grid
                    Spacer(modifier = Modifier.height(18.dp))
                    Divider(color = OWColor.line1, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(
                            Triple("HRV", "${avgHrv.toInt()}", "ms"),
                            Triple("Resting HR", "$restingHr", "bpm"),
                            Triple("Resp", String.format(Locale.US, "%.1f", respRateBpm), "rpm"),
                            Triple("Sleep", formatDurationMin(totalSleepMin), "")
                        ).forEach { (label, value, unit) ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = value,
                                        fontSize = if (label == "Sleep") 15.sp else 21.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                                    )
                                    if (unit.isNotEmpty()) {
                                        Text(
                                            text = " $unit",
                                            fontSize = 11.sp,
                                            color = OWColor.textSecondary,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    color = OWColor.textTertiary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // DAY STRAIN CARD
        item {
            val strainDelta = latestMetric?.strain.orZero() - prevMetric?.strain.orZero()
            val ceiling = if (recovery >= 0.66) 16.0 else if (recovery >= 0.34) 12.5 else 8.0

            Card(
                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OWColor.line1, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "DAY STRAIN",
                                    color = OWColor.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.4.sp
                                )
                                IconButton(onClick = { openExplanation("strain") }, modifier = Modifier.size(16.dp)) {
                                    Text("?", color = OWColor.blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format(Locale.US, "%.1f", strain),
                                color = OWColor.blue,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                            )
                            val trendIndicator = if (strainDelta >= 0) "▲" else "▼"
                            Text(
                                text = "$trendIndicator ${String.format(Locale.US, "%.1f", Math.abs(strainDelta))} vs yesterday",
                                color = OWColor.textTertiary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress Bar with Ceiling Line
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(OWColor.bg3)
                        ) {
                            // Progress
                            val strainFraction = (strain / 21.0).toFloat()
                            val safeStrainFraction = if (strainFraction.isNaN()) 0f else strainFraction.coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = safeStrainFraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(OWColor.blue, OWColor.green)
                                        )
                                    )
                            )
                        }
                        
                        // Ceiling indicator overlay
                        val ceilingFraction = (ceiling / 21.0).toFloat()
                        val safeCeilingFraction = if (ceilingFraction.isNaN()) 0f else ceilingFraction.coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(fraction = safeCeilingFraction)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .offset(x = 1.dp)
                                    .width(2.dp)
                                    .height(20.dp)
                                    .background(Color.White.copy(alpha = 0.5f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0", color = OWColor.textTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text("ceiling ${String.format(Locale.US, "%.1f", ceiling)}", color = OWColor.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("21", color = OWColor.textTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // LAST NIGHT SLEEP CARD
        item {
            val sleepNeedMin = 480.0 // 8 hours default baseline
            val pctOfNeed = ((totalSleepMin / sleepNeedMin) * 100).toInt()

            Card(
                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OWColor.line1, RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "LAST NIGHT",
                                color = OWColor.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp
                            )
                            IconButton(onClick = { openExplanation("sleep") }, modifier = Modifier.size(16.dp)) {
                                Text("?", color = OWColor.blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = formatDurationMin(totalSleepMin),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = OWColor.blue,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$pctOfNeed% of ${formatDurationMin(sleepNeedMin)} needed",
                            fontSize = 12.sp,
                            color = OWColor.textTertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Sleep Efficiency Ring
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(84.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawArc(
                                color = OWColor.bg3,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = OWColor.blue,
                                startAngle = -90f,
                                sweepAngle = (efficiency.toFloat() * 360f),
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(efficiency * 100).toInt()}%",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                            )
                            Text(
                                text = "Effic.",
                                fontSize = 8.sp,
                                color = OWColor.textTertiary
                            )
                        }
                    }
                }
            }
        }

        // SIGNALS GRID TITLE
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECOVERY SIGNALS",
                    color = OWColor.textTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "tap any to explain",
                    color = OWColor.textTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // dense signals grid — every tile explains itself on tap
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val last7 = dailyMetrics.takeLast(7)
                
                val signals = listOf(
                    SignalItem(
                        key = "recovery",
                        label = "HRV",
                        value = "${avgHrv.toInt()}",
                        unit = "ms",
                        c = OWColor.teal,
                        series = last7.map { it.avgHrv ?: 0.0 },
                        delta = avgHrv - (prevMetric?.avgHrv ?: avgHrv),
                        goodUp = true
                    ),
                    SignalItem(
                        key = "recovery",
                        label = "Resting HR",
                        value = "$restingHr",
                        unit = "bpm",
                        c = OWColor.red,
                        series = last7.map { (it.restingHr ?: 0).toDouble() },
                        delta = (restingHr - (prevMetric?.restingHr ?: restingHr)).toDouble(),
                        goodUp = false
                    ),
                    SignalItem(
                        key = "recovery",
                        label = "Resp Rate",
                        value = String.format(Locale.US, "%.1f", respRateBpm),
                        unit = "rpm",
                        c = OWColor.purple,
                        series = last7.map { it.respRateBpm ?: 0.0 },
                        delta = respRateBpm - (prevMetric?.respRateBpm ?: respRateBpm),
                        goodUp = false
                    ),
                    SignalItem(
                        key = "nightly_biometrics",
                        label = "SpO₂",
                        value = String.format(Locale.US, "%.1f", spo2Pct),
                        unit = "%",
                        c = OWColor.teal,
                        series = last7.map { it.spo2Pct ?: 0.0 },
                        delta = spo2Pct - (prevMetric?.spo2Pct ?: spo2Pct),
                        goodUp = true
                    ),
                    SignalItem(
                        key = "nightly_biometrics",
                        label = "Skin Temp",
                        value = "${if (skinTempDevC >= 0) "+" else ""}${String.format(Locale.US, "%.1f", skinTempDevC)}",
                        unit = "°C",
                        c = OWColor.yellow,
                        series = last7.map { it.skinTempDevC ?: 0.0 },
                        delta = skinTempDevC - (prevMetric?.skinTempDevC ?: skinTempDevC),
                        goodUp = null
                    ),
                    SignalItem(
                        key = "sleep",
                        label = "Sleep Effic.",
                        value = "${(efficiency * 100).toInt()}",
                        unit = "%",
                        c = OWColor.blue,
                        series = last7.map { (it.efficiency ?: 0.0) * 100.0 },
                        delta = (efficiency - (prevMetric?.efficiency ?: efficiency)) * 100.0,
                        goodUp = true
                    )
                )

                // Grid layout: 2 columns
                for (rowIndex in 0 until 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        for (colIndex in 0 until 2) {
                            val signal = signals[rowIndex * 2 + colIndex]
                            val isGood = when (signal.goodUp) {
                                true -> signal.delta > 0
                                false -> signal.delta < 0
                                null -> true
                            }
                            val deltaColor = if (signal.delta == 0.0) OWColor.textTertiary else if (isGood) OWColor.greenDim else OWColor.red

                            Card(
                                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, OWColor.line1, RoundedCornerShape(16.dp))
                                    .clickable { openExplanation(signal.key) }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = signal.label.uppercase(Locale.US),
                                        fontSize = 9.5.sp,
                                        color = OWColor.textSecondary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.6.sp
                                    )
                                    Spacer(modifier = Modifier.height(11.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = signal.value,
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                                        )
                                        if (signal.unit.isNotEmpty()) {
                                            Text(
                                                text = " ${signal.unit}",
                                                fontSize = 12.sp,
                                                color = OWColor.textSecondary,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val indicator = if (signal.delta == 0.0) "±" else if (signal.delta > 0) "▲ " else "▼ "
                                        val deltaAbs = String.format(Locale.US, "%.1f", Math.abs(signal.delta)).replace(".0", "")
                                        Text(
                                            text = "$indicator$deltaAbs ${signal.unit}",
                                            color = deltaColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "7d",
                                            color = OWColor.textTertiary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SparklineChart(
                                        points = signal.series,
                                        lineColor = signal.c,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class SignalItem(
    val key: String,
    val label: String,
    val value: String,
    val unit: String,
    val c: Color,
    val series: List<Double>,
    val delta: Double,
    val goodUp: Boolean?
)

@Composable
fun MethodButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CalculationSheet(metricKey: String, explanation: MetricExplanation?, onDismiss: () -> Unit) {
    val title = when (metricKey) {
        "strain" -> "Strain calculation"
        "recovery" -> "Recovery calculation"
        "sleep" -> "Sleep calculation"
        else -> "Metric calculation"
    }
    val metric = explanation?.metrics?.get(metricKey)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.primary)
            }
        },
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (metric == null) {
                    Text(
                        "No daily metric is cached for this calculation yet. Sync server to phone from Device, then reopen this sheet.",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                } else {
                    MetricExplanationCopy.lines(metricKey, metric).forEach { line ->
                        Text(line, color = Color.LightGray, fontSize = 13.sp)
                    }
                    val counts = explanation.dataQuality.streamCounts
                    if (counts.isNotEmpty()) {
                        Text(
                            "Daily data: ${counts["hr"] ?: 0} HR, ${counts["rr"] ?: 0} RR, ${counts["resp"] ?: 0} resp, ${counts["gravity"] ?: 0} movement samples.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF161616)
    )
}

// 2. SLEEP TAB: Sleep efficiency gauge, time asleep progress, stage bars, sleep wellness signals, smart alarm
// 2. SLEEP TAB: Sleep performance score, hypnogram, stages list, wellness signals, Smart Alarm editor
@Composable
fun SleepTabScreen() {
    val context = LocalContext.current
    var dailyMetrics by remember { mutableStateOf<List<DailyMetric>>(emptyList()) }
    var sleepSessions by remember { mutableStateOf<List<CachedSleepSession>>(emptyList()) }

    val prefs = remember { context.getSharedPreferences("whoop_prefs", Context.MODE_PRIVATE) }
    var smartAlarmOn by remember { mutableStateOf(prefs.getBoolean("alarm_on", false)) }
    var alarmHour by remember { mutableStateOf(prefs.getInt("alarm_hour", 7)) }
    var alarmMinute by remember { mutableStateOf(prefs.getInt("alarm_minute", 0)) }
    var alarmWindow by remember { mutableStateOf(prefs.getInt("alarm_window", 30)) }
    val alarmDaysString = prefs.getString("alarm_days", "1,2,3,4,5") ?: ""
    var alarmDays by remember { mutableStateOf(
        if (alarmDaysString.isEmpty()) emptySet<Int>() 
        else alarmDaysString.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    ) }

    android.util.Log.d("MainActivity", "SleepTabScreen recomposing: smartAlarmOn=$smartAlarmOn, hour=$alarmHour, min=$alarmMinute, win=$alarmWindow, days=$alarmDays")

    fun saveAlarmSettings(on: Boolean, hour: Int, minute: Int, window: Int, days: Set<Int>) {
        android.util.Log.d("MainActivity", "saveAlarmSettings: on=$on, hour=$hour, min=$minute, win=$window, days=$days")
        try {
            prefs.edit()
                .putBoolean("alarm_on", on)
                .putInt("alarm_hour", hour)
                .putInt("alarm_minute", minute)
                .putInt("alarm_window", window)
                .putString("alarm_days", days.joinToString(","))
                .apply()
            android.util.Log.d("MainActivity", "saveAlarmSettings: saved to prefs successfully")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "saveAlarmSettings: error writing prefs", e)
        }
        try {
            val service = WhoopBleService.activeInstance
            android.util.Log.d("MainActivity", "saveAlarmSettings: service activeInstance is ${if (service == null) "null" else "non-null"}")
            service?.syncAlarmToStrap()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "saveAlarmSettings: error syncing to service", e)
        }
    }

    LaunchedEffect(Unit) {
        val db = WhoopDatabase.getDatabase(context)
        val store = WhoopStore(db)
        dailyMetrics = store.dailyMetrics("my-whoop", "2000-01-01", "2030-01-01")
        sleepSessions = store.sleepSessions("my-whoop", 0, System.currentTimeMillis() / 1000, 100)
    }

    val latestMetric = dailyMetrics.lastOrNull()
    val efficiency = latestMetric?.efficiency.orZero()
    val totalSleep = latestMetric?.totalSleepMin.orZero()
    val deep = latestMetric?.deepMin.orZero()
    val rem = latestMetric?.remMin.orZero()
    val light = latestMetric?.lightMin.orZero()
    val disturbances = latestMetric?.disturbances ?: 0
    val hrv = latestMetric?.avgHrv.orZero()
    val rhr = latestMetric?.restingHr ?: 0
    val resp = latestMetric?.respRateBpm.orZero()
    val spo2 = latestMetric?.spo2Pct.orZero()
    val tempDev = latestMetric?.skinTempDevC.orZero()

    val latestSession = sleepSessions.lastOrNull()
    val startTs = latestSession?.startTs ?: 0L
    val endTs = latestSession?.endTs ?: 0L
    val stagesJSON = latestSession?.stagesJSON

    val animatedSleepPercent = animateFloatAsState(
        targetValue = efficiency.toFloat(),
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "sleepArc"
    )

    fun formatDurationMin(min: Double): String {
        val h = (min / 60).toInt()
        val m = (min % 60).toInt()
        return "${h}h ${m}m"
    }

    val DAY_LETTERS = listOf("S", "M", "T", "W", "T", "F", "S")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // SCREEN HEADER
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "Sleep",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = latestMetric?.day ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    fontSize = 13.sp,
                    color = OWColor.textTertiary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // TIME ASLEEP HEADLINE CARD
        item {
            val sleepNeedMin = 480.0
            val pctOfNeed = ((totalSleep / sleepNeedMin) * 100).toInt()

            Card(
                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OWColor.line1, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "TIME ASLEEP",
                                    color = OWColor.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.4.sp
                                )
                                IconButton(onClick = { /* openExplanation */ }, modifier = Modifier.size(16.dp)) {
                                    Text("?", color = OWColor.blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = formatDurationMin(totalSleep),
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = OWColor.blue,
                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val timeStr = if (latestSession != null) {
                                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                                "${formatter.format(Date(startTs * 1000))} → ${formatter.format(Date(endTs * 1000))} · "
                            } else {
                                "23:18 → 07:06 · "
                            }

                            Text(
                                text = "$timeStr$pctOfNeed% of need",
                                fontSize = 12.sp,
                                color = OWColor.textTertiary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Progress Ring
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(92.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = OWColor.bg3,
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = OWColor.blue,
                                    startAngle = -90f,
                                    sweepAngle = animatedSleepPercent.value * 360f,
                                    useCenter = false,
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${(efficiency * 100).toInt()}%",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                                )
                                Text(
                                    text = "Effic.",
                                    fontSize = 8.sp,
                                    color = OWColor.textTertiary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    SleepHypnogram(stagesJSON = stagesJSON, startTs = startTs, endTs = endTs)
                }
            }
        }

        // SLEEP STAGES BREAKDOWN CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OWColor.line1, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "SLEEP STAGES",
                        color = OWColor.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    StageBar(
                        label = "Deep (SWS)",
                        duration = formatDurationMin(deep),
                        fraction = if (totalSleep > 0) (deep / totalSleep).toFloat() else 0f,
                        color = OWColor.stageDeep
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    StageBar(
                        label = "REM",
                        duration = formatDurationMin(rem),
                        fraction = if (totalSleep > 0) (rem / totalSleep).toFloat() else 0f,
                        color = OWColor.stageRem
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    StageBar(
                        label = "Light",
                        duration = formatDurationMin(light),
                        fraction = if (totalSleep > 0) (light / totalSleep).toFloat() else 0f,
                        color = OWColor.stageLight
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val awakeMins = disturbances * 3.0
                    StageBar(
                        label = "Awake",
                        duration = "${awakeMins.toInt()}m (${disturbances}×)",
                        fraction = if (totalSleep > 0) (awakeMins / totalSleep).toFloat() else 0f,
                        color = OWColor.stageAwake
                    )
                }
            }
        }

        // IN-SLEEP SIGNALS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OWColor.line1, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "IN-SLEEP SIGNALS",
                        color = OWColor.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val signals = listOf(
                        Triple("Resting HR", "$rhr bpm", OWColor.red),
                        Triple("HRV (average)", "${hrv.toInt()} ms", OWColor.teal),
                        Triple("Resp Rate", String.format(Locale.US, "%.1f rpm", resp), OWColor.purple),
                        Triple("SpO₂ (Average)", String.format(Locale.US, "%.1f%%", spo2), OWColor.teal),
                        Triple("Skin Temp Dev", "${if (tempDev >= 0) "+" else ""}${String.format(Locale.US, "%.1f°C", tempDev)}", OWColor.yellow),
                        Triple("Disturbances", "$disturbances ×", OWColor.textPrimary)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (row in 0 until 2) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (col in 0 until 3) {
                                    val item = signals[row * 3 + col]
                                    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                        Text(item.first, color = OWColor.textTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                                        Spacer(modifier = Modifier.height(7.dp))
                                        Text(item.second, color = item.third, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                                    }
                                }
                            }
                            if (row == 0) {
                                Divider(color = OWColor.line1, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        }

        // 7-NIGHT CHART
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OWColor.line1, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "LAST 7 NIGHTS",
                        color = OWColor.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val last7 = dailyMetrics.takeLast(7)
                    val maxSleep = last7.map { it.totalSleepMin.orZero() }.maxOrNull() ?: 1.0
                    val maxSleepVal = if (maxSleep <= 0.0 || maxSleep.isNaN()) 1.0 else maxSleep

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        last7.forEachIndexed { index, dayMetric ->
                            val sleepMin = dayMetric.totalSleepMin.orZero()
                            val ratioVal = (sleepMin / maxSleepVal).toFloat()
                            val safeRatio = if (ratioVal.isNaN()) 0f else ratioVal.coerceIn(0f, 1f)
                            val isLatest = index == last7.size - 1
                            val barColor = if (isLatest) OWColor.blue else OWColor.blue.copy(alpha = 0.32f)

                            val dayOfWeek = try {
                                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dayMetric.day)
                                val cal = Calendar.getInstance()
                                cal.time = date
                                when (cal.get(Calendar.DAY_OF_WEEK)) {
                                    Calendar.SUNDAY -> "S"
                                    Calendar.MONDAY -> "M"
                                    Calendar.TUESDAY -> "T"
                                    Calendar.WEDNESDAY -> "W"
                                    Calendar.THURSDAY -> "T"
                                    Calendar.FRIDAY -> "F"
                                    Calendar.SATURDAY -> "S"
                                    else -> ""
                                }
                            } catch (e: Exception) {
                                ""
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.Bottom,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1f", sleepMin / 60.0),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OWColor.textSecondary,
                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(safeRatio * 0.7f)
                                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                        .background(barColor)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = dayOfWeek,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OWColor.textTertiary
                                )
                            }
                        }
                    }
                }
            }
        }

        // SMART ALARM PERSISTED PANEL
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OWColor.line1, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Smart Alarm",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Switch(
                            checked = smartAlarmOn,
                            onCheckedChange = {
                                smartAlarmOn = it
                                saveAlarmSettings(it, alarmHour, alarmMinute, alarmWindow, alarmDays)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OWColor.green,
                                checkedTrackColor = OWColor.green.copy(alpha = 0.3f)
                            )
                        )
                    }

                    if (smartAlarmOn) {
                        Spacer(modifier = Modifier.height(18.dp))
                        
                        // Time Stepper Editor
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hour Column
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = {
                                        alarmHour = (alarmHour + 1) % 24
                                        saveAlarmSettings(smartAlarmOn, alarmHour, alarmMinute, alarmWindow, alarmDays)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = OWColor.bg2),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(width = 44.dp, height = 30.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("▲", color = OWColor.textSecondary, fontSize = 12.sp)
                                }
                                Text(
                                    text = String.format("%02d", alarmHour),
                                    fontSize = 46.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                                )
                                Button(
                                    onClick = {
                                        alarmHour = (alarmHour + 23) % 24
                                        saveAlarmSettings(smartAlarmOn, alarmHour, alarmMinute, alarmWindow, alarmDays)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = OWColor.bg2),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(width = 44.dp, height = 30.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("▼", color = OWColor.textSecondary, fontSize = 12.sp)
                                }
                            }

                            Text(
                                text = ":",
                                fontSize = 40.sp,
                                color = OWColor.textTertiary,
                                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 4.dp)
                            )

                            // Minute Column
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = {
                                        alarmMinute = (alarmMinute + 5) % 60
                                        saveAlarmSettings(smartAlarmOn, alarmHour, alarmMinute, alarmWindow, alarmDays)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = OWColor.bg2),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(width = 44.dp, height = 30.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("▲", color = OWColor.textSecondary, fontSize = 12.sp)
                                }
                                Text(
                                    text = String.format("%02d", alarmMinute),
                                    fontSize = 46.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                                )
                                Button(
                                    onClick = {
                                        alarmMinute = (alarmMinute + 55) % 60
                                        saveAlarmSettings(smartAlarmOn, alarmHour, alarmMinute, alarmWindow, alarmDays)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = OWColor.bg2),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(width = 44.dp, height = 30.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("▼", color = OWColor.textSecondary, fontSize = 12.sp)
                                }
                            }
                        }

                        // Smart Wake Window segment buttons
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "SMART WAKE WINDOW",
                            color = OWColor.textTertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(OWColor.bg2, RoundedCornerShape(12.dp))
                                .border(1.dp, OWColor.line1, RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(0 to "Exact", 15 to "15 min", 30 to "30 min").forEach { (win, label) ->
                                val active = alarmWindow == win
                                val bg = if (active) OWColor.green.copy(alpha = 0.13f) else Color.Transparent
                                val bc = if (active) OWColor.green else Color.Transparent
                                val tc = if (active) OWColor.green else OWColor.textTertiary

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bg)
                                        .border(1.dp, bc, RoundedCornerShape(8.dp))
                                        .clickable {
                                            alarmWindow = win
                                            saveAlarmSettings(smartAlarmOn, alarmHour, alarmMinute, alarmWindow, alarmDays)
                                        }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = tc, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Days repeat selection
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        val dayText = when {
                            alarmDays.size == 7 -> "Every day"
                            alarmDays.size == 0 -> "Once"
                            alarmDays.size == 5 && alarmDays.containsAll(listOf(1, 2, 3, 4, 5)) -> "Weekdays"
                            alarmDays.size == 2 && alarmDays.containsAll(listOf(0, 6)) -> "Weekends"
                            else -> alarmDays.sorted().map { DAY_LETTERS[it] }.joinToString(" ")
                        }

                        Text(
                            text = "REPEAT · $dayText",
                            color = OWColor.textTertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            DAY_LETTERS.forEachIndexed { i, ltr ->
                                val active = alarmDays.contains(i)
                                val bg = if (active) OWColor.green.copy(alpha = 0.16f) else OWColor.bg2
                                val bc = if (active) OWColor.green else OWColor.line1
                                val tc = if (active) OWColor.green else OWColor.textTertiary

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(bg)
                                        .border(1.dp, bc, RoundedCornerShape(999.dp))
                                        .clickable {
                                            alarmDays = if (active) alarmDays - i else alarmDays + i
                                            saveAlarmSettings(smartAlarmOn, alarmHour, alarmMinute, alarmWindow, alarmDays)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(ltr, color = tc, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Summary description
                        Spacer(modifier = Modifier.height(18.dp))
                        Divider(color = OWColor.line1, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "🔔",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                            
                            var totalMin = alarmHour * 60 + alarmMinute - alarmWindow
                            totalMin = (totalMin + 1440) % 1440
                            val startTimeStr = String.format("%02d:%02d", totalMin / 60, totalMin % 60)
                            val endTimeStr = String.format("%02d:%02d", alarmHour, alarmMinute)

                            val summaryMsg = if (alarmWindow > 0) {
                                "Wakes you with a silent wrist buzz between $startTimeStr and $endTimeStr, at your lightest sleep."
                            } else {
                                "Silent wrist buzz at exactly $endTimeStr."
                            }

                            Text(
                                text = "$summaryMsg Fires from strap firmware even if the app is closed.",
                                color = OWColor.textTertiary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StageBar(label: String, duration: String, fraction: Float, color: Color) {
    val safeFraction = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = OWColor.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(duration, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = safeFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

// 3. TRENDS TAB: range selector, charts, raw heart rate, days list
private data class MetricDef(
    val color: Color,
    val unit: String,
    val getVal: (DailyMetric) -> Double,
    val format: (Double) -> String
)

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selectedValue: T,
    onOptionSelected: (T) -> Unit,
    accentColor: Color,
    labelMapper: (T) -> String = { it.toString() }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OWColor.bg1)
            .border(1.dp, OWColor.line1, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val active = option == selectedValue
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) accentColor.copy(alpha = 0.13f) else Color.Transparent)
                    .border(1.dp, if (active) accentColor else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onOptionSelected(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelMapper(option),
                    color = if (active) accentColor else OWColor.textTertiary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
    }
}

@Composable
fun TrendsTabScreen() {
    val context = LocalContext.current
    var dailyMetrics by remember { mutableStateOf<List<DailyMetric>>(emptyList()) }
    var selectedRange by remember { mutableStateOf(30) } // 7, 30, 90 days (defaults to 30)
    var selectedMetric by remember { mutableStateOf("Recovery") } // Recovery, Strain, HRV, RHR
    var selectedDay by remember { mutableStateOf<DailyMetric?>(null) }
    var hrSeries by remember { mutableStateOf<List<Double>>(emptyList()) }
    var hrPeak by remember { mutableStateOf<Double?>(null) }

    val metricConfig = remember {
        mapOf(
            "Recovery" to MetricDef(color = OWColor.green, unit = "%", getVal = { (it.recovery.orZero()) * 100.0 }, format = { "${it.toInt()}%" }),
            "Strain" to MetricDef(color = OWColor.blue, unit = "", getVal = { it.strain.orZero() }, format = { String.format(Locale.US, "%.1f", it) }),
            "HRV" to MetricDef(color = OWColor.teal, unit = " ms", getVal = { it.avgHrv.orZero() }, format = { "${it.toInt()} ms" }),
            "RHR" to MetricDef(color = OWColor.red, unit = " bpm", getVal = { (it.restingHr ?: 0).toDouble() }, format = { "${it.toInt()} bpm" })
        )
    }

    LaunchedEffect(Unit) {
        val db = WhoopDatabase.getDatabase(context)
        val store = WhoopStore(db)
        dailyMetrics = store.dailyMetrics("my-whoop", "2000-01-01", "2030-01-01")

        // Fetch 24h HR series
        try {
            val serverSync = ServerSync(context, store, "my-whoop")
            val now = System.currentTimeMillis() / 1000
            val oneDayAgo = now - 24 * 3600
            val series = serverSync.getHRSeries(oneDayAgo, now, 300)
            if (series.isNotEmpty()) {
                hrSeries = series.map { it.value }
                hrPeak = series.maxOfOrNull { it.value }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    val displayMetrics = dailyMetrics.takeLast(selectedRange)
    val M = metricConfig[selectedMetric] ?: metricConfig["Recovery"]!!
    val pts = displayMetrics.map { M.getVal(it) }
    val avg = if (pts.isNotEmpty()) pts.average() else 0.0
    val minVal = if (pts.isNotEmpty()) pts.minOrNull() ?: 0.0 else 0.0
    val maxVal = if (pts.isNotEmpty()) pts.maxOrNull() ?: 0.0 else 0.0
    val latestVal = if (pts.isNotEmpty()) pts.last() else 0.0

    val stats = remember(avg, minVal, maxVal, latestVal, selectedMetric) {
        listOf(
            "Avg" to M.format(avg),
            "Min" to M.format(minVal),
            "Max" to M.format(maxVal),
            "Latest" to M.format(latestVal)
        )
    }

    val diff = maxVal - minVal
    val chartMax = if (selectedMetric == "Recovery") 100.0 else maxVal + (diff * 0.1)
    val chartMin = if (selectedMetric == "Recovery") 0.0 else (minVal - (diff * 0.1)).coerceAtLeast(0.0)

    selectedDay?.let { day ->
        DailyDetailDialog(day = day, onDismiss = { selectedDay = null })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Trends",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }

        // SEGMENTED RANGE SELECTOR
        item {
            SegmentedControl(
                options = listOf(7, 30, 90),
                selectedValue = selectedRange,
                onOptionSelected = { selectedRange = it },
                accentColor = M.color,
                labelMapper = { "${it}D" }
            )
        }

        // METRIC SELECTOR TABS
        item {
            SegmentedControl(
                options = listOf("Recovery", "Strain", "HRV", "RHR"),
                selectedValue = selectedMetric,
                onOptionSelected = { selectedMetric = it },
                accentColor = M.color
            )
        }

        // PREMIUM TREND LINE CHART CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OWColor.line1, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = "${selectedMetric.uppercase(Locale.US)} · $selectedRange DAYS",
                                color = OWColor.textTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = M.format(avg).replace(M.unit, ""),
                                    color = M.color,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                                if (M.unit.isNotEmpty()) {
                                    Text(
                                        text = M.unit,
                                        color = OWColor.textSecondary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "average",
                                color = OWColor.textTertiary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = OWColor.textTertiary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TrendLineChart(
                        points = pts,
                        rangeMax = chartMax,
                        rangeMin = chartMin,
                        lineColor = M.color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp),
                        drawDots = true,
                        fill = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = OWColor.line1, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        stats.forEach { (label, value) ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = label.uppercase(Locale.US),
                                    color = OWColor.textTertiary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = value,
                                    color = OWColor.textPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                            }
                        }
                    }
                }
            }
        }

        // small multiples title
        item {
            Text(
                text = "All metrics · ${selectedRange}d",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // small multiples 2x2 grid
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1: Recovery & Strain
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("Recovery", "Strain").forEach { name ->
                        val mDef = metricConfig[name]!!
                        val mPts = displayMetrics.map { mDef.getVal(it) }
                        val mLatest = if (mPts.isNotEmpty()) mPts.last() else 0.0
                        Card(
                            colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, if (selectedMetric == name) mDef.color else OWColor.line1, RoundedCornerShape(16.dp))
                                .clickable { selectedMetric = name }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        text = name,
                                        color = OWColor.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = mDef.format(mLatest),
                                        color = mDef.color,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        style = TextStyle(fontFeatureSettings = "tnum")
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                SparklineChart(
                                    points = mPts,
                                    lineColor = mDef.color,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                )
                            }
                        }
                    }
                }
                
                // Row 2: HRV & RHR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("HRV", "RHR").forEach { name ->
                        val mDef = metricConfig[name]!!
                        val mPts = displayMetrics.map { mDef.getVal(it) }
                        val mLatest = if (mPts.isNotEmpty()) mPts.last() else 0.0
                        Card(
                            colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, if (selectedMetric == name) mDef.color else OWColor.line1, RoundedCornerShape(16.dp))
                                .clickable { selectedMetric = name }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        text = name,
                                        color = OWColor.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = mDef.format(mLatest),
                                        color = mDef.color,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        style = TextStyle(fontFeatureSettings = "tnum")
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                SparklineChart(
                                    points = mPts,
                                    lineColor = mDef.color,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // rolling HR
        item {
            val displayHrSeries = if (hrSeries.isNotEmpty()) hrSeries else listOf(62.0, 65.0, 68.0, 70.0, 72.0, 85.0, 110.0, 132.0, 120.0, 95.0, 80.0, 72.0, 68.0, 64.0, 60.0, 58.0, 62.0, 65.0, 70.0, 75.0)
            val peakBpm = hrPeak ?: (displayHrSeries.maxOrNull() ?: 132.0)
            Card(
                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OWColor.line1, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Heart Rate · last 24h",
                            color = OWColor.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(OWColor.red.copy(alpha = 0.15f))
                                .border(1.dp, OWColor.red.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "peak ${peakBpm.toInt()}",
                                color = OWColor.red,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    TrendLineChart(
                        points = displayHrSeries,
                        rangeMax = displayHrSeries.maxOrNull() ?: 100.0,
                        rangeMin = displayHrSeries.minOrNull() ?: 50.0,
                        lineColor = OWColor.red,
                        drawDots = false,
                        fill = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                    )
                }
            }
        }

        // CHRONOLOGICAL LIST HEADER
        item {
            Text(
                text = "Daily log",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // SCROLLABLE DAY METRICS LIST (take last 8)
        val dailyLogList = displayMetrics.reversed().take(8)
        items(dailyLogList) { m ->
            DailyHistoryRow(metric = m, onClick = { selectedDay = m })
        }
    }
}

@Composable
fun DailyHistoryRow(metric: DailyMetric, onClick: () -> Unit) {
    val recVal = metric.recovery.orZero()
    val rc = OWColor.recoveryColor(recVal)
    val formattedDate = remember(metric.day) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("EEE, MMM d", Locale.US)
            val date = parser.parse(metric.day)
            if (date != null) formatter.format(date) else metric.day
        } catch (e: Exception) {
            metric.day
        }
    }
    val sleepHours = (metric.totalSleepMin.orZero() / 60).toInt()
    val sleepMins = (metric.totalSleepMin.orZero() % 60).toInt()
    val sleepStr = "${sleepHours}h ${sleepMins}m"

    Card(
        colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OWColor.line1, RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(46.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.06f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx())
                        )
                        drawArc(
                            color = rc,
                            startAngle = -90f,
                            sweepAngle = (recVal * 360f).toFloat(),
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = "${(recVal * 100).toInt()}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }
                Column {
                    Text(
                        text = formattedDate,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "HRV ${metric.avgHrv?.toInt() ?: 0} · RHR ${metric.restingHr ?: 0} · Resp ${metric.respRateBpm?.let { String.format(Locale.US, "%.1f", it) } ?: "--"}",
                        color = OWColor.textSecondary,
                        fontSize = 11.sp,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.US, "%.1f", metric.strain ?: 0.0),
                    color = OWColor.blue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
                Text(
                    text = sleepStr,
                    color = OWColor.textSecondary,
                    fontSize = 11.sp,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
    }
}

@Composable
fun DailyDetailDialog(day: DailyMetric, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = OWColor.green)
            }
        },
        title = { Text(day.day, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Recovery ${day.recovery.orZero().let { (it * 100).toInt() }} / 100", color = Color.White)
                Text("Strain ${String.format(Locale.US, "%.1f", day.strain.orZero())} / 21", color = OWColor.blue)
                Text("Sleep ${(day.totalSleepMin.orZero() / 60).toInt()}h ${(day.totalSleepMin.orZero() % 60).toInt()}m · efficiency ${(day.efficiency.orZero() * 100).toInt()}%", color = Color.LightGray)
                Text("Stages deep ${day.deepMin.orZero().toInt()}m · REM ${day.remMin.orZero().toInt()}m · light ${day.lightMin.orZero().toInt()}m", color = Color.LightGray)
                Text("Night signals SpO2 ${day.spo2Pct?.let { if (it.isNaN()) null else String.format(Locale.US, "%.1f%%", it) } ?: "--"} · skin ${day.skinTempDevC?.let { if (it.isNaN()) null else String.format(Locale.US, "%+.1f C", it) } ?: "--"} · resp ${day.respRateBpm?.let { if (it.isNaN()) null else String.format(Locale.US, "%.1f bpm", it) } ?: "--"}", color = Color.Gray)
            }
        },
        containerColor = OWColor.bg1
    )
}

@Composable
fun TrendLineChart(
    points: List<Double>,
    rangeMax: Double,
    rangeMin: Double,
    lineColor: Color,
    modifier: Modifier = Modifier,
    drawDots: Boolean = true,
    fill: Boolean = true
) {
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
        val width = size.width
        val height = size.height
        val stepX = if (points.size > 1) width / (points.size - 1) else width
        
        val path = Path()
        val fillPath = Path()
        
        points.forEachIndexed { index, value ->
            val x = index * stepX
            val normValue = ((value - rangeMin) / (rangeMax - rangeMin)).coerceIn(0.0, 1.0)
            val y = height - (normValue.toFloat() * height)
            
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (index == points.lastIndex) {
                fillPath.lineTo(x, height)
                fillPath.close()
            }
        }
        
        // Draw grid lines
        val gridLines = 4
        for (g in 0..gridLines) {
            val yGrid = height * g / gridLines
            drawLine(
                color = Color(0x0FFFFFFF),
                start = Offset(0f, yGrid),
                end = Offset(width, yGrid),
                strokeWidth = 1.dp.toPx()
            )
        }
        
        // Draw filled gradient area under the line
        if (fill) {
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.2f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )
        }
        
        // Draw the main line
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Draw points
        if (drawDots) {
            points.forEachIndexed { index, value ->
                val x = index * stepX
                val normValue = ((value - rangeMin) / (rangeMax - rangeMin)).coerceIn(0.0, 1.0)
                val y = height - (normValue.toFloat() * height)
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
                drawCircle(
                    color = Color(0xFF161616),
                    radius = 2.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}

// 4. WORKOUTS TAB: workouts list, exercise statistics
private val kindIcons = mapOf(
    "Running" to "🏃",
    "Strength" to "💪",
    "Cycling" to "🚴",
    "Swimming" to "🏊",
    "Walking" to "🚶"
)

private fun strainColor(s: Double): Color = when {
    s >= 14.0 -> OWColor.yellow
    s >= 10.0 -> OWColor.green
    else -> OWColor.blue
}

@Composable
fun WorkoutsTabScreen() {
    val context = LocalContext.current
    var workouts by remember { mutableStateOf<List<Workout>>(emptyList()) }
    var loadingWorkouts by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loadingWorkouts = true
        try {
            val serverSync = ServerSync(context, WhoopStore(WhoopDatabase.getDatabase(context)), "my-whoop")
            val fetched = serverSync.getWorkouts("2000-01-01", "2030-01-01")
            workouts = fetched
        } catch (e: Exception) {
            workouts = emptyList()
        } finally {
            loadingWorkouts = false
        }
    }

    val totalMin = remember(workouts) {
        workouts.sumOf { it.durationS } / 60
    }
    val totalKcal = remember(workouts) {
        workouts.sumOf { it.caloriesKcal ?: 0.0 }.toInt()
    }
    val avgStrain = remember(workouts) {
        workouts.mapNotNull { it.strain }.average()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Workouts",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "This week",
                    color = OWColor.textTertiary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // WORKOUT WEEK STATS CARD (2x2 grid style)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OWColor.line1, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Row 1: Activities & Avg Strain
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ACTIVITIES",
                                color = OWColor.textTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${workouts.size}",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AVG STRAIN",
                                color = OWColor.textTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (avgStrain.isNaN()) "—" else String.format(Locale.US, "%.1f", avgStrain),
                                color = OWColor.blue,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Row 2: Total Time & Calories
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TOTAL TIME",
                                color = OWColor.textTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${totalMin / 60}h ${totalMin % 60}m",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CALORIES",
                                color = OWColor.textTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (totalKcal > 0) "$totalKcal" else "—",
                                color = OWColor.green,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Auto-detected · tap to expand",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = OWColor.textSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // CHRONOLOGICAL LIST OF WORKOUTS
        if (loadingWorkouts && workouts.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CustomCircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else if (workouts.isEmpty()) {
            item {
                Text("No exercise sessions recorded yet.", color = OWColor.textSecondary, fontSize = 14.sp)
            }
        } else {
            items(workouts) { workout ->
                WorkoutCard(workout)
            }
        }

        item {
            Text(
                text = "Strain is cardiovascular load only until calibrated against WHOOP reference data.",
                color = OWColor.textTertiary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun WorkoutCard(workout: Workout) {
    val sdf = remember { SimpleDateFormat("EEE, MMM d, HH:mm", Locale.US) }
    val startStr = sdf.format(Date(workout.startTs * 1000))
    val minutes = workout.durationS / 60
    var expanded by remember { mutableStateOf(false) }
    val sc = strainColor(workout.strain ?: 0.0)

    val emoji = kindIcons[workout.kind] ?: "🏋️"

    Card(
        colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OWColor.line1, RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(sc.copy(alpha = 0.14f))
                    ) {
                        Text(text = emoji, fontSize = 20.sp)
                    }
                    Column {
                        Text(
                            text = workout.kind ?: "Exercise",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = startStr,
                            color = OWColor.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                
                workout.strain?.let { strVal ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(sc.copy(alpha = 0.15f))
                            .border(1.dp, sc, RoundedCornerShape(999.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f", strVal),
                            color = sc,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // Duration
                Column {
                    Text(
                        text = "DURATION",
                        color = OWColor.textTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "${minutes}m",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }
                
                // Avg HR
                Column {
                    Text(
                        text = "AVG HR",
                        color = OWColor.textTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "${workout.avgHr.toInt()}",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }

                // Calories
                Column {
                    Text(
                        text = "CALORIES",
                        color = OWColor.textTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = workout.caloriesKcal?.let { "${it.toInt()}" } ?: "—",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = OWColor.line1, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "HR ZONE BREAKDOWN",
                    color = OWColor.textTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                val colors = mapOf(
                    0 to OWColor.zone0,
                    1 to OWColor.zone1,
                    2 to OWColor.zone2,
                    3 to OWColor.zone3,
                    4 to OWColor.zone4,
                    5 to OWColor.zone5
                )

                val totalZones = (0..5).sumOf { workout.zoneTimePct[it] ?: 0.0 }
                val normalizer = if (totalZones > 0) totalZones else 1.0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    (0..5).forEach { zone ->
                        val pct = workout.zoneTimePct[zone] ?: 0.0
                        if (pct > 0.0) {
                            Box(
                                modifier = Modifier
                                    .weight((pct / normalizer).toFloat())
                                    .fillMaxHeight()
                                    .background(colors[zone] ?: Color.Gray)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(
                        "Avg %HRR" to (workout.avgHrrPct?.let { "${it.toInt()}%" } ?: "—"),
                        "Peak HR" to "${workout.peakHr}",
                        "HRmax" to (workout.hrmax?.let { "${it.toInt()}" } ?: "—")
                    ).forEach { (label, value) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = label.uppercase(Locale.US),
                                color = OWColor.textTertiary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = value,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getMockWorkouts(): List<Workout> {
    return emptyList()
}

// 5. DEVICE TAB: BLE status chips, live pulse animation, server configuration, commands, logs console
@Composable
fun HardwareRow(label: String, value: String) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = OWColor.textTertiary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
        Divider(color = OWColor.line1, thickness = 1.dp)
    }
}

@Composable
fun StyledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    mono: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(Locale.US),
            color = OWColor.textTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 5.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = if (mono) {
                androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color.White
                )
            } else {
                androidx.compose.ui.text.TextStyle(
                    fontSize = 13.sp,
                    color = Color.White
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = OWColor.bg2,
                unfocusedContainerColor = OWColor.bg2,
                focusedBorderColor = OWColor.line2,
                unfocusedBorderColor = OWColor.line2,
                cursorColor = OWColor.green
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
fun CmdButton(
    label: String,
    kind: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bg, fg, border) = when (kind) {
        "primary" -> Triple(OWColor.green, Color.Black, Color.Transparent)
        "secondary" -> Triple(OWColor.blue, Color.White, Color.Transparent)
        "ghost" -> Triple(OWColor.bg2, Color.White, OWColor.line2)
        "danger" -> Triple(OWColor.red.copy(alpha = 0.16f), OWColor.red, OWColor.red)
        else -> Triple(OWColor.bg2, Color.White, Color.Transparent)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .let { if (border != Color.Transparent) it.border(1.dp, border, RoundedCornerShape(8.dp)) else it }
            .clickable { onClick() }
            .padding(vertical = 11.dp, horizontal = 8.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun DeviceTabScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("whoop_prefs", Context.MODE_PRIVATE)

    var serverUrl by remember { mutableStateOf(prefs.getString("server_url", BuildConfig.DEFAULT_SERVER_URL) ?: BuildConfig.DEFAULT_SERVER_URL) }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", BuildConfig.DEFAULT_API_KEY) ?: BuildConfig.DEFAULT_API_KEY) }

    var storageStatsText by remember { mutableStateOf("Loading...") }

    fun refreshStats() {
        coroutineScope.launch {
            val db = WhoopDatabase.getDatabase(context)
            val store = WhoopStore(db)
            val stats = store.storageStats()
            storageStatsText = "${stats.decodedRows} samples · ${stats.rawBatches} batches · ${stats.rawBytes / 1024} KB"
        }
    }

    LaunchedEffect(Unit) {
        refreshStats()
    }

    val liveState = WhoopBleService.liveState
    val connected by liveState.connected
    val bonded by liveState.bonded
    val hr by liveState.heartRate
    val battery by liveState.batteryPct
    val batteryVal = battery
    val lastFrame by liveState.lastFrameType
    val lastEvent by liveState.lastEvent
    val logs = liveState.log

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Device",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }

        // BLE STATS & CONNECTION STATE CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (connected) OWColor.green.copy(alpha = 0.5f) else OWColor.line1, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WHOOP 4.0 Strap",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BadgeChip(
                                text = if (connected) "Connected" else "Offline",
                                color = if (connected) OWColor.green else OWColor.red
                            )
                            BadgeChip(
                                text = if (bonded) "Bonded" else "Unbonded",
                                color = if (bonded) OWColor.blue else OWColor.yellow
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Pulse HR display
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(150.dp)
                    ) {
                        if (connected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(fraction = pulseScale)
                                    .graphicsLayer { alpha = 1.0f - (pulseScale - 1.0f) * 4f }
                                    .clip(RoundedCornerShape(75.dp))
                                    .border(2.dp, OWColor.green, RoundedCornerShape(75.dp))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            OWColor.green.copy(alpha = 0.16f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = if (connected && hr != null) Modifier.scale(pulseScale) else Modifier
                        ) {
                            Text(
                                text = if (connected) (hr?.toString() ?: "--") else "—",
                                fontSize = 58.sp,
                                fontWeight = FontWeight.Black,
                                color = OWColor.green,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                            Text(
                                text = "Live BPM",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = OWColor.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = OWColor.line1, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf(
                            "Battery" to (if (connected && batteryVal != null) "${batteryVal.toInt()}%" else "—"),
                            "Last Frame" to (if (connected) (lastFrame ?: "REALTIME") else "—"),
                            "Last Event" to (if (connected) (lastEvent ?: "BLE_BONDED") else "—")
                        ).forEach { (label, value) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = label.uppercase(Locale.US),
                                    color = OWColor.textTertiary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = value,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                            }
                        }
                    }

                    // CONNECT / DISCONNECT MANUAL BUTTON
                    Button(
                        onClick = {
                            if (connected) {
                                WhoopBleService.activeInstance?.manualDisconnect()
                            } else {
                                WhoopBleService.activeInstance?.manualConnect()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (connected) OWColor.bg2 else OWColor.green,
                            contentColor = if (connected) Color.White else Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = if (connected) BorderStroke(1.dp, OWColor.line2) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .height(48.dp)
                    ) {
                        Text(
                            text = if (connected) "Disconnect strap" else "Connect & bond strap",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // HARDWARE CONFIGURATION CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OWColor.line1, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "HARDWARE",
                        color = OWColor.textTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    HardwareRow(label = "Serial", value = "WHOOP 4.0 · 4A210...")
                    HardwareRow(label = "Harvard FW", value = "4.10.1.0")
                    HardwareRow(label = "Boylston FW", value = "4.2.0.0")
                    HardwareRow(label = "Local DB", value = storageStatsText)
                }
            }
        }

        // INGEST SERVER CONFIG
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OWColor.line1, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "INGEST SERVER",
                        color = OWColor.textTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    StyledField(
                        label = "Server URL",
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            prefs.edit().putString("server_url", it).apply()
                        }
                    )

                    StyledField(
                        label = "API Bearer Key",
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            prefs.edit().putString("api_key", it).apply()
                        },
                        mono = true
                    )
                }
            }
        }

        // CONTROL COMMANDS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OWColor.bg1),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OWColor.line1, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "CURATED COMMAND PANEL",
                        color = OWColor.textTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CmdButton(
                            label = "Sync Strap → Phone",
                            kind = "primary",
                            onClick = {
                                WhoopBleService.activeInstance?.forceSyncStrap()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CmdButton(
                            label = "Sync Server → Phone",
                            kind = "secondary",
                            onClick = {
                                coroutineScope.launch {
                                    val db = WhoopDatabase.getDatabase(context)
                                    val store = WhoopStore(db)
                                    val sync = ServerSync(context, store, "my-whoop")
                                    sync.pull()
                                    refreshStats()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CmdButton(
                            label = "Test Haptic Buzz",
                            kind = "ghost",
                            onClick = {
                                WhoopBleService.activeInstance?.testAlarmBuzz()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CmdButton(
                            label = "Capture IMU · 30s",
                            kind = "ghost",
                            onClick = {
                                WhoopBleService.activeInstance?.captureRawAccel(30.0)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CmdButton(
                            label = "Disable Alarm",
                            kind = "ghost",
                            onClick = {
                                WhoopBleService.activeInstance?.disableStrapAlarm()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CmdButton(
                            label = "Wipe Local DB",
                            kind = "danger",
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val db = WhoopDatabase.getDatabase(context)
                                        db.clearAllTables()
                                        withContext(Dispatchers.Main) {
                                            refreshStats()
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("MainActivity", "Wipe database failed", e)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // LOG PANEL CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OWColor.line1, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BLE LOG CONSOLE",
                        color = OWColor.green,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Box(modifier = Modifier.height(150.dp)) {
                        LazyColumn(reverseLayout = true) {
                            val logsCopy = logs.toList()
                            items(logsCopy.reversed()) { logLine ->
                                Text(
                                    text = logLine,
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// RENDER HELPERS
@Composable
fun BadgeChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InfoStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MetricCardItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(6.dp)) {
        Text(text = label, color = Color.Gray, fontSize = 10.sp)
        Text(text = value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Sparkline(modifier: Modifier = Modifier, hr: Int) {
    val points = remember { mutableStateListOf<Float>() }
    LaunchedEffect(hr) {
        points.add(hr.toFloat())
        if (points.size > 50) points.removeAt(0)
    }

    Canvas(modifier = modifier.fillMaxWidth()) {
        if (points.size < 2) return@Canvas
        val maxVal = points.maxOrNull() ?: 100f
        val minVal = points.minOrNull() ?: 50f
        val diff = if (maxVal == minVal) 1f else maxVal - minVal
        
        val width = size.width
        val height = size.height
        val step = width / (points.size - 1)

        val path = Path()
        points.forEachIndexed { i, p ->
            val x = i * step
            val y = height - ((p - minVal) / diff * (height * 0.7f) + (height * 0.15f))
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = Color(0xFF00FF66),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun Modifier.scale(scale: Float): Modifier = this.graphicsLayer {
    scaleX = scale
    scaleY = scale
}

fun getMockDailyMetrics(): List<DailyMetric> {
    return emptyList()
}

@Composable
fun CustomCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "loading")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loadingAngle"
    )

    Canvas(modifier = modifier.size(36.dp)) {
        drawArc(
            color = color.copy(alpha = 0.2f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
