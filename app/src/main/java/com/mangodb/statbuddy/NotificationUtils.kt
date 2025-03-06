package com.mangodb.statbuddy

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import com.caverock.androidsvg.SVG

object NotificationUtils {
    private const val TAG = "NotificationUtils"
    private const val ICON_SIZE = 96 // 알림 아이콘 크기 (픽셀)
    private const val ICON_PADDING = 8 // 아이콘 패딩 (픽셀)
    private const val TEMP_ICON_NAME = "temp_notification_icon.png"
    private const val PROVIDER_AUTHORITY = "com.example.notificationimageapp.fileprovider" // Manifest에 맞게 수정 필요

    /**
     * 사용자가 선택한 이미지에서 알림용 아이콘 생성
     * @return 생성된 아이콘 파일의 URI
     */
    fun createSmallIconFromImage(context: Context, imageUri: Uri?): Int? {
        if (imageUri == null) return null

        try {
            // 파일 확장자 확인
            val fileExtension = getMimeType(context, imageUri)

            // 원본 이미지 로드
            val originalBitmap = when {
                fileExtension.equals("svg", ignoreCase = true) -> {
                    // SVG 파일 처리
                    loadSvgAsBitmap(context, imageUri)
                }
                else -> {
                    // 일반 이미지 파일 처리
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, imageUri)
                        android.graphics.ImageDecoder.decodeBitmap(source)
                    } else {
                        @Suppress("DEPRECATION")
                        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                    }
                }
            } ?: return null  // 비트맵을 로드할 수 없으면 null 반환

            // 알림용 아이콘으로 변환 (크기 조정, 단색화, 알파 채널 적용)
            val iconBitmap = createNotificationIconBitmap(originalBitmap)

            // 임시 파일에 저장
            val iconFile = saveIconToFile(context, iconBitmap)
            if (iconFile != null) {
                // 동적 리소스 ID 생성 (해시 기반)
                val resourceId = iconFile.absolutePath.hashCode()

                // ResourceManager에 등록
                DynamicResourceManager.registerIcon(resourceId, iconFile)

                return resourceId
            }
        } catch (e: Exception) {
            Log.e(TAG, "아이콘 생성 실패", e)
        }

        return null
    }

    /**
     * URI의 MIME 타입 확인하고 확장자 반환
     */
    private fun getMimeType(context: Context, uri: Uri): String {
        var extension = ""
        try {
            val contentResolver = context.contentResolver
            val mimeTypeMap = MimeTypeMap.getSingleton()

            // ContentResolver로 MIME 타입 확인
            val mimeType = contentResolver.getType(uri)

            if (mimeType != null) {
                extension = mimeTypeMap.getExtensionFromMimeType(mimeType) ?: ""
            }

            // MIME 타입으로 확인 안 되면 경로에서 확장자 추출
            if (extension.isEmpty()) {
                val path = uri.path
                if (path != null) {
                    val lastDot = path.lastIndexOf(".")
                    if (lastDot >= 0) {
                        extension = path.substring(lastDot + 1)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MIME 타입 확인 실패", e)
        }

        return extension
    }

    /**
     * SVG 파일을 비트맵으로 변환
     */
    private fun loadSvgAsBitmap(context: Context, uri: Uri): Bitmap? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // AndroidSVG 라이브러리로 SVG 파일 로드
            val svg = SVG.getFromInputStream(inputStream)
            inputStream.close()

            // SVG 크기 결정
            val width = if (svg.documentWidth > 0) svg.documentWidth.toInt() else ICON_SIZE
            val height = if (svg.documentHeight > 0) svg.documentHeight.toInt() else ICON_SIZE

            // 비트맵 생성 및 그리기
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            svg.renderToCanvas(canvas)

            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "SVG 로드 실패", e)
            return null
        }
    }

    /**
     * 비트맵을 알림용 아이콘으로 변환
     */
    private fun createNotificationIconBitmap(original: Bitmap): Bitmap {
        // 정사각형 캔버스 생성
        val size = ICON_SIZE
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 원본 이미지 크기 조절 및 중앙 배치
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val scale: Float
        val left: Float
        val top: Float

        if (original.width > original.height) {
            scale = (size - 2 * ICON_PADDING).toFloat() / original.height
            left = (size - original.width * scale) / 2
            top = ICON_PADDING.toFloat()
        } else {
            scale = (size - 2 * ICON_PADDING).toFloat() / original.width
            left = ICON_PADDING.toFloat()
            top = (size - original.height * scale) / 2
        }

        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(left, top)

        // 비트맵 그리기
        canvas.drawBitmap(original, matrix, paint)

        // 단색화 처리 (알림 아이콘용)
        val monochromeResult = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val monochromeCanvas = Canvas(monochromeResult)

        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f) // 채도 제거 (흑백)

        val monochromePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        monochromePaint.colorFilter = ColorMatrixColorFilter(colorMatrix)

        monochromeCanvas.drawBitmap(result, 0f, 0f, monochromePaint)

        return monochromeResult
    }

    /**
     * 아이콘 비트맵을 파일로 저장
     */
    private fun saveIconToFile(context: Context, bitmap: Bitmap): File? {
        val iconDir = File(context.cacheDir, "notification_icons")
        if (!iconDir.exists()) {
            iconDir.mkdirs()
        }

        val iconFile = File(iconDir, TEMP_ICON_NAME)

        try {
            FileOutputStream(iconFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            return iconFile
        } catch (e: IOException) {
            Log.e(TAG, "아이콘 파일 저장 실패", e)
        }

        return null
    }

    /**
     * 동적으로 생성된 아이콘 리소스 관리자
     */
    object DynamicResourceManager {
        private val iconResources = HashMap<Int, File>()

        fun registerIcon(resourceId: Int, file: File) {
            iconResources[resourceId] = file
        }

        fun getIconFile(resourceId: Int): File? {
            return iconResources[resourceId]
        }
    }
}