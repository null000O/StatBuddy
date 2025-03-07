package com.mangodb.statbuddy

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

@Composable
fun ImageLibraryScreen(
    images: List<Uri>,
    onImageSelected: (Uri) -> Unit,
    onCancel: () -> Unit,
    onImageCropped: (Uri) -> Unit
) {
    var selectedImageForCrop by remember { mutableStateOf<Uri?>(null) }

    if (selectedImageForCrop != null) {
        // 이미지 편집 화면 표시
        ImageCropperScreen(
            uri = selectedImageForCrop!!,
            onCropComplete = { croppedUri ->
                onImageCropped(croppedUri)
                selectedImageForCrop = null
            },
            onCancel = { selectedImageForCrop = null }
        )
    } else {
        // 이미지 라이브러리 화면
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "이미지 라이브러리",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (images.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("저장된 이미지가 없습니다.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(images) { uri ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .aspectRatio(1f)
                                .border(1.dp, Color.Gray)
                        ) {
                            // 이미지
                            androidx.compose.foundation.Image(
                                painter = rememberAsyncImagePainter(uri),
                                contentDescription = "라이브러리 이미지",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onImageSelected(uri) }
                            )

                            // 편집 버튼
                            IconButton(
                                onClick = { selectedImageForCrop = uri },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(36.dp)
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "편집",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("돌아가기")
            }
        }
    }
}