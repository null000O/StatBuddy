package com.mangodb.statbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mangodb.statbuddy.ui.theme.StatBuddyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StatBuddyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    AppNavigation(this)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(activity: ComponentActivity) {
    val navController = rememberNavController()
    val viewModel: ImageViewModel = viewModel(factory = ImageViewModelFactory(activity.application))

    NavHost(navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                navigateToGallery = { navController.navigate("gallery") },
                navigateToLibrary = { navController.navigate("library") },
                viewModel = viewModel,
                activity = activity
            )
        }
        composable("gallery") {
            ImagePickerScreen(
                onImageSelected = { uri ->
                    viewModel.addImageToLibrary(uri)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() }
            )
        }
        composable("library") {
            ImageLibraryScreen(
                images = viewModel.savedImages,
                onImageSelected = { uri ->
                    viewModel.setActiveNotificationImage(uri)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() }
            )
        }
    }
}


@Composable
fun MainScreen(
    navigateToGallery: () -> Unit,
    navigateToLibrary: () -> Unit,
    viewModel: ImageViewModel,
    activity: ComponentActivity
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "알림 이미지 앱",
            style = MaterialTheme.typography.h4,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = navigateToGallery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("새 이미지 선택하기")
        }

        Button(
            onClick = navigateToLibrary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("저장된 이미지 라이브러리")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 알림 활성화 스위치
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "알림 활성화",
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = viewModel.notificationActive.value,
                onCheckedChange = { isActive ->
                    viewModel.setNotificationActive(isActive)
                    if (isActive) {
                        NotificationService.startNotification(activity, viewModel.activeNotificationImage.value)
                    } else {
                        NotificationService.stopNotification(activity)
                    }
                }
            )
        }

        // 현재 선택된 이미지 표시
        viewModel.activeNotificationImage.value?.let { uri ->
            Text(
                text = "현재 선택된 이미지:",
                modifier = Modifier.padding(top = 16.dp)
            )
            // 이미지 미리보기 표시 (Coil 라이브러리 사용)
            androidx.compose.foundation.Image(
                painter = coil.compose.rememberImagePainter(uri),
                contentDescription = "선택된 이미지",
                modifier = Modifier
                    .padding(8.dp)
                    .size(100.dp)
            )
        }
    }
}

// MainScreen Preview
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    StatBuddyTheme {
        // MainActivity에서 사용될 실제 인스턴스를 전달할 수 없으므로
        // 미리보기용 더미 함수와 ViewModel을 사용합니다
        val viewModel = ImageViewModel(androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application)
        MainScreen(
            navigateToGallery = { /* 미리보기에서는 작동 안함 */ },
            navigateToLibrary = { /* 미리보기에서는 작동 안함 */ },
            viewModel = viewModel,
            activity = androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity
        )
    }
}

// ImagePickerScreen Preview
@Preview(showBackground = true)
@Composable
fun ImagePickerScreenPreview() {
    StatBuddyTheme {
        ImagePickerScreen(
            onImageSelected = {},
            onCancel = {}
        )
    }
}

// ImageLibraryScreen Preview
@Preview(showBackground = true)
@Composable
fun EmptyImageLibraryScreenPreview() {
    StatBuddyTheme {
        ImageLibraryScreen(
            images = emptyList(),
            onImageSelected = {},
            onCancel = {}
        )
    }
}