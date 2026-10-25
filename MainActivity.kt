package com.example.renkliagandam

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val monthNames = listOf(
    "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
    "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
)
private val monthColors = listOf(
    Color(0xFF2475C9), Color(0xFF9670C8), Color(0xFF45A879),
    Color(0xFFE6A1B9), Color(0xFFE3C34E), Color(0xFFE98C42),
    Color(0xFFD94D55), Color(0xFF42AFC0), Color(0xFF9A7652),
    Color(0xFFD77A38), Color(0xFF687D9D), Color(0xFF527D64)
)
private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
private val timeFormat = SimpleDateFormat("HH:mm", Locale("tr", "TR"))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
        setContent { MaterialTheme { PlannerApp() } }
    }
}

private data class Stroke(val points: List<Offset>)

private fun pageKey(year: Int, month: Int) = "page_${year}_$month"

private fun loadPage(context: Context, year: Int, month: Int): Pair<String, List<String>> {
    val raw = context.getSharedPreferences("planner", Context.MODE_PRIVATE)
        .getString(pageKey(year, month), null) ?: return "" to emptyList()
    return try {
        val obj = JSONObject(raw)
        val stamps = obj.optJSONArray("stamps") ?: JSONArray()
        obj.optString("note") to (0 until stamps.length()).map { stamps.getString(it) }
    } catch (_: Exception) { "" to emptyList() }
}

private fun savePage(context: Context, year: Int, month: Int, note: String, stamps: List<String>) {
    val arr = JSONArray()
    stamps.forEach { arr.put(it) }
    val obj = JSONObject().put("note", note).put("stamps", arr)
    context.getSharedPreferences("planner", Context.MODE_PRIVATE).edit()
        .putString(pageKey(year, month), obj.toString()).apply()
}

@Composable
fun PlannerApp() {
    val context = LocalContext.current
    val now = remember { Calendar.getInstance() }
    var year by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(now.get(Calendar.MONTH)) }
    var note by remember { mutableStateOf("") }
    var stamps by remember { mutableStateOf(emptyList<String>()) }
    var drawingMode by remember { mutableStateOf(false) }
    var strokes by remember { mutableStateOf(listOf<Stroke>()) }
    var currentPoints by remember { mutableStateOf(listOf<Offset>()) }
    var status by remember { mutableStateOf("") }

    fun refresh() {
        val loaded = loadPage(context, year, month)
        note = loaded.first
        stamps = loaded.second
        strokes = emptyList()
        currentPoints = emptyList()
    }
    LaunchedEffect(year, month) { refresh() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF7F7F7)).verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("12 Renkli Ajandam", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = {
                if (month == 0) { month = 11; year-- } else month--
            }) { Text("‹") }
            TextButton(onClick = {
                if (month == 11) { month = 0; year++ } else month++
            }) { Text("›") }
        }
        Text(year.toString(), color = Color.DarkGray)
        Spacer(Modifier.height(10.dp))

        // 12 renkli ay seçimi
        for (row in 0..3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0..2) {
                    val m = row * 3 + col
                    Surface(
                        modifier = Modifier.weight(1f).height(54.dp),
                        color = monthColors[m],
                        shape = RoundedCornerShape(12.dp),
                        onClick = { month = m }
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text(monthNames[m], color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Surface(color = monthColors[month], shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("${monthNames[month].uppercase(Locale("tr", "TR"))} $year", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (stamps.isEmpty()) {
                    Text("Henüz kayıt tarihi yok", color = Color.White.copy(alpha = .9f), fontSize = 12.sp)
                } else {
                    stamps.forEach { Text(it, color = Color.White, fontSize = 13.sp) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Notlarım", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        if (!drawingMode) {
            BasicTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)).padding(14.dp),
                textStyle = TextStyle(fontSize = 17.sp, color = Color(0xFF222222), lineHeight = 25.sp),
                decorationBox = { inner ->
                    if (note.isEmpty()) Text("Notunuzu buraya yazın...", color = Color.Gray)
                    inner()
                }
            )
        } else {
            Canvas(
                Modifier.fillMaxWidth().height(260.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                    .pointerInput(drawingMode) {
                        detectDragGestures(
                            onDragStart = { currentPoints = listOf(it) },
                            onDrag = { change, _ ->
                                currentPoints = currentPoints + change.position
                            },
                            onDragEnd = {
                                if (currentPoints.isNotEmpty()) strokes = strokes + Stroke(currentPoints)
                                currentPoints = emptyList()
                            }
                        )
                    }
            ) {
                (strokes + if (currentPoints.isNotEmpty()) listOf(Stroke(currentPoints)) else emptyList()).forEach { stroke ->
                    if (stroke.points.size > 1) {
                        val path = Path().apply {
                            moveTo(stroke.points.first().x, stroke.points.first().y)
                            stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, Color(0xFF202020), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { drawingMode = !drawingMode; status = if (drawingMode) "Klavye modu" else "Kalem modu" }) {
                Text(if (drawingMode) "Klavye" else "Kalemle yaz")
            }
            OutlinedButton(onClick = { if (strokes.isNotEmpty()) strokes = emptyList() }) { Text("Çizimi temizle") }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val old = loadPage(context, year, month)
                val stamp = "${dateFormat.format(Date())} – ${timeFormat.format(Date())}"
                // Tarih yalnızca içerik gerçekten değiştiğinde eklenir.
                if (note != old.first || strokes.isNotEmpty()) {
                    stamps = old.second + stamp
                    savePage(context, year, month, note, stamps)
                    status = "Kaydedildi: $stamp"
                } else {
                    status = "Notta değişiklik yok."
                }
            }
        ) { Text("Notu kaydet") }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val cal = Calendar.getInstance()
                DatePickerDialog(context, { _, y, m, d ->
                    cal.set(y, m, d)
                    TimePickerDialog(context, { _, h, min ->
                        cal.set(Calendar.HOUR_OF_DAY, h)
                        cal.set(Calendar.MINUTE, min)
                        cal.set(Calendar.SECOND, 0)
                        scheduleReminder(context, cal.timeInMillis, "${dateFormat.format(cal.time)} ${timeFormat.format(cal.time)}")
                        status = "Hatırlatma ayarlandı: ${dateFormat.format(cal.time)} – ${timeFormat.format(cal.time)}"
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        ) { Text("Tarih ve saate hatırlatma kur") }
        if (status.isNotBlank()) Text(status, color = Color(0xFF276749), fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Text("Notlarınız bu telefonda saklanır.", color = Color.Gray, fontSize = 12.sp)
    }
}

private fun scheduleReminder(context: Context, atMillis: Long, message: String) {
    val intent = Intent(context, AlarmReceiver::class.java).putExtra("message", message)
    val pending = PendingIntent.getBroadcast(
        context, atMillis.hashCode(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
    }
    if (atMillis > System.currentTimeMillis()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            alarm.setExact(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }
}
