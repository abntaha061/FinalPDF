package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GestureAction
import com.example.data.GestureType
import com.example.data.defaultGestures
import com.example.ui.PdfViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureSettingsScreen(
    navController: androidx.navigation.NavController,
    viewModel: PdfViewModel
) {
    val context = LocalContext.current
    val gestureMappings by viewModel.gestureMappings.collectAsState()

    var activeGestureToEdit by remember { mutableStateOf<GestureType?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    // Helpers to get user-friendly Arabic titles for each gesture type
    fun getGestureNameAr(type: GestureType): String {
        return when (type) {
            GestureType.SINGLE_TAP -> "نقرة واحدة"
            GestureType.DOUBLE_TAP -> "نقرتان مزدوجتان"
            GestureType.LONG_PRESS -> "ضغط مطول"
            GestureType.SWIPE_LEFT -> "سحب لليسار"
            GestureType.SWIPE_RIGHT -> "سحب لليمين"
            GestureType.SWIPE_UP -> "سحب للأعلى"
            GestureType.SWIPE_DOWN -> "sحب للأسفل"
            GestureType.TWO_FINGER_TAP -> "نقرة بإصبعين"
            GestureType.TWO_FINGER_SWIPE_UP -> "سحب للأعلى بإصبعين"
            GestureType.TWO_FINGER_SWIPE_DOWN -> "سحب للأسفل بإصبعين"
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = Color(0xFF0D0D0F),
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color(0xFF0D0D0F)
                    ),
                    title = {
                        Text(
                            text = "تخصيص الإيماءات",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showResetDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "Reset",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D0D0F))
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                item {
                    Text(
                        text = "خصص كيفية تفاعل القارئ مع حركات أصابعك للاستمتاع بتجربة قراءة أكثر سلاسة وسرعة.",
                        color = AppTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                items(GestureType.values()) { gestureType ->
                    val action = gestureMappings[gestureType] ?: GestureAction.NOTHING
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = AppSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { activeGestureToEdit = gestureType }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = getGestureNameAr(gestureType),
                                    color = AppTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = gestureType.name,
                                    color = AppTextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            ) {
                                Text(
                                    text = action.labelAr,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Dialogue picker for a gesture's mapping action
            if (activeGestureToEdit != null) {
                val currentGesture = activeGestureToEdit!!
                val currentAction = gestureMappings[currentGesture] ?: GestureAction.NOTHING

                AlertDialog(
                    containerColor = AppSurface,
                    onDismissRequest = { activeGestureToEdit = null },
                    title = {
                        Text(
                            text = "اختر إجراء لـ \"${getGestureNameAr(currentGesture)}\"",
                            color = AppTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.Start)
                        )
                    },
                    text = {
                        Box(modifier = Modifier.heightIn(max = 350.dp)) {
                            LazyColumn {
                                items(GestureAction.values()) { action ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val newMappings = gestureMappings.toMutableMap()
                                                newMappings[currentGesture] = action
                                                viewModel.saveGestureMappings(newMappings)
                                                activeGestureToEdit = null
                                            }
                                            .padding(vertical = 12.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        RadioButton(
                                            selected = action == currentAction,
                                            onClick = {
                                                val newMappings = gestureMappings.toMutableMap()
                                                newMappings[currentGesture] = action
                                                viewModel.saveGestureMappings(newMappings)
                                                activeGestureToEdit = null
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = action.labelAr,
                                            color = if (action == currentAction) MaterialTheme.colorScheme.primary else AppTextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = if (action == currentAction) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { activeGestureToEdit = null }) {
                            Text("إلغاء", color = AppTextPrimary)
                        }
                    }
                )
            }

            // Restore defaults dialogue
            if (showResetDialog) {
                AlertDialog(
                    containerColor = AppSurface,
                    onDismissRequest = { showResetDialog = false },
                    title = {
                        Text(
                            text = "إعادة تعيين الافتراضي",
                            color = AppTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "هل أنت متأكد من رغبتك في إعادة تعيين جميع الإيماءات إلى الإعدادات الافتراضية؟",
                            color = AppTextSecondary
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.saveGestureMappings(defaultGestures)
                                showResetDialog = false
                            }
                        ) {
                            Text("نعم، إعادة تعيين", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetDialog = false }) {
                            Text("إلغاء", color = AppTextPrimary)
                        }
                    }
                )
            }
        }
    }
}
