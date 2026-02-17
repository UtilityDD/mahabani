package com.blackgrapes.kadachabuk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

import android.graphics.Color
import android.widget.ImageView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class QuoteCardGenerator(private val context: Context) {

    fun generateQuoteCard(quote: Quote): File? {
        try {
            // 1. Inflate the layout
            val view = LayoutInflater.from(context).inflate(R.layout.layout_quote_card, null)

            // 2. Populate data
            val quoteText = view.findViewById<TextView>(R.id.tv_quote_text)
            val quoteSource = view.findViewById<TextView>(R.id.tv_quote_source)
            val qrCodeImage = view.findViewById<ImageView>(R.id.iv_qr_code)

            quoteText.text = quote.text
            quoteSource.text = "— ${quote.source}"

            // Generate and set QR Code
            val playStoreUrl = "https://play.google.com/store/apps/details?id=${context.packageName}"
            val qrBitmap = generateQRCode(playStoreUrl)
            if (qrBitmap != null) {
                qrCodeImage.setImageBitmap(qrBitmap)
            }

            // 3. Measure and Layout (Fixed size 1080x1350 for 4:5 aspect ratio)
            val width = 1080
            val height = 1350
            val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            view.measure(widthSpec, heightSpec)
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)

            // 4. Draw to Bitmap
            val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)

            // 5. Save to File
            val cachePath = File(context.externalCacheDir, "quotes")
            cachePath.mkdirs()
            val file = File(cachePath, "quote_card_${System.currentTimeMillis()}.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun generateQRCode(content: String): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    // White QR code on Transparent background for dark theme
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.WHITE else Color.TRANSPARENT)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
