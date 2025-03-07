package com.mangodb.statbuddy

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun ImageCropperScreen(
    uri: Uri,
    onCropComplete: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val contentResolver = context.contentResolver

    // 이미지 로드 상태
    var isLoading by remember { mutableStateOf(true) }
    var originalImageWidth by remember { mutableStateOf(0f) }
    var originalImageHeight by remember { mutableStateOf(0f) }

    // 크롭 영역 상태
    var cropRect by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartPoint by remember { mutableStateOf(Offset(0f, 0f)) }

    // 줌/위치 조정 상태
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // 이미지 로드 완료 핸들러
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                    android.graphics.ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }

                originalImageWidth = bitmap.width.toFloat()
                originalImageHeight = bitmap.height.toFloat()

                // 초기 크롭 영역 설정 (정사각형, 이미지 중앙)
                val size = minOf(originalImageWidth, originalImageHeight)
                val left = (originalImageWidth - size) / 2
                val top = (originalImageHeight - size) / 2
                cropRect = Rect(left, top, left + size, top + size)

                isLoading = false
            } catch (e: Exception) {
                // 이미지 로드 실패
                onCancel()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "이미지 자르기",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // 이미지 크롭 영역
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .clipToBounds()
            ) {
                // 이미지 표시
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    dragStartPoint = offset
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()

                                    // 드래그에 따라 크롭 영역 이동
                                    val newLeft = cropRect.left + dragAmount.x
                                    val newTop = cropRect.top + dragAmount.y
                                    val newRight = cropRect.right + dragAmount.x
                                    val newBottom = cropRect.bottom + dragAmount.y

                                    // 이미지 경계 내에 있는지 확인
                                    if (newLeft >= 0 && newRight <= originalImageWidth &&
                                        newTop >= 0 && newBottom <= originalImageHeight) {
                                        cropRect = Rect(newLeft, newTop, newRight, newBottom)
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                }
                            )
                        }
                ) {
                    // 이미지
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = "원본 이미지",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // 크롭 가이드 오버레이
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        // 이미지 스케일 계산
                        val imageScale = minOf(
                            canvasWidth / originalImageWidth,
                            canvasHeight / originalImageHeight
                        )

                        // 이미지 위치 계산
                        val imageOffsetX = (canvasWidth - originalImageWidth * imageScale) / 2
                        val imageOffsetY = (canvasHeight - originalImageHeight * imageScale) / 2

                        // 스케일된 크롭 영역 계산
                        val scaledCropRect = Rect(
                            left = cropRect.left * imageScale + imageOffsetX,
                            top = cropRect.top * imageScale + imageOffsetY,
                            right = cropRect.right * imageScale + imageOffsetX,
                            bottom = cropRect.bottom * imageScale + imageOffsetY
                        )

                        // 반투명 오버레이 그리기
                        drawRect(
                            color = Color(0x80000000),
                            topLeft = Offset(0f, 0f),
                            size = Size(canvasWidth, canvasHeight)
                        )

                        // 크롭 영역은 투명하게
                        drawRect(
                            color = Color.Transparent,
                            topLeft = Offset(scaledCropRect.left, scaledCropRect.top),
                            size = Size(scaledCropRect.width, scaledCropRect.height)
                        )

                        // 크롭 테두리 그리기
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(scaledCropRect.left, scaledCropRect.top),
                            size = Size(scaledCropRect.width, scaledCropRect.height),
                            style = Stroke(width = 2f)
                        )

                        // 그리드 라인 그리기
                        val thirdWidth = scaledCropRect.width / 3
                        val thirdHeight = scaledCropRect.height / 3

                        // 수직 그리드 라인
                        for (i in 1..2) {
                            drawLine(
                                color = Color.White,
                                start = Offset(scaledCropRect.left + thirdWidth * i, scaledCropRect.top),
                                end = Offset(scaledCropRect.left + thirdWidth * i, scaledCropRect.bottom),
                                strokeWidth = 1f
                            )
                        }

                        // 수평 그리드 라인
                        for (i in 1..2) {
                            drawLine(
                                color = Color.White,
                                start = Offset(scaledCropRect.left, scaledCropRect.top + thirdHeight * i),
                                end = Offset(scaledCropRect.right, scaledCropRect.top + thirdHeight * i),
                                strokeWidth = 1f
                            )
                        }
                    }
                }
            }

            // 컨트롤 버튼 영역
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 중앙 정렬 버튼
                Button(
                    onClick = {
                        val size = cropRect.width
                        val left = (originalImageWidth - size) / 2
                        val top = (originalImageHeight - size) / 2
                        cropRect = Rect(left, top, left + size, top + size)
                    }
                ) {
                    Text("중앙 정렬")
                }

                // 원본 비율로 리셋 버튼
                IconButton(
                    onClick = {
                        val size = minOf(originalImageWidth, originalImageHeight)
                        val left = (originalImageWidth - size) / 2
                        val top = (originalImageHeight - size) / 2
                        cropRect = Rect(left, top, left + size, top + size)
                    }
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "리셋")
                }
            }
        }

        // 하단 버튼 영역
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.weight(1f)
            ) {
                Text("취소")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        val croppedUri = cropImage(context, uri, cropRect)
                        croppedUri?.let { onCropComplete(it) }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("확인")
            }
        }
    }
}

private suspend fun cropImage(context: Context, imageUri: Uri, cropRect: Rect): Uri? = withContext(Dispatchers.IO) {
    try {
        // 원본 이미지 불러오기
        val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, imageUri)
            android.graphics.ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
        }

        // 잘라내기
        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            cropRect.left.toInt(),
            cropRect.top.toInt(),
            cropRect.width.toInt(),
            cropRect.height.toInt()
        )

        // 임시 파일에 저장
        val outputDir = context.cacheDir
        val outputFile = File.createTempFile("cropped_", ".png", outputDir)

        FileOutputStream(outputFile).use { out ->
            croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // URI 반환
        Uri.fromFile(outputFile)
    } catch (e: Exception) {
        null
    }
}