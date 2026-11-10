package com.focushome

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.print.PrintHelper
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

sealed interface ScanResult {
    data class Code(val raw: String) : ScanResult
    data object Cancelled : ScanResult
    data class Failed(val reason: String) : ScanResult
}

object QrKey {
    private const val SHARE_DIR = "keys"

    fun newToken(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * Plain text, not a link: no other app can act on it, so a photo or screenshot of the code
     * is useless on this phone. Only a live scan in Focus Home counts.
     */
    fun contentFor(token: String) = "FOCUSHOME-KEY:$token"

    fun hash(raw: String): String =
        MessageDigest.getInstance("SHA-256").digest(raw.trim().toByteArray()).joinToString("") { "%02x".format(it) }

    /** Opens Google's scanner. Typing a code by hand is deliberately not allowed. */
    fun scan(context: Context, onResult: (ScanResult) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code ->
                val raw = code.rawValue
                onResult(if (raw.isNullOrBlank()) ScanResult.Failed("That QR code is empty.") else ScanResult.Code(raw))
            }
            .addOnCanceledListener { onResult(ScanResult.Cancelled) }
            .addOnFailureListener {
                onResult(ScanResult.Failed("The scanner isn't ready yet. Check your connection and try again in a minute."))
            }
    }

    /** A printable card: the code with a caption underneath, black on white. */
    fun render(content: String, size: Int = 1000): Bitmap {
        val matrix = QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
        val captionHeight = size / 5
        val bitmap = Bitmap.createBitmap(size, size + captionHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)

        val canvas = Canvas(bitmap)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = size / 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.15f
        }
        val sub = Paint(title).apply {
            textSize = size / 32f
            typeface = Typeface.DEFAULT
            letterSpacing = 0f
            color = Color.DKGRAY
        }
        canvas.drawText("FOCUS HOME KEY", size / 2f, size + captionHeight * 0.35f, title)
        canvas.drawText("Scan in Focus Home to unlock", size / 2f, size + captionHeight * 0.65f, sub)
        return bitmap
    }

    /** Opens the system print dialog. Nothing is written to storage. */
    fun print(activity: Activity, bitmap: Bitmap) =
        PrintHelper(activity).apply { scaleMode = PrintHelper.SCALE_MODE_FIT }.printBitmap("Focus Home key", bitmap)

    /** Writes the card to private cache for sharing (cleared on next launch) and returns a shareable Uri. */
    fun shareFile(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val file = File(dir, "focus-home-key.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun clearShared(context: Context) {
        File(context.cacheDir, SHARE_DIR).deleteRecursively()
    }

    /** Removes key images that version 1.0 saved to Pictures/Focus Home. Only files this app created are visible to it. */
    fun deleteSavedCopies(context: Context): Int = try {
        context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf(Environment.DIRECTORY_PICTURES + "/Focus Home%"),
        )
    } catch (_: Exception) {
        0
    }
}
