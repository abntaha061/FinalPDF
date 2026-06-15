package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

data class ToolItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val testTag: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    onNavigateToMerge: () -> Unit,
    onNavigateToMultiSearch: () -> Unit,
    onNavigateToPdfToImages: () -> Unit,
    onNavigateToImagesToPdf: () -> Unit,
    onNavigateToPdfToWord: () -> Unit,
    onNavigateToSignature: () -> Unit,
    modifier: Modifier = Modifier
) {
    val toolsList = listOf(
        ToolItem(
            id = "pdf_to_images",
            title = "PDF إلى صور",
            description = "حوّل كل صفحة إلى صورة PNG/JPG",
            icon = Icons.Default.Image,
            iconColor = AppPrimary,
            testTag = "tool_pdf_to_images_card",
            onClick = onNavigateToPdfToImages
        ),
        ToolItem(
            id = "images_to_pdf",
            title = "صور إلى PDF",
            description = "اجمع عدة صور في ملف PDF واحد",
            icon = Icons.Default.PictureAsPdf,
            iconColor = AppPrimaryVariant,
            testTag = "tool_images_to_pdf_card",
            onClick = onNavigateToImagesToPdf
        ),
        ToolItem(
            id = "pdf_to_word",
            title = "PDF إلى Word (تقريبي)",
            description = "استخراج النص لملف Word قابل للتعديل",
            icon = Icons.Default.Description,
            iconColor = AppPrimary,
            testTag = "tool_pdf_to_word_card",
            onClick = onNavigateToPdfToWord
        ),
        ToolItem(
            id = "merge_pdfs",
            title = "دمج ملفات PDF",
            description = "اجمع عدة ملفات PDF في ملف واحد",
            icon = Icons.Default.CallMerge,
            iconColor = AppPrimary,
            testTag = "tool_merge_pdfs_card",
            onClick = onNavigateToMerge
        ),
        ToolItem(
            id = "multi_search",
            title = "البحث متعدد الملفات",
            description = "البحث عن نصوص في أكثر من ملف في نفس الوقت",
            icon = Icons.Default.Search,
            iconColor = AppPrimaryVariant,
            testTag = "tool_multi_search_card",
            onClick = onNavigateToMultiSearch
        ),
        ToolItem(
            id = "signature",
            title = "توقيع PDF",
            description = "رسم وحفظ توقيعك لإدراجه بالمستندات",
            icon = Icons.Default.Gesture,
            iconColor = AppPrimary,
            testTag = "tool_signature_card",
            onClick = onNavigateToSignature
        )
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(AppBackground)
                .padding(16.dp)
        ) {
            // Screen Header
            Text(
                text = "أدوات تحويل وإدارة PDF",
                color = AppTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = "مجموعة من الأدوات المتقدمة لإدارة ملفاتك وتحويلها بسهولة",
                color = AppTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Grid Layout
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(toolsList) { tool ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clickable { tool.onClick() }
                            .testTag(tool.testTag)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(tool.iconColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.title,
                                    tint = tool.iconColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = tool.title,
                                    color = AppTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = tool.description,
                                    color = AppTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
