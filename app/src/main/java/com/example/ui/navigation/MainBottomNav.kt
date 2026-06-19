package com.example.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.example.ui.PdfViewModel

@Composable
fun MainBottomNav(
    navController: NavHostController,
    currentRoute: String?,
    viewModel: PdfViewModel
) {
    val haptic = LocalHapticFeedback.current

    // Observe unread values
    val hasUnreadFiles by viewModel.hasUnreadFiles.collectAsState()
    val hasUnreadCloud by viewModel.hasUnreadCloud.collectAsState()

    // Progressive marker clear
    LaunchedEffect(currentRoute) {
        if (currentRoute == "files") {
            viewModel.markFilesAsRead()
        } else if (currentRoute == "cloud") {
            viewModel.markCloudAsRead()
        }
    }

    val tabs = listOf(
        BottomTabItem(
            route = "home",
            label = "الرئيسية",
            filledIcon = Icons.Filled.Home,
            outlinedIcon = Icons.Outlined.Home,
            testTag = "bottom_tab_home",
            hasBadge = false
        ),
        BottomTabItem(
            route = "files",
            label = "الملفات",
            filledIcon = Icons.Filled.Folder,
            outlinedIcon = Icons.Outlined.FolderOpen,
            testTag = "bottom_tab_files",
            hasBadge = hasUnreadFiles
        ),
        BottomTabItem(
            route = "pdf_tools",
            label = "الأدوات",
            filledIcon = Icons.Filled.Build,
            outlinedIcon = Icons.Outlined.Build,
            testTag = "bottom_tab_tools",
            hasBadge = false
        ),
        BottomTabItem(
            route = "cloud",
            label = "السحابة",
            filledIcon = Icons.Filled.Cloud,
            outlinedIcon = Icons.Outlined.CloudQueue,
            testTag = "bottom_tab_cloud",
            hasBadge = hasUnreadCloud
        )
    )

    Surface(
        shadowElevation = 0.dp,
        color = Color(0xF913131A), // 98% opacity #13131A
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Top border: 1dp solid color #2A2A35
                drawLine(
                    color = Color(0xFF2A2A35),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .navigationBarsPadding()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = currentRoute == tab.route

                // Color Transitions
                val labelColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF6C63FF) else Color(0xFF888899),
                    animationSpec = tween(200),
                    label = "labelColor"
                )

                val pillBgColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF6C63FF).copy(alpha = 0.15f) else Color.Transparent,
                    animationSpec = tween(200),
                    label = "pillBgColor"
                )

                // Spring scale animation for the pill
                val pillScale by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.6f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                    label = "pillScale"
                )

                // Spring bounce for the Icon
                val iconScale = remember { Animatable(1f) }
                LaunchedEffect(isSelected) {
                    if (isSelected) {
                        iconScale.animateTo(
                            targetValue = 1.2f,
                            animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)
                        )
                        iconScale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)
                        )
                    } else {
                        iconScale.snapTo(1f)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 24.dp)
                        ) {
                            // Trigger haptics
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                            if (isSelected) {
                                // Already selected action
                                when (tab.route) {
                                    "home" -> viewModel.triggerScrollToTop()
                                    "files" -> viewModel.triggerNavigateToRoot()
                                }
                            } else {
                                // Navigate to destination
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        .semantics {
                            role = Role.Tab
                        }
                        .testTag(tab.testTag),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon with background pill and badge
                    Box(
                        modifier = Modifier.size(width = 44.dp, height = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Background selected pill behind the icon
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 28.dp)
                                .scale(pillScale)
                                .clip(CircleShape)
                                .background(pillBgColor)
                        )

                        // Icon Graphic
                        Box(
                            modifier = Modifier.scale(iconScale.value)
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                                contentDescription = "${tab.label}, ${if (isSelected) "محدد" else "غير محدد"}",
                                tint = if (isSelected) Color(0xFF6C63FF) else Color(0xFF888899),
                                modifier = Modifier.size(24.dp)
                            )

                            // Unread count badge
                            if (tab.hasBadge) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 8.dp, y = (-4).dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF6B6B))
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        color = labelColor,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

data class BottomTabItem(
    val route: String,
    val label: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val testTag: String,
    val hasBadge: Boolean
)
