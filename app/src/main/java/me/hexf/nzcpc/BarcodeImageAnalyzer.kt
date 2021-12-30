package me.hexf.nzcpc

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

@androidx.camera.core.ExperimentalGetImage
class BarcodeImageAnalyzer(
    private val onBarcodeDetected: (qrCodes: List<Barcode>?) -> Unit
) : ImageAnalysis.Analyzer{

    private val barcodeOptions = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE
        )
        .build()

    private val barcodeScanner = BarcodeScanning.getClient(barcodeOptions)

    override fun analyze(image: ImageProxy) {
        image.image?.let {

            val imageValue = InputImage.fromMediaImage(it, image.imageInfo.rotationDegrees)

            barcodeScanner.process(imageValue)
                .addOnCompleteListener { barcodes ->
                    onBarcodeDetected(barcodes.result)
                    image.image?.close()
                    image.close()
                }
                .addOnFailureListener { failure ->
                    failure.printStackTrace()
                    image.image?.close()
                    image.close()
                }
        }
    }
}