package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.HorizontalPagerIndicator
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext
import com.example.ui.PdfViewModel
import com.example.util.findActivity

@Composable
fun OnboardingScreen(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val viewModel = remember(context) {
        val activity = context.findActivity()
            ?: throw IllegalStateException("Context must be ComponentActivity")
        androidx.lifecycle.ViewModelProvider(activity)[PdfViewModel::class.java]
    }
    
    OnboardingScreen(
        onFinished = {
            viewModel.completeOnboarding()
            navController.navigate(com.example.ui.navigation.Screen.Home.route) {
                popUpTo(com.example.ui.navigation.Screen.Onboarding.route) { inclusive = true }
            }
        }
    )
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState()
    val pageCount = 3

    // Background Gradient (from #0D0D0F to #1A1A2E)
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D0D0F),
            Color(0xFF1A1A2E)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .systemBarsPadding()
    ) {
        HorizontalPager(
            count = pageCount,
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            when (pageIndex) {
                0 -> PageFirst()
                1 -> PageSecond()
                2 -> PageThird(onStartClick = onFinished)
            }
        }

        // Navigation controls at the bottom of the screen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp, start = 24.dp, end = 24.dp)
        ) {
            val currentPage = pagerState.currentPage

            // Skip Button (Bottom-Left) - Arabic "تخطي"
            if (currentPage < pageCount - 1) {
                Text(
                    text = "تخطي",
                    color = AppTextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .clickable { onFinished() }
                        .padding(12.dp)
                        .testTag("onboarding_skip_button")
                )
            }

            // Pager Indicator (Center)
            HorizontalPagerIndicator(
                pagerState = pagerState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                activeColor = AppPrimary,
                inactiveColor = AppTextSecondary,
                indicatorWidth = 12.dp,
                indicatorHeight = 8.dp,
                spacing = 8.dp
            )

            // Next Button (Bottom-Right) - Arabic "التالي"
            if (currentPage < pageCount - 1) {
                Text(
                    text = "التالي",
                    color = AppPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(currentPage + 1)
                            }
                        }
                        .padding(12.dp)
                        .testTag("onboarding_next_button")
                )
            }
        }
    }
}

@Composable
fun PageFirst() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon
        Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = "ملف PDF",
            tint = AppPrimary,
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 32.dp)
                .testTag("onboarding_icon_1")
        )

        // Title
        Text(
            text = "افتح أي ملف PDF",
            color = AppTextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("onboarding_title_1")
        )

        // Subtitle
        Text(
            text = "يدعم الملفات حتى 200 صفحة بجودة عالية وسرعة فائقة",
            color = AppTextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .testTag("onboarding_subtitle_1")
        )
    }
}

@Composable
fun PageSecond() {
    // Pulse animation for the TouchApp icon
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated Pulse Icon
        Icon(
            imageVector = Icons.Default.TouchApp,
            contentDescription = "تفاعل بروابط",
            tint = AppPrimaryVariant,
            modifier = Modifier
                .size(120.dp)
                .scale(iconScale)
                .padding(bottom = 32.dp)
                .testTag("onboarding_icon_2")
        )

        // Title
        Text(
            text = "روابط وصوت تفاعلي",
            color = AppTextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("onboarding_title_2")
        )

        // Subtitle
        Text(
            text = "اضغط على أي رابط مخفي في ملفات تعلم اللغات لسماع النطق الصحيح فوراً",
            color = AppTextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .testTag("onboarding_subtitle_2")
        )
    }
}

@Composable
fun PageThird(
    onStartClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon
        Icon(
            imageVector = Icons.Default.MenuBook,
            contentDescription = "جاهز للقراءة",
            tint = AppPrimary,
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 32.dp)
                .testTag("onboarding_icon_3")
        )

        // Title
        Text(
            text = "جاهز للقراءة!",
            color = AppTextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("onboarding_title_3")
        )

        // Subtitle
        Text(
            text = "خصص تجربتك مع وضع الليل والإشارات المرجعية وأدوات البحث",
            color = AppTextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 48.dp)
                .testTag("onboarding_subtitle_3")
        )

        // Big action button (ابدأ الآن)
        Button(
            onClick = onStartClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppPrimary,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(280.dp)
                .height(56.dp)
                .testTag("onboarding_start_button")
        ) {
            Text(
                text = "ابدأ الآن",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
