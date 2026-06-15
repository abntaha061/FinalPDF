package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScreen(
    navController: NavController,
    viewModel: PdfViewModel = viewModel(
        LocalContext.current as androidx.activity.ComponentActivity
    )
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("رسم", "نص", "صورة")

    // Pen Stroke Preferences
    var selectedColor by remember { mutableStateOf(Color.Black) }
    val colorsList = listOf(
        Color.Black,
        Color(0xFF1A127E), // Blue
        Color(0xFFB71C1C)  // Red
    )
    var strokeWidthValue by remember { mutableStateOf(8f) } // Default medium thickness

    // Signature data references
    var drawPaths by remember { mutableStateOf(listOf<Pair<Path, Color>>()) }
    var currentPath by remember { mutableStateOf(Path()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Typed Signature State
    var typedName by remember { mutableStateOf("") }
    var selectedFontIndex by remember { mutableStateOf(0) }

    // Setup Compose Google Fonts
    val fontProvider = remember {
        GoogleFont.Provider(
            providerAuthority = "com.google.android.gms.fonts",
            providerPackage = "com.google.android.gms",
            certificates = R.array.com_google_android_gms_fonts_certs
        )
    }

    val fontOptions = remember {
        listOf(
            Pair("Dancing Script", FontFamily(Font(googleFont = GoogleFont("Dancing Script"), fontProvider = fontProvider))),
            Pair("Pacifico", FontFamily(Font(googleFont = GoogleFont("Pacifico"), fontProvider = fontProvider))),
            Pair("Sacramento", FontFamily(Font(googleFont = GoogleFont("Sacramento"), fontProvider = fontProvider))),
            Pair("Great Vibes", FontFamily(Font(googleFont = GoogleFont("Great Vibes"), fontProvider = fontProvider))),
            Pair("Allura", FontFamily(Font(googleFont = GoogleFont("Allura"), fontProvider = fontProvider)))
        )
    }

    // Image Signature State
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropFactor by remember { mutableStateOf(1.0f) } // 0.5f to 1.0f slider

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            imageUri = uri
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                rawBitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "التوقيع الرقمي",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Clear signature canvas
                    TextButton(
                        onClick = {
                            if (selectedTab == 0) {
                                drawPaths = emptyList()
                                currentPath = Path()
                            } else if (selectedTab == 1) {
                                typedName = ""
                            } else {
                                imageUri = null
                                rawBitmap = null
                                cropFactor = 1.0f
                            }
                        },
                        modifier = Modifier.testTag("signature_clear_button")
                    ) {
                        Text("مسح", color = Color.Red, fontWeight = FontWeight.Bold)
                    }

                    // Save signature Button
                    TextButton(
                        onClick = {
                            // Perform signature generation based on active tab
                            var finalBmp: Bitmap? = null
                            try {
                                when (selectedTab) {
                                    0 -> { // DRAW
                                        if (drawPaths.isEmpty()) {
                                            Toast.makeText(context, "الرجاء رسم التوقيع أولاً", Toast.LENGTH_SHORT).show()
                                            return@TextButton
                                        }
                                        val width = if (canvasSize.width > 0) canvasSize.width else 800
                                        val height = if (canvasSize.height > 0) canvasSize.height else 400
                                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                        val canvas = android.graphics.Canvas(bmp)
                                        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

                                        drawPaths.forEach { (path, color) ->
                                            val paint = android.graphics.Paint().apply {
                                                this.color = color.toArgb()
                                                style = android.graphics.Paint.Style.STROKE
                                                strokeWidth = strokeWidthValue
                                                strokeCap = android.graphics.Paint.Cap.ROUND
                                                strokeJoin = android.graphics.Paint.Join.ROUND
                                                isAntiAlias = true
                                            }
                                            canvas.drawPath(path.asAndroidPath(), paint)
                                        }
                                        finalBmp = bmp
                                    }
                                    1 -> { // TEXT
                                        if (typedName.trim().isEmpty()) {
                                            Toast.makeText(context, "الرجاء كتابة اسمك أولاً", Toast.LENGTH_SHORT).show()
                                            return@TextButton
                                        }
                                        val width = 800
                                        val height = 300
                                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                        val canvas = android.graphics.Canvas(bmp)
                                        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

                                        val systemFontName = fontOptions[selectedFontIndex].first
                                        val typeface = try {
                                            Typeface.create(systemFontName, Typeface.ITALIC)
                                        } catch (e: Exception) {
                                            Typeface.create("serif", Typeface.ITALIC)
                                        }

                                        val paint = android.graphics.Paint().apply {
                                            this.color = selectedColor.toArgb()
                                            textSize = 80f
                                            isAntiAlias = true
                                            textAlign = android.graphics.Paint.Align.CENTER
                                            setTypeface(typeface)
                                        }
                                        // Draw centered text
                                        val xPos = (canvas.width / 2).toFloat()
                                        val yPos = ((canvas.height / 2) - ((paint.descent() + paint.ascent()) / 2))
                                        canvas.drawText(typedName, xPos, yPos, paint)
                                        finalBmp = bmp
                                    }
                                    2 -> { // IMAGE
                                        val raw = rawBitmap
                                        if (raw == null) {
                                            Toast.makeText(context, "الرجاء اختيار صورة أولاً", Toast.LENGTH_SHORT).show()
                                            return@TextButton
                                        }
                                        // Apply cropping factor
                                        val cx = raw.width / 2
                                        val cy = raw.height / 2
                                        val diameterX = (raw.width * cropFactor).toInt().coerceIn(10, raw.width)
                                        val diameterY = (raw.height * cropFactor).toInt().coerceIn(10, raw.height)
                                        
                                        val startX = (cx - diameterX / 2).coerceIn(0, raw.width - 10)
                                        val startY = (cy - diameterY / 2).coerceIn(0, raw.height - 10)
                                        
                                        finalBmp = Bitmap.createBitmap(raw, startX, startY, diameterX, diameterY)
                                    }
                                }

                                finalBmp?.let { bmp ->
                                    val sigFile = File(context.filesDir, "signature_${System.currentTimeMillis()}.png")
                                    val outStream = FileOutputStream(sigFile)
                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                                    outStream.flush()
                                    outStream.close()

                                    // Save file path to SharedPreferences for reuse
                                    val prefs = context.getSharedPreferences("PdfPrefs", Context.MODE_PRIVATE)
                                    prefs.edit().putString("saved_signature_path", sigFile.absolutePath).apply()

                                    // Store signature inside ViewModel state
                                    viewModel.setSavedSignaturePath(sigFile.absolutePath)

                                    Toast.makeText(context, "تم حفظ التوقيع الرقمي بنجاح ✓", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "خطأ أثناء حفظ التوقيع: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("signature_save_button")
                    ) {
                        Text("حفظ", color = AppPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = AppBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TABS SELECTOR
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = AppSurface,
                contentColor = AppPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .padding(bottom = 16.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // TAB 1: DRAW SIGNATURE
            if (selectedTab == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .background(Color.White, shape = RoundedCornerShape(12.dp))
                        .border(1.dp, AppTextSecondary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPath = Path()
                                        currentPath.moveTo(offset.x, offset.y)
                                    },
                                    onDrag = { change, _ ->
                                        currentPath.lineTo(change.position.x, change.position.y)
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        drawPaths = drawPaths + (currentPath to selectedColor)
                                        currentPath = Path()
                                    }
                                )
                            }
                    ) {
                        if (canvasSize == IntSize.Zero) {
                            canvasSize = IntSize(size.width.toInt(), size.height.toInt())
                        }
                        
                        // Draw saved strokes
                        drawPaths.forEach { (path, color) ->
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(
                                    width = strokeWidthValue,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        // Draw current active stroke
                        drawPath(
                            path = currentPath,
                            color = selectedColor,
                            style = Stroke(
                                width = strokeWidthValue,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CONTROLS FOR DRAWING TAB
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Undo last stroke Button
                    Button(
                        onClick = {
                            if (drawPaths.isNotEmpty()) {
                                drawPaths = drawPaths.dropLast(1)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppSurface),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Undo, contentDescription = "تراجع", tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تراجع", color = Color.White)
                    }

                    // Stroke Width Selectors
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(4f, 8f, 16f).forEachIndexed { index, size ->
                            val label = when (index) {
                                0 -> "رقيق"
                                1 -> "متوسط"
                                else -> "سميك"
                            }
                            FilterChip(
                                selected = strokeWidthValue == size,
                                onClick = { strokeWidthValue = size },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // PEN COLOR SELECTOR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colorsList.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(color, CircleShape)
                                .border(
                                    width = if (selectedColor == color) 3.dp else 0.dp,
                                    color = if (selectedColor == color) AppPrimaryVariant else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }
            }

            // TAB 2: TYPED SIGNATURE
            if (selectedTab == 1) {
                OutlinedTextField(
                    value = typedName,
                    onValueChange = { if (it.length <= 25) typedName = it },
                    placeholder = { Text("اكتب اسمك") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppTextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                // Render signature preview dynamically with Compose Google Fonts if typed
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Color.White, shape = RoundedCornerShape(12.dp))
                        .border(1.dp, AppTextSecondary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (typedName.isNotEmpty()) {
                        Text(
                            text = typedName,
                            fontFamily = fontOptions[selectedFontIndex].second,
                            fontSize = 32.sp,
                            color = selectedColor,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "معاينة التوقيع الرقمي",
                            color = AppTextSecondary,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // FONT SELECTOR HORIZONTAL LazyRow
                Text(
                    text = "اختر خط التوقيع:",
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    fontSize = 14.sp,
                    textAlign = TextAlign.End
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(fontOptions.size) { index ->
                        val item = fontOptions[index]
                        Card(
                            onClick = { selectedFontIndex = index },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .width(120.dp)
                                .height(60.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedFontIndex == index) AppPrimary.copy(alpha = 0.15f) else AppSurface
                            ),
                            border = if (selectedFontIndex == index) {
                                androidx.compose.foundation.BorderStroke(2.dp, AppPrimary)
                            } else {
                                null
                            }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (typedName.isEmpty()) "Style" else typedName.split(" ").firstOrNull() ?: item.first,
                                    fontFamily = item.second,
                                    fontSize = 18.sp,
                                    color = if (selectedFontIndex == index) AppPrimary else Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // PEN COLOR SELECTOR FOR TEXT TAB
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colorsList.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(color, CircleShape)
                                .border(
                                    width = if (selectedColor == color) 3.dp else 0.dp,
                                    color = if (selectedColor == color) AppPrimaryVariant else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }
            }

            // TAB 3: IMAGE SIGNATURE
            if (selectedTab == 2) {
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("اختر صورة التوقيع", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White, shape = RoundedCornerShape(12.dp))
                        .border(1.dp, AppTextSecondary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "صورة التوقيع",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = AppTextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "لم يتم اختيار صورة",
                                color = AppTextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                if (imageUri != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "منطقة القص (تكبير / تصغير):",
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Slider(
                        value = cropFactor,
                        onValueChange = { cropFactor = it },
                        valueRange = 0.3f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = AppPrimary,
                            activeTrackColor = AppPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
