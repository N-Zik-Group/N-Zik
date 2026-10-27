package app.it.fast4x.rimusic.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import app.n_zik.android.R
import app.it.fast4x.rimusic.enums.LogType
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@RequiresApi(Build.VERSION_CODES.O)
fun moveDir(src: Path, dest: Path): Boolean {
    if (src.toFile().isDirectory) {
        for (file in src.toFile().listFiles()!!) {
            moveDir(file.toPath(), dest.resolve(src.relativize(file.toPath())))
        }
    }
    return try {
        Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING)
        true
    } catch (e: IOException) {
        Timber.tag("FileUtils").e(e)
        false
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun copyDir(src: Path, dest: Path) {
    val sources = Files.walk(src).toList()
    for (source in sources) {
        Files.copy(source, dest.resolve(src.relativize(source)),
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}

fun saveImageToInternalStorage(context: Context, imageUri: Uri, dirPath: String, thumbnailName: String): Uri? {
    try {
        if (!createDirIfNotExists(context, dirPath)) {
            Timber.tag("FileUtils").e("Failed to create directory: $dirPath")
            return null
        }
        val outputFile = File(context.filesDir, "$dirPath/$thumbnailName")

        val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
        if (inputStream == null) {
            Timber.tag("FileUtils").e("Failed to open input stream for URI: $imageUri")
            return null
        }

        // Decode bounds only to calculate sample size
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        val maxDimension = 1000
        val sampleSize = calculateInSampleSize(options, maxDimension, maxDimension)

        // Decode the sampled bitmap
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decodeStream = context.contentResolver.openInputStream(imageUri) ?: return null
        val bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
        decodeStream.close()

        if (bitmap == null) {
            Timber.tag("FileUtils").e("Failed to decode bitmap from URI: $imageUri")
            return null
        }

        // Scale down if still larger than maxDimension
        val finalBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
            val matrix = Matrix().apply { postScale(scale, scale) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it != bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }

        // Save the resized bitmap
        FileOutputStream(outputFile).use { outputStream ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.flush()
        }

        if (finalBitmap != bitmap) finalBitmap.recycle()

        Timber.tag("FileUtils").d("Saved resized image: ${finalBitmap.width}x${finalBitmap.height} to ${outputFile.absolutePath}")
        return Uri.fromFile(outputFile)
    } catch (e: IOException) {
        Timber.tag("FileUtils").e(e, "Failed to save image to internal storage")
        return null
    } catch (e: Exception) {
        Timber.tag("FileUtils").e(e, "Unexpected error saving image")
        return null
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height, width) = options.outHeight to options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

fun checkFileExists(context: Context, filePath: String): String? {
    val file = File(context.filesDir, filePath)

    return if (file.exists()) {
        file.toURI().toString()
    } else {
        null
    }
}

fun deleteFileIfExists(context: Context, filePath: String): Boolean {
    val file = File(context.filesDir, filePath)

    return if (file.exists()) {
        file.delete()
    } else {
        false
    }
}

fun createDirIfNotExists(context: Context, dirPath: String): Boolean {
    val directory = File(context.filesDir, dirPath)

    return if (!directory.exists()) {
        directory.mkdirs()
    } else {
        true
    }
}



fun loadAppLog(context: Context, type: LogType): String? {
    val file = File(context.filesDir.resolve("logs"),
        when (type) {
            LogType.Default ->  "N-Zik_log.txt"
            LogType.Crash ->    "N-Zik_crash_log.txt"
        }
    )
    if (file.exists()) {
        Toaster.s( R.string.value_copied )
        return file.readText()
    } else
        Toaster.w( R.string.no_log_available )
    return null
}

fun saveFileToInternalStorage(context: Context, fileName: String, fileContent: String) {
    try {
        val file = File(context.filesDir.resolve("logs"), fileName)
        file.writeText(fileContent)
    } catch (e: IOException) {
        Timber.tag("FileUtils").e("Failed to save file $fileName to internal storage: $e")

    }


}


