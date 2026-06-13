package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pages
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.ReadingSessionEntity
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: PdfViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sessions by viewModel.allReadingSessions.collectAsState(initial = emptyList())
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Enforce RTL Layout for Arabic
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "إحصائيات القراءة",
                            color = AppTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = AppTextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0D0D0F)
                    )
                )
            },
            containerColor = AppBackground
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // CARD 1: ملخص إجمالي (Summary Card)
                item {
                    SummaryCard(sessions)
                }

                // CARD 2: رسم بياني (Reading Chart)
                item {
                    ReadingChartCard(sessions)
                }

                // CARD 3: أكثر الملفات قراءة
                item {
                    MostReadFilesCard(context, sessions)
                }

                // CARD 4: إحصائيات اليوم
                item {
                    TodayStatsCard(sessions)
                }

                // BUTTON AT BOTTOM: مسح كل الإحصائيات
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = AppTextSecondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "مسح كل الإحصائيات",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllReadingSessions()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("مسح", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("إلغاء", color = AppTextPrimary)
                }
            },
            title = { Text("مسح الإحصائيات", fontWeight = FontWeight.Bold, color = AppTextPrimary) },
            text = { Text("هل أنت متأكد من رغبتك في مسح كافة إحصائيات وسجلات القراءة؟ لا يمكن التراجع عن هذا الإجراء.", color = AppTextSecondary) },
            containerColor = Color(0xFF1E1E24)
        )
    }
}

@Composable
fun SummaryCard(sessions: List<ReadingSessionEntity>) {
    val totalTimeSeconds = sessions.sumOf { it.durationSeconds }
    val uniqueFilesCount = sessions.map { it.fileUri }.distinct().size
    val totalPagesRead = sessions.sumOf { it.pagesRead }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1A1A1F),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "ملخص إجمالي",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    title = "إجمالي الوقت",
                    value = formatDuration(totalTimeSeconds),
                    icon = Icons.Default.Schedule,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    title = "الملفات",
                    value = "$uniqueFilesCount ملف",
                    icon = Icons.Default.MenuBook,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    title = "الصفحات",
                    value = "$totalPagesRead صفحة",
                    icon = Icons.Default.Pages,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatItem(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            color = AppTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AppTextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ReadingChartCard(sessions: List<ReadingSessionEntity>) {
    val primaryColor = AppPrimary.toArgb()
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1A1A1F),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "معدل القراءة اليومي (آخر 7 أيام)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد بيانات قراءة مسجلة بعد",
                        color = AppTextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                val dayFormat = SimpleDateFormat("EEEE", Locale("ar"))
                
                // Get last 7 days calendars (reversed to go chronologically)
                val calBasis = (0..6).map { i ->
                    Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -i)
                    }
                }.reversed()

                val dailyMinutes = calBasis.map { cal ->
                    val daySessions = sessions.filter { s ->
                        val scat = Calendar.getInstance().apply { timeInMillis = s.date }
                        scat.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                                scat.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
                    }
                    val totalSecs = daySessions.sumOf { it.durationSeconds }
                    totalSecs / 60.0f
                }

                val xLabels = calBasis.map { cal ->
                    val dayName = dayFormat.format(cal.time)
                    // Shorten day names to avoid overlap
                    when (dayName) {
                        "الاستراحة" -> "السبت"
                        else -> dayName.substringBefore(" ")
                    }
                }

                val entries = dailyMinutes.mapIndexed { index, mins ->
                    BarEntry(index.toFloat(), mins)
                }

                AndroidView(
                    factory = { ctx ->
                        BarChart(ctx).apply {
                            description.isEnabled = false
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            setDrawGridBackground(false)
                            setDrawBarShadow(false)
                            setDrawValueAboveBar(true)
                            
                            xAxis.apply {
                                position = XAxis.XAxisPosition.BOTTOM
                                setDrawGridLines(false)
                                textColor = android.graphics.Color.GRAY
                                granularity = 1f
                                valueFormatter = IndexAxisValueFormatter(xLabels)
                            }
                            
                            axisRight.isEnabled = false
                            axisLeft.apply {
                                setDrawGridLines(true)
                                gridColor = android.graphics.Color.parseColor("#2A2A2F")
                                textColor = android.graphics.Color.GRAY
                                axisMinimum = 0f
                            }
                            
                            legend.isEnabled = false
                            setScaleEnabled(false)
                            setPinchZoom(false)
                            setDoubleTapToZoomEnabled(false)
                        }
                    },
                    update = { chart ->
                        val dataSet = BarDataSet(entries, "دقائق القراءة").apply {
                            color = primaryColor
                            valueTextColor = android.graphics.Color.WHITE
                            valueTextSize = 10f
                            valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String {
                                    return if (value > 0f) String.format(Locale.getDefault(), "%.1f د", value) else ""
                                }
                            }
                        }
                        chart.data = BarData(dataSet).apply {
                            barWidth = 0.45f
                        }
                        chart.xAxis.valueFormatter = IndexAxisValueFormatter(xLabels)
                        chart.invalidate()
                        chart.animateY(1000)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    }
}

@Composable
fun MostReadFilesCard(context: Context, sessions: List<ReadingSessionEntity>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1A1A1F),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "أكثر الملفات قراءة",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (sessions.isEmpty()) {
                Text(
                    text = "لا توجد بيانات سجلات وملفات بعد",
                    color = AppTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                // Group sessions and take top 5
                val fileStats = sessions.groupBy { it.fileUri }
                    .map { (uri, list) ->
                        val duration = list.sumOf { it.durationSeconds }
                        val name = getFileNameFromUri(context, uri)
                        Triple(uri, name, duration)
                    }
                    .sortedByDescending { it.third }
                    .take(5)

                val maxTime = fileStats.firstOrNull()?.third ?: 1L

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    fileStats.forEachIndexed { index, (uri, name, duration) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rank number circle (RTL left-to-right aligned so it sits on the right of text in RTL)
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(AppPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))

                            // Details
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = name,
                                    color = AppTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "وقت القراءة: ${formatDuration(duration)}",
                                    color = AppTextSecondary,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { duration.toFloat() / maxTime.toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = AppPrimary,
                                    trackColor = Color(0xFF2A2A2F)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TodayStatsCard(sessions: List<ReadingSessionEntity>) {
    val todaySessions = sessions.filter {
        val cal = Calendar.getInstance().apply { timeInMillis = it.date }
        val now = Calendar.getInstance()
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    }

    val todaySeconds = todaySessions.sumOf { it.durationSeconds }
    val todayMinutes = todaySeconds / 60
    val todayPages = todaySessions.sumOf { it.pagesRead }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1A1A1F),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "إحصائيات اليوم",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "قرأت اليوم",
                        fontSize = 12.sp,
                        color = AppTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$todayMinutes دقيقة",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppPrimary
                    )
                }

                Divider(
                    modifier = Modifier
                        .height(40.dp)
                        .width(1.dp)
                        .align(Alignment.CenterVertically),
                    color = Color(0xFF2E2E35)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "صفحات اليوم",
                        fontSize = 12.sp,
                        color = AppTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$todayPages صفحة",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppPrimaryVariant
                    )
                }
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    return when {
         totalSeconds <= 0 -> "0 ثانية"
         totalSeconds < 60 -> "$totalSeconds ثانية"
         totalSeconds < 3600 -> "${totalSeconds / 60} دقيقة"
         else -> {
             val hours = totalSeconds / 3600
             val minutes = (totalSeconds % 3600) / 60
             "$hours س $minutes د"
         }
    }
}

private fun getFileNameFromUri(context: Context, uriString: String): String {
    return try {
        val uri = Uri.parse(uriString)
        var name = "مستند.pdf"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                name = cursor.getString(nameIndex) ?: "مستند.pdf"
            }
        }
        if (name == "مستند.pdf") {
            uri.lastPathSegment?.let { name = it }
        }
        if (!name.lowercase().endsWith(".pdf")) {
            name += ".pdf"
        }
        name
    } catch (e: Exception) {
        val lastSeg = Uri.parse(uriString).lastPathSegment
        if (lastSeg != null) {
            val decoded = Uri.decode(lastSeg)
            if (decoded.lowercase().endsWith(".pdf")) decoded else "$decoded.pdf"
        } else {
            "مستند PDF"
        }
    }
}
