package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.ui.AnnotationTool
import com.example.ui.ShapeType

@Composable
fun AnnotationBar(
    selectedTool: AnnotationTool,
    onToolSelect: (AnnotationTool) -> Unit,
    currentColor: Color,
    onColorSelect: (Color) -> Unit,
    strokeWidth: Float,
    onStrokeWidthSelect: (Float) -> Unit,
    onCloseClick: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    canRedo: Boolean,
    onRedo: () -> Unit,
    selectedShapeType: ShapeType,
    onShapeTypeChange: (ShapeType) -> Unit,
    shapeFillEnabled: Boolean,
    onShapeFillToggle: (Boolean) -> Unit,
    onStampClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showColorPickerPopup by remember { mutableStateOf(false) }
    var showStrokeWidthPopup by remember { mutableStateOf(false) }
    var showShapesSubMenu by remember { mutableStateOf(false) }

    val colorsList = listOf(
        Color(0xFFFF3F3F), // Red
        Color(0xFFFF7F3F), // Orange
        Color(0xFFFFD93D), // Yellow
        Color(0xFF6BCB77), // Green
        Color(0xFF4D96FF), // Teal
        Color(0xFF1E90FF), // Blue
        Color(0xFF9B5DE5), // Purple
        Color(0xFFF15BB5), // Pink
        Color(0xFF1E1E1E), // Black
        Color(0xFF888888)  // Gray
    )

    Surface(
        color = Color(0xFF1E1E2E),
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // FIXED COLUMN ON LEFT
            Column(
                modifier = Modifier
                    .wrapContentWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val haptic = LocalHapticFeedback.current

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onUndo()
                    },
                    enabled = canUndo,
                    modifier = Modifier.size(24.dp).testTag("annotation_undo")
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "تراجع",
                        tint = if (canUndo) MaterialTheme.colorScheme.primary else Color(0xFFA0A0B0).copy(0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRedo()
                    },
                    enabled = canRedo,
                    modifier = Modifier.size(24.dp).testTag("annotation_redo")
                ) {
                    Icon(
                        imageVector = Icons.Default.Redo,
                        contentDescription = "إعادة",
                        tint = if (canRedo) MaterialTheme.colorScheme.primary else Color(0xFFA0A0B0).copy(0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color(0xFF323242))
            )

            // HORIZONTALLY SCROLLABLE ROW FOR ANDROID TOOLS
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tool 1: Pen
                AnnotationToolButton(
                    icon = Icons.Default.Create,
                    contentDescription = "قلم رسم",
                    isSelected = selectedTool == AnnotationTool.Pen,
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = Color(0xFFA0A0B0),
                    onClick = { onToolSelect(AnnotationTool.Pen) },
                    testTag = "annotation_tool_pen"
                )

                // Tool 2: Highlighter
                AnnotationToolButton(
                    icon = Icons.Default.BorderColor,
                    contentDescription = "قلم تمييز",
                    isSelected = selectedTool == AnnotationTool.Highlighter,
                    selectedColor = Color(0xFFFFD93D),
                    unselectedColor = Color(0xFFA0A0B0),
                    onClick = { onToolSelect(AnnotationTool.Highlighter) },
                    testTag = "annotation_tool_highlighter"
                )

                // Tool 3: Text Note
                AnnotationToolButton(
                    icon = Icons.Default.TextFields,
                    contentDescription = "ملاحظة نصية",
                    isSelected = selectedTool == AnnotationTool.TextNote,
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = Color(0xFFA0A0B0),
                    onClick = { onToolSelect(AnnotationTool.TextNote) },
                    testTag = "annotation_tool_text_note"
                )

                // Tool 4: Sticky Note
                AnnotationToolButton(
                    icon = Icons.Default.StickyNote2,
                    contentDescription = "ملصق ملاحظة",
                    isSelected = selectedTool == AnnotationTool.StickyNote,
                    selectedColor = Color(0xFFFFD93D),
                    unselectedColor = Color(0xFFA0A0B0),
                    onClick = { onToolSelect(AnnotationTool.StickyNote) },
                    testTag = "annotation_tool_sticky_note"
                )

                // Tool 11 / Shape Tool: Shapes Button with custom sub-menu anchor
                val shapeIcon = when (selectedShapeType) {
                    ShapeType.RECTANGLE -> Icons.Default.CropSquare
                    ShapeType.ELLIPSE -> Icons.Default.Circle
                    ShapeType.LINE -> Icons.Default.Remove
                    ShapeType.ARROW -> Icons.Default.ArrowForward
                }
                Box(contentAlignment = Alignment.TopCenter) {
                    AnnotationToolButton(
                        icon = shapeIcon,
                        contentDescription = "أشكال هندسية",
                        isSelected = selectedTool == AnnotationTool.Shapes,
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = Color(0xFFA0A0B0),
                        onClick = {
                            if (selectedTool == AnnotationTool.Shapes) {
                                showShapesSubMenu = !showShapesSubMenu
                            } else {
                                onToolSelect(AnnotationTool.Shapes)
                                showShapesSubMenu = true
                            }
                            showColorPickerPopup = false
                            showStrokeWidthPopup = false
                        },
                        testTag = "annotation_tool_shapes"
                    )

                    if (showShapesSubMenu && selectedTool == AnnotationTool.Shapes) {
                        Popup(
                            onDismissRequest = { showShapesSubMenu = false },
                            properties = PopupProperties(focusable = true, dismissOnClickOutside = true)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Transparent)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        showShapesSubMenu = false
                                    },
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E3E)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                    modifier = Modifier
                                        .padding(bottom = 76.dp)
                                        .width(220.dp)
                                        .clickable(enabled = false) {}
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "أدوات الأشكال",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceAround
                                        ) {
                                            IconButton(
                                                onClick = { onShapeTypeChange(ShapeType.RECTANGLE) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CropSquare,
                                                    contentDescription = "مستطيل",
                                                    tint = if (selectedShapeType == ShapeType.RECTANGLE) MaterialTheme.colorScheme.primary else Color(0xFFA0A0B0)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onShapeTypeChange(ShapeType.ELLIPSE) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Circle,
                                                    contentDescription = "دائرة",
                                                    tint = if (selectedShapeType == ShapeType.ELLIPSE) MaterialTheme.colorScheme.primary else Color(0xFFA0A0B0)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onShapeTypeChange(ShapeType.LINE) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Remove,
                                                    contentDescription = "خط",
                                                    tint = if (selectedShapeType == ShapeType.LINE) MaterialTheme.colorScheme.primary else Color(0xFFA0A0B0)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onShapeTypeChange(ShapeType.ARROW) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowForward,
                                                    contentDescription = "سهم",
                                                    tint = if (selectedShapeType == ShapeType.ARROW) MaterialTheme.colorScheme.primary else Color(0xFFA0A0B0)
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("تعبئة الشكل", color = Color.White, fontSize = 12.sp)
                                            Switch(
                                                checked = shapeFillEnabled,
                                                onCheckedChange = onShapeFillToggle,
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Tool 12 / Stamp: Stamp Button
                AnnotationToolButton(
                    icon = Icons.Default.Approval,
                    contentDescription = "أختام جاهزة",
                    isSelected = selectedTool == AnnotationTool.Stamp,
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = Color(0xFFA0A0B0),
                    onClick = {
                        onToolSelect(AnnotationTool.Stamp)
                        onStampClick()
                    },
                    testTag = "annotation_tool_stamp"
                )

                // Tool 13 / Comment: Comments button
                AnnotationToolButton(
                    icon = Icons.Default.AddComment,
                    contentDescription = "تعليق ومناقشة",
                    isSelected = selectedTool == AnnotationTool.Comment,
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = Color(0xFFA0A0B0),
                    onClick = { onToolSelect(AnnotationTool.Comment) },
                    testTag = "annotation_tool_comment"
                )

                // Tool 5: Eraser
                AnnotationToolButton(
                    icon = Icons.Default.AutoFixNormal,
                    contentDescription = "ممحاة",
                    isSelected = selectedTool == AnnotationTool.Eraser,
                    selectedColor = Color(0xFFFF6B6B),
                    unselectedColor = Color(0xFFA0A0B0),
                    onClick = { onToolSelect(AnnotationTool.Eraser) },
                    testTag = "annotation_tool_eraser"
                )

                // Tool 6: Color Picker Button
                Box(contentAlignment = Alignment.TopCenter) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(2.dp, Color.White, CircleShape)
                            .clickable {
                                showColorPickerPopup = !showColorPickerPopup
                                showStrokeWidthPopup = false
                                showShapesSubMenu = false
                            }
                            .testTag("annotation_color_preview")
                    )

                    if (showColorPickerPopup) {
                        Popup(
                            onDismissRequest = { showColorPickerPopup = false },
                            properties = PopupProperties(focusable = true, dismissOnClickOutside = true)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Transparent)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        showColorPickerPopup = false
                                    },
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E3E)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                    modifier = Modifier
                                        .padding(bottom = 76.dp)
                                        .width(280.dp)
                                        .height(130.dp)
                                        .clickable(enabled = false) {}
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.SpaceAround
                                    ) {
                                        Text(
                                            text = "اختر لون الخط / التمييز",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                                        ) {
                                            colorsList.take(5).forEach { color ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                        .border(
                                                            2.dp,
                                                            if (currentColor == color) Color.White else Color.Transparent,
                                                            CircleShape
                                                        )
                                                        .clickable {
                                                            onColorSelect(color)
                                                            showColorPickerPopup = false
                                                        }
                                                        .testTag("color_picker_${color.value}")
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                                        ) {
                                            colorsList.drop(5).forEach { color ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                        .border(
                                                            2.dp,
                                                            if (currentColor == color) Color.White else Color.Transparent,
                                                            CircleShape
                                                        )
                                                        .clickable {
                                                            onColorSelect(color)
                                                            showColorPickerPopup = false
                                                        }
                                                        .testTag("color_picker_${color.value}")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Tool 7: Stroke Width Button
                Box(contentAlignment = Alignment.TopCenter) {
                    IconButton(
                        onClick = {
                            showStrokeWidthPopup = !showStrokeWidthPopup
                            showColorPickerPopup = false
                            showShapesSubMenu = false
                        },
                        modifier = Modifier.testTag("annotation_stroke_picker")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LineWeight,
                            contentDescription = "عرض الخط",
                            tint = if (showStrokeWidthPopup) MaterialTheme.colorScheme.primary else Color(0xFFA0A0B0),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (showStrokeWidthPopup) {
                        Popup(
                            onDismissRequest = { showStrokeWidthPopup = false },
                            properties = PopupProperties(focusable = true, dismissOnClickOutside = true)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Transparent)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        showStrokeWidthPopup = false
                                    },
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E3E)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                    modifier = Modifier
                                        .padding(bottom = 76.dp)
                                        .width(260.dp)
                                        .height(110.dp)
                                        .clickable(enabled = false) {}
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.SpaceAround,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "اختر سُمك الخط",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            listOf(
                                                Triple("دقيق", 2f, 4.dp),
                                                Triple("عادي", 4f, 8.dp),
                                                Triple("سميك", 7f, 14.dp),
                                                Triple("عريض", 12f, 24.dp)
                                            ).forEach { (label, width, dotSize) ->
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (strokeWidth == width) Color(0xFF3F3F56) else Color.Transparent)
                                                        .clickable {
                                                            onStrokeWidthSelect(width)
                                                            showStrokeWidthPopup = false
                                                        }
                                                        .padding(6.dp)
                                                        .testTag("stroke_width_$width")
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(dotSize)
                                                                .clip(CircleShape)
                                                                .background(Color.White)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = label,
                                                        color = Color.White,
                                                        fontSize = 10.sp
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

                // Close Button (X)
                IconButton(
                    onClick = onCloseClick,
                    modifier = Modifier.testTag("annotation_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق وضع التعديل",
                        tint = Color(0xFFFFA0A0),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AnnotationToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .testTag(testTag)
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF2E2E3E) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) selectedColor else unselectedColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

