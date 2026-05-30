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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import com.openwhoop.sync.ServerSync
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

// PREMIUM SLEEK DARK THEME
@Composable
fun OpenWhoopAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00FF66), // Neon Green
            secondary = Color(0xFF0099FF), // Electric Blue
            background = Color(0xFF0A0A0A), // Rich Deep Black
            surface = Color(0xFF161616), // Carbon Charcoal
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
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

// 1. TODAY TAB: Hero Recovery Ring, Strain, Last Night Sleep, HRV/RHR
@Composable
fun TodayTabScreen() {
    val context = LocalContext.current
    var dailyMetrics by remember { mutableStateOf<List<DailyMetric>>(emptyList()) }

    LaunchedEffect(Unit) {
        val db = WhoopDatabase.getDatabase(context)
        val store = WhoopStore(db)
        val fetched = store.dailyMetrics("my-whoop", "2000-01-01", "2030-01-01")
        dailyMetrics = fetched
    }

    val latestMetric = dailyMetrics.lastOrNull()
    val recovery = latestMetric?.recovery ?: 0.0
    val strain = latestMetric?.strain ?: 0.0
    val totalSleepMin = latestMetric?.totalSleepMin ?: 0.0
    val efficiency = latestMetric?.efficiency ?: 0.0
    val avgHrv = latestMetric?.avgHrv ?: 0.0
    val restingHr = latestMetric?.restingHr ?: 0

    val animatedPercent = animateFloatAsState(
        targetValue = recovery.toFloat(),
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "recoveryArc"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = latestMetric?.day ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    fontSize = 14.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // HERO RECOVERY PROGRESS ARC
        item {
            val ringColor = when {
                recovery >= 0.66 -> Color(0xFF00FF66)
                recovery >= 0.34 -> Color(0xFFFFCC00)
                else -> Color(0xFFFF3366)
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(220.dp)
                    .padding(12.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Base ring background
                    drawArc(
                        color = Color(0x0FFFFFFF),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Animated Recovery progress
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = animatedPercent.value * 360f,
                        useCenter = false,
                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(recovery * 100).toInt()}%",
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = "RECOVERY",
                        fontSize = 12.sp,
                        color = ringColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }

        // DAY STRAIN CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Day Strain",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f", strain),
                            color = Color(0xFF00FF66),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Custom Linear Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0x0FFFFFFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (strain / 21.0).toFloat().coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF0099FF), Color(0xFF00FF66))
                                    )
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Strain is calculated relative to maximum cardiovascular load.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // LAST NIGHT SLEEP CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Last Night Sleep",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(totalSleepMin / 60).toInt()}h ${(totalSleepMin % 60).toInt()}m asleep",
                            color = Color(0xFF0099FF),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Efficiency", color = Color.Gray, fontSize = 11.sp)
                            Text("${(efficiency * 100).toInt()}%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Disturbances", color = Color.Gray, fontSize = 11.sp)
                            Text("${latestMetric?.disturbances ?: 0} waking events", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // HRV & RHR ROW
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("HRV (Average)", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${avgHrv.toInt()} ms", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Resting HR", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("$restingHr bpm", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

// 2. SLEEP TAB: Sleep efficiency gauge, time asleep progress, stage bars, sleep wellness signals, smart alarm
@Composable
fun SleepTabScreen() {
    val context = LocalContext.current
    var dailyMetrics by remember { mutableStateOf<List<DailyMetric>>(emptyList()) }
    var smartAlarmOn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val db = WhoopDatabase.getDatabase(context)
        val store = WhoopStore(db)
        val fetched = store.dailyMetrics("my-whoop", "2000-01-01", "2030-01-01")
        dailyMetrics = fetched
    }

    val latestMetric = dailyMetrics.lastOrNull()
    val efficiency = latestMetric?.efficiency ?: 0.0
    val totalSleep = latestMetric?.totalSleepMin ?: 0.0
    val deep = latestMetric?.deepMin ?: 0.0
    val rem = latestMetric?.remMin ?: 0.0
    val light = latestMetric?.lightMin ?: 0.0
    val disturbances = latestMetric?.disturbances ?: 0
    val hrv = latestMetric?.avgHrv ?: 0.0
    val rhr = latestMetric?.restingHr ?: 0
    val resp = latestMetric?.respRateBpm ?: 0.0
    val spo2 = latestMetric?.spo2Pct ?: 0.0
    val tempDev = latestMetric?.skinTempDevC ?: 0.0

    val animatedSleepPercent = animateFloatAsState(
        targetValue = efficiency.toFloat(),
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "sleepArc"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Sleep",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }

        // HERO SLEEP EFFICIENCY PROGRESS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("Sleep Performance", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Your sleep was highly efficient with few disturbances.", fontSize = 14.sp, color = Color.LightGray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${(totalSleep / 60).toInt()}h ${(totalSleep % 60).toInt()}m asleep",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0099FF)
                        )
                    }

                    // Progress Ring
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(90.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawArc(
                                color = Color(0x0FFFFFFF),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = Color(0xFF0099FF),
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
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "Efficiency",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        // SLEEP STAGES BREAKDOWN
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Sleep Stages Breakdown", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    StageBar(
                        label = "Deep Sleep (SWS)",
                        duration = "${(deep / 60).toInt()}h ${(deep % 60).toInt()}m",
                        fraction = (deep / totalSleep).toFloat(),
                        color = Color(0xFF0033CC)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    StageBar(
                        label = "REM Sleep",
                        duration = "${(rem / 60).toInt()}h ${(rem % 60).toInt()}m",
                        fraction = (rem / totalSleep).toFloat(),
                        color = Color(0xFF9933FF)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    StageBar(
                        label = "Light Sleep",
                        duration = "${(light / 60).toInt()}h ${(light % 60).toInt()}m",
                        fraction = (light / totalSleep).toFloat(),
                        color = Color(0xFF0099FF)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val awakeMins = disturbances * 3.0
                    StageBar(
                        label = "Awake / Disturbances",
                        duration = "${awakeMins.toInt()} mins (${disturbances}x)",
                        fraction = (awakeMins / totalSleep).toFloat().coerceIn(0f, 0.2f),
                        color = Color(0xFFFF3366)
                    )
                }
            }
        }

        // IN-SLEEP WELLNESS SIGNALS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("In-Sleep Wellness Signals", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricCardItem(label = "Resting HR", value = "$rhr bpm", modifier = Modifier.weight(1f))
                        MetricCardItem(label = "HRV (average)", value = "${hrv.toInt()} ms", modifier = Modifier.weight(1f))
                        MetricCardItem(label = "Resp Rate", value = "${String.format(Locale.US, "%.1f", resp)} rpm", modifier = Modifier.weight(1f))
                    }
                    Divider(color = Color(0x1AFFFFFF), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricCardItem(label = "SpO2 (Average)", value = "${String.format(Locale.US, "%.1f", spo2)}%", modifier = Modifier.weight(1f))
                        MetricCardItem(label = "Skin Temp Dev", value = "${if (tempDev >= 0) "+" else ""}${String.format(Locale.US, "%.1f", tempDev)}°C", modifier = Modifier.weight(1f))
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // SMART ALARM TOGGLE
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text("Strap Smart Alarm", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Wake up silently with a gentle wrist vibration at your sleep goal floor.", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = smartAlarmOn,
                        onCheckedChange = {
                            smartAlarmOn = it
                            if (it) {
                                WhoopBleService.activeInstance?.testAlarmBuzz()
                            } else {
                                WhoopBleService.activeInstance?.disableStrapAlarm()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FF66),
                            checkedTrackColor = Color(0x5500FF66)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun StageBar(label: String, duration: String, fraction: Float, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.LightGray, fontSize = 12.sp)
            Text(duration, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x0FFFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

// 3. TRENDS TAB: range selector, charts, raw heart rate, days list
@Composable
fun TrendsTabScreen() {
    val context = LocalContext.current
    var dailyMetrics by remember { mutableStateOf<List<DailyMetric>>(emptyList()) }
    var selectedRange by remember { mutableStateOf(7) } // 7, 30, 90 days
    var selectedMetric by remember { mutableStateOf("Recovery") } // Recovery, Strain, HRV, RHR

    LaunchedEffect(Unit) {
        val db = WhoopDatabase.getDatabase(context)
        val store = WhoopStore(db)
        val fetched = store.dailyMetrics("my-whoop", "2000-01-01", "2030-01-01")
        dailyMetrics = fetched
    }

    val displayMetrics = dailyMetrics.takeLast(selectedRange)

    val chartPoints = when (selectedMetric) {
        "Recovery" -> displayMetrics.map { (it.recovery ?: 0.5) * 100.0 }
        "Strain" -> displayMetrics.map { it.strain ?: 10.0 }
        "HRV" -> displayMetrics.map { it.avgHrv ?: 60.0 }
        "RHR" -> displayMetrics.map { (it.restingHr ?: 55).toDouble() }
        else -> displayMetrics.map { (it.recovery ?: 0.5) * 100.0 }
    }

    val maxVal = if (chartPoints.isNotEmpty()) chartPoints.maxOrNull() ?: 100.0 else 100.0
    val minVal = if (chartPoints.isNotEmpty()) chartPoints.minOrNull() ?: 0.0 else 0.0
    val diff = maxVal - minVal
    val chartMax = if (selectedMetric == "Recovery") 100.0 else maxVal + (diff * 0.1)
    val chartMin = if (selectedMetric == "Recovery") 0.0 else (minVal - (diff * 0.1)).coerceAtLeast(0.0)

    val chartColor = when (selectedMetric) {
        "Recovery" -> Color(0xFF00FF66)
        "Strain" -> Color(0xFFFFCC00)
        "HRV" -> Color(0xFF0099FF)
        "RHR" -> Color(0xFFFF3366)
        else -> Color(0xFF00FF66)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(7, 30, 90).forEach { range ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedRange == range) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                            .border(1.dp, if (selectedRange == range) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { selectedRange = range }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${range}D",
                            color = if (selectedRange == range) MaterialTheme.colorScheme.primary else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // METRIC SELECTOR TABS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Recovery", "Strain", "HRV", "RHR").forEach { m ->
                    val active = selectedMetric == m
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) chartColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (active) chartColor else Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                            .clickable { selectedMetric = m }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = m,
                            color = if (active) Color.White else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // PREMIUM TREND LINE CHART CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Historical $selectedMetric", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val avg = if (chartPoints.isNotEmpty()) chartPoints.average() else 0.0
                            val avgUnit = when (selectedMetric) {
                                "Recovery" -> "%"
                                "Strain" -> ""
                                "HRV" -> " ms"
                                "RHR" -> " bpm"
                                else -> ""
                            }
                            Text(
                                text = "Avg: ${String.format(Locale.US, "%.1f", avg)}$avgUnit",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TrendLineChart(
                        points = chartPoints,
                        rangeMax = chartMax,
                        rangeMin = chartMin,
                        lineColor = chartColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }

        // ROLLING HR STREAM CHART
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Rolling Pulse Rate (Live HR Spark)",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    val liveHR = WhoopBleService.liveState.heartRate.value ?: 70
                    Sparkline(modifier = Modifier.height(80.dp), hr = liveHR)
                }
            }
        }

        // CHRONOLOGICAL LIST HEADER
        item {
            Text(
                text = "Chronological Days",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // SCROLLABLE DAY METRICS LIST
        items(displayMetrics.reversed()) { m ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val recVal = m.recovery ?: 0.5
                        val ringColor = when {
                            recVal >= 0.66 -> Color(0xFF00FF66)
                            recVal >= 0.34 -> Color(0xFFFFCC00)
                            else -> Color(0xFFFF3366)
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(45.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = Color(0x0FFFFFFF),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 4.dp.toPx())
                                )
                                drawArc(
                                    color = ringColor,
                                    startAngle = -90f,
                                    sweepAngle = (recVal * 360f).toFloat(),
                                    useCenter = false,
                                    style = Stroke(width = 4.dp.toPx())
                                )
                            }
                            Text(
                                text = "${(recVal * 100).toInt()}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Column {
                            Text(text = m.day, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(text = "HRV: ${m.avgHrv?.toInt() ?: 60} ms | RHR: ${m.restingHr ?: 55} bpm", color = Color.Gray, fontSize = 11.sp)
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${m.strain ?: 0.0} Strain",
                            color = Color(0xFFFFCC00),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${((m.totalSleepMin ?: 450.0) / 60).toInt()}h ${((m.totalSleepMin ?: 450.0) % 60).toInt()}m sleep",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrendLineChart(
    points: List<Double>,
    rangeMax: Double,
    rangeMin: Double,
    lineColor: Color,
    modifier: Modifier = Modifier
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
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.2f), Color.Transparent),
                startY = 0f,
                endY = height
            )
        )
        
        // Draw the main line
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Draw points
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

// 4. WORKOUTS TAB: workouts list, exercise statistics
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Workouts",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }

        // WORKOUT WEEK STATS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Weekly Workouts", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${workouts.size} activities recorded", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Avg Strain", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        val avgStrain = workouts.mapNotNull { it.strain }.average()
                        Text(
                            text = if (avgStrain.isNaN()) "--" else String.format(Locale.US, "%.1f", avgStrain),
                            fontSize = 18.sp,
                            color = Color(0xFF00FF66),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
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
                Text("No exercise sessions recorded yet.", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            items(workouts) { workout ->
                WorkoutCard(workout)
            }
        }
    }
}

@Composable
fun WorkoutCard(workout: Workout) {
    val sdf = SimpleDateFormat("EEE, MMM d, HH:mm", Locale.getDefault())
    val startStr = sdf.format(Date(workout.startTs * 1000))
    val minutes = workout.durationS / 60

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.5f)) {
                Text(
                    text = workout.kind ?: "Exercise",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = startStr, color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Duration", color = Color.Gray, fontSize = 10.sp)
                        Text("${minutes} mins", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Avg HR", color = Color.Gray, fontSize = 10.sp)
                        Text("${workout.avgHr.toInt()} bpm", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Calories", color = Color.Gray, fontSize = 10.sp)
                        Text(workout.caloriesKcal?.let { "${it.toInt()} kcal" } ?: "--", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            workout.strain?.let { strVal ->
                val badgeColor = when {
                    strVal >= 14.0 -> Color(0xFFFFCC00)
                    strVal >= 10.0 -> Color(0xFF00FF66)
                    else -> Color(0xFF0099FF)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .border(1.dp, badgeColor, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f", strVal),
                        color = badgeColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
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
            storageStatsText = "${stats.decodedRows} decoded samples, ${stats.rawBatches} raw batches (${stats.rawBytes / 1024} KB)"
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
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
                            text = "Strap Connection",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BadgeChip(
                                text = if (connected) "Connected" else "Disconnected",
                                color = if (connected) Color(0xFF00FF66) else Color(0xFFFF3366)
                            )
                            BadgeChip(
                                text = if (bonded) "Bonded" else "Unbonded",
                                color = if (bonded) Color(0xFF0099FF) else Color(0xFFFFCC00)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Pulse HR display
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(160.dp)
                    ) {
                        if (connected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(fraction = pulseScale)
                                    .graphicsLayer { alpha = 1.0f - (pulseScale - 1.0f) * 4f }
                                    .clip(RoundedCornerShape(80.dp))
                                    .border(2.dp, Color(0xFF00FF66), RoundedCornerShape(80.dp))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0x1A00FF66),
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
                                text = hr?.toString() ?: "--",
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "LIVE BPM",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InfoStat(label = "Battery", value = battery?.let { "${it.toInt()}%" } ?: "--")
                        InfoStat(label = "Last Frame", value = lastFrame ?: "--")
                        InfoStat(label = "Last Event", value = lastEvent ?: "--")
                    }
                }
            }
        }

        // HARDWARE CONFIGURATION CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Hardware Configuration", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    Text("Serial: WHOOP 4.0 Strap", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Harvard FW: 4.10.1.0", color = Color.LightGray, fontSize = 12.sp)
                    Text("Boylston FW: 4.2.0.0", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Local Database Footprint:", color = Color.Gray, fontSize = 11.sp)
                    Text(storageStatsText, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // INGEST SERVER CONFIG
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ingest Server Configuration", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            prefs.edit().putString("server_url", it).apply()
                        },
                        label = { Text("Server URL") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            prefs.edit().putString("api_key", it).apply()
                        },
                        label = { Text("API Bearer Key") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // CONTROL COMMANDS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Curated Command Panel", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                WhoopBleService.activeInstance?.forceSyncStrap()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sync Strap to Phone", color = Color.Black)
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val db = WhoopDatabase.getDatabase(context)
                                    val store = WhoopStore(db)
                                    val sync = ServerSync(context, store, "my-whoop")
                                    sync.pull()
                                    refreshStats()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sync Server to Phone", color = Color.White)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                WhoopBleService.activeInstance?.testAlarmBuzz()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Test Haptic Buzz", color = Color.White)
                        }
                        Button(
                            onClick = {
                                WhoopBleService.activeInstance?.disableStrapAlarm()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x55FF3366)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Disable Alarm", color = Color.White)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                WhoopBleService.activeInstance?.captureRawAccel(30.0)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Capture IMU (30s)", color = Color.White, fontSize = 12.sp)
                        }
                        Button(
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Wipe Local DB", color = Color.White)
                        }
                    }
                }
            }
        }

        // LOG PANEL CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BLE LOG CONSOLE",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
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
