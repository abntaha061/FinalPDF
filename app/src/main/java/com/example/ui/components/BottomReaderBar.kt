package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.ui.theme.*

@Composable
fun BottomReaderBar(
    isPageBookmarked: Boolean,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onZoomInClick: () -> Unit,
    onZoomOutClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onShareClick: () -> Unit,
    onGoToPageClick: () -> Unit,
    onPrintClick: () -> Unit,
    onFileInfoClick: () -> Unit,
    onReadingModeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showBrightnessPopup by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    
    val activity = context as? Activity
    var brightness by remember {
        mutableStateOf(
            activity?.window?.let { window ->
                val currentScreenBr = window.attributes.screenBrightness
                if (currentScreenBr in 0.1f..1.0f) {
                    currentScreenBr
                } else {
                    0.7f
                }
            } ?: 0.7f
        )
    }

    // Synchronize activity window brightness when adjusted
    LaunchedEffect(brightness) {
        activity?.window?.let { window ->
            val lp = window.attributes
            lp.screenBrightness = brightness
            window.attributes = lp
        }
    }

    Surface(
        color = AppBottomBarBg,
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Menu button
            BarIconButton(
                icon = Icons.Default.Menu,
                contentDescription = "قائمة المحتويات والرموز",
                onClick = onMenuClick,
                testTag = "reader_menu_button"
            )

            // 2. Search button
            BarIconButton(
                icon = Icons.Default.Search,
                contentDescription = "بحث النص",
                onClick = onSearchClick,
                testTag = "reader_search_button"
            )

            // 3. ZoomIn button
            BarIconButton(
                icon = Icons.Default.ZoomIn,
                contentDescription = "تعديل التكبير",
                onClick = onZoomInClick,
                testTag = "reader_zoomin_button"
            )

            // 4. ZoomOut button
            BarIconButton(
                icon = Icons.Default.ZoomOut,
                contentDescription = "تعديل التصغير",
                onClick = onZoomOutClick,
                testTag = "reader_zoomout_button"
            )

            // 5. Bookmark toggler button
            BarIconButton(
                icon = if (isPageBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                tint = if (isPageBookmarked) AppPrimaryVariant else AppTextPrimary,
                contentDescription = "حفظ الصفحة الإشارية",
                onClick = onBookmarkClick,
                testTag = "reader_bookmark_page_button"
            )

            // 6. Brightness slider popup anchor
            Box(contentAlignment = Alignment.TopCenter) {
                BarIconButton(
                    icon = Icons.Default.BrightnessMedium,
                    contentDescription = "تعديل إضاءة الشاشة",
                    onClick = { showBrightnessPopup = !showBrightnessPopup },
                    testTag = "reader_brightness_button"
                )

                if (showBrightnessPopup) {
                    Popup(
                        onDismissRequest = { showBrightnessPopup = false },
                        properties = PopupProperties(
                            focusable = true,
                            dismissOnClickOutside = true,
                            dismissOnBackPress = true
                        )
                    ) {
                        // Full-screen transparent clickable overlay behind the popup that dismisses it on tap
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    showBrightnessPopup = false
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = AppSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                modifier = Modifier
                                    .padding(bottom = 80.dp) // Offset above the bottom bar
                                    .width(48.dp)
                                    .height(200.dp)
                                    .clickable(enabled = false) {} // Prevent click-through dismissal
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Slider(
                                        value = brightness,
                                        onValueChange = { brightness = it },
                                        valueRange = 0.1f..1.0f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = AppPrimary,
                                            activeTrackColor = AppPrimary,
                                            inactiveTrackColor = AppPrimary.copy(alpha = 0.24f)
                                        ),
                                        modifier = Modifier
                                            .width(180.dp)
                                            .rotate(270f)
                                            .testTag("brightness_slider")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7. Share button
            BarIconButton(
                icon = Icons.Default.Share,
                contentDescription = "مشاركة الملف الحالي",
                onClick = onShareClick,
                testTag = "reader_share_button"
            )

            // 8. More vertically options dropdown
            Box(contentAlignment = Alignment.TopCenter) {
                BarIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "خيارات إضافية",
                    onClick = { showMoreMenu = !showMoreMenu },
                    testTag = "reader_more_options_button"
                )

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    modifier = Modifier.background(AppSurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("وضع القراءة", color = AppTextPrimary, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AppPrimary) },
                        onClick = {
                            showMoreMenu = false
                            onReadingModeClick()
                        },
                        modifier = Modifier.testTag("dropdown_reading_mode_option")
                    )
                    DropdownMenuItem(
                        text = { Text("طباعة الملف", color = AppTextPrimary, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Print, contentDescription = null, tint = AppPrimary) },
                        onClick = {
                            showMoreMenu = false
                            onPrintClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("معلومات الملف", color = AppTextPrimary, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = AppPrimary) },
                        onClick = {
                            showMoreMenu = false
                            onFileInfoClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("الانتقال إلى صفحة", color = AppTextPrimary, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null, tint = AppPrimary) },
                        onClick = {
                            showMoreMenu = false
                            onGoToPageClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppTextPrimary,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .testTag(testTag)
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
