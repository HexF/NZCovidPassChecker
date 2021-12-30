package me.hexf.nzcpc.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.*
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.google.mlkit.vision.barcode.common.Barcode
import me.hexf.nzcp.CovidPass
import me.hexf.nzcp.Verifier
import me.hexf.nzcp.exceptions.DecodingException
import me.hexf.nzcp.exceptions.KeyFormatException
import me.hexf.nzcp.exceptions.KeyNotFoundException
import me.hexf.nzcp.exceptions.SignatureMismatchException
import me.hexf.nzcp.resolvers.CachingResolver
import me.hexf.nzcpc.*
import me.hexf.nzcpc.databinding.ScanActivityBinding
import me.hexf.nzcpc.resolvers.HttpResolver
import me.hexf.nzcpc.resolvers.SQLiteCachingResolver
import me.hexf.nzcpc.sqlite.DatabaseHelper
import java.io.Serializable
import java.net.URI
import java.time.Period
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@androidx.camera.core.ExperimentalGetImage
class ScanActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var binding: ScanActivityBinding

    private lateinit var verifier: Verifier

    private lateinit var errorBackground: Drawable
    private lateinit var waitingBackground: Drawable
    private lateinit var okBackground: Drawable

    private lateinit var errorStripe: Drawable
    private lateinit var waitingStripe: Drawable
    private lateinit var okStripe: Drawable

    private var resetTimer = Timer()
    private var lastScannedBarcode: String? = null
    private var lastScannedStatus: ScanStatus? = null
    private lateinit var database: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ScanActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()

        errorBackground = AppCompatResources.getDrawable(applicationContext,
            R.drawable.scan_border_error
        )!!
        waitingBackground = AppCompatResources.getDrawable(applicationContext,
            R.drawable.scan_border_waiting
        )!!
        okBackground = AppCompatResources.getDrawable(applicationContext, R.drawable.scan_border_ok)!!

        errorStripe = AppCompatResources.getDrawable(applicationContext,
            R.drawable.scan_stripe_error
        )!!
        waitingStripe = AppCompatResources.getDrawable(applicationContext,
            R.drawable.scan_stripe_waiting
        )!!
        okStripe = AppCompatResources.getDrawable(applicationContext, R.drawable.scan_stripe_ok)!!

        database = DatabaseHelper(applicationContext)

        val policy = ThreadPolicy.Builder().permitAll().build()
        // networking needs to be allowed on the UI thread - we are caching so its fine :)
        StrictMode.setThreadPolicy(policy)

        // Disable screenshotting/recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val verifierBuilder = Verifier.Builder()
            .setResolver(
                SQLiteCachingResolver(
                    database,
                    HttpResolver()
                )
            )

        database.readableDatabase.query(
            DatabaseHelper.TRUSTEDISSUERS_TABLE,
            arrayOf(
                DatabaseHelper.TRUSTEDISSUERS_ISSUER_COLUMN
            ),
            null,
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            while(cursor.moveToNext()){
                verifierBuilder.addTrustedIssuer(
                    URI.create(cursor.getString(0))
                )
            }
        }

        verifier = verifierBuilder.build()

        resetDisplay()
        showWhenLockedAndTurnScreenOn()

        if(allPermissionsGranted()){
            startCamera()
        }else{
            requestPermissions()
        }
    }

    private fun resetDisplay(){
        binding.fullName.text = getString(R.string.scan_full_name_empty)
        binding.dateOfBirth.text = getString(R.string.scan_dob_empty)
        binding.statusText.text = getString(R.string.scan_status_waiting)
        binding.rootLayout.background = waitingBackground
        binding.scanStripe.background = waitingStripe
        binding.moreInfoButton.isEnabled = false
    }

    private fun displayPass(pass: CovidPass?, status: ScanStatus){
        resetDisplay()

        binding.statusText.text = when (status){
            ScanStatus.EXPIRED -> getString(R.string.scan_status_expired)
            ScanStatus.UNTRUSTED_ISSUER -> getString(R.string.scan_status_no_trust)
            ScanStatus.VALID -> getString(R.string.scan_status_ok)
            ScanStatus.DECODING_FAILED -> getString(R.string.scan_status_decoding_failed)
            ScanStatus.UNKNOWN_KEY -> getString(R.string.scan_status_key_not_found)
            ScanStatus.INVALID_KEY_FORMAT -> getString(R.string.scan_status_invalid_key_format)
            ScanStatus.INVALID_SIGNATURE -> getString(R.string.scan_status_invalid_signature)
            ScanStatus.UNKNOWN_ERROR -> "Unknown Error"
        }

        binding.rootLayout.background =
            if(status == ScanStatus.VALID) okBackground
            else errorBackground

        binding.scanStripe.background =
            if(status == ScanStatus.VALID) okStripe
            else errorStripe

        if (pass == null) return

        binding.fullName.text = getString(
            R.string.scan_full_name,
            pass.credentialSubject.givenName,
            pass.credentialSubject.familyName
        )

        val dobString = pass.credentialSubject.dateOfBirth.toString()
        binding.dateOfBirth.text = getString(R.string.scan_dob, dobString)



        binding.moreInfoButton.isEnabled = true
    }

    private fun queueDisplayReset(){
        resetTimer.cancel()
        resetTimer.purge()

        resetTimer = Timer()
        resetTimer.schedule(object: TimerTask() {
            override fun run() {
                runOnUiThread {
                    resetDisplay()
                    lastScannedBarcode = null
                }
            }
        }, 5000)


    }

    private fun requestPermissions(){
        ActivityCompat.requestPermissions(
            this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
        )
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == REQUEST_CODE_PERMISSIONS){
            if(allPermissionsGranted()){
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions are not granted",
                    Toast.LENGTH_SHORT
                    ).show()
                finish()
            }
        }
    }


    private fun startCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        val imageAnalyzer = ImageAnalysis.Builder()
            .build()
            .also {
                it.setAnalyzer(
                    cameraExecutor,
                    BarcodeImageAnalyzer { barcodes ->
                        barcodes?.forEach { barcode ->
                            if(barcode.format == Barcode.FORMAT_QR_CODE)
                            {
                                // Vibrate to indicate scanned
                                if(lastScannedBarcode == barcode.rawValue)
                                    return@forEach
                                lastScannedBarcode = ""

                                queueDisplayReset()

                                var scanStatus: ScanStatus
                                var pass : CovidPass? = null

                                try {
                                    binding.rootLayout.background = errorBackground

                                    pass = CovidPass.createFromQRCodeString(
                                        barcode.rawValue
                                    )

                                    lastScannedBarcode = barcode.rawValue
                                    
                                    val varinfo = verifier.verify(pass)

                                    if(varinfo.isExpired) {
                                        scanStatus = ScanStatus.EXPIRED
                                    } else if(!varinfo.isTrustedIssuer) {
                                        scanStatus = ScanStatus.UNTRUSTED_ISSUER
                                    } else {
                                        binding.rootLayout.background = okBackground
                                        scanStatus = ScanStatus.VALID
                                    }
                                } catch (e: DecodingException){
                                    scanStatus = ScanStatus.DECODING_FAILED
                                } catch (e: KeyNotFoundException){
                                    scanStatus = ScanStatus.UNKNOWN_KEY
                                } catch(e: KeyFormatException){
                                    scanStatus = ScanStatus.INVALID_KEY_FORMAT
                                } catch(e: SignatureMismatchException){
                                    scanStatus = ScanStatus.INVALID_SIGNATURE
                                }

                                lastScannedStatus = scanStatus

                                val pattern =
                                    if(scanStatus == ScanStatus.VALID)
                                        arrayOf(0L,100L,200L,100L)
                                    else
                                        arrayOf(0L,1000L)

                                val vibrator = getSystemService<Vibrator>()

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern.toLongArray(), -1))
                                }else{
                                    vibrator?.vibrate(pattern.toLongArray(), -1)
                                }

                                displayPass(pass, scanStatus)
                            }
                        }
                    }
                )
            }



        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()


            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception){
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    fun openMoreInfo(view: View){
        val intent = Intent(view.context, ScanInfoActivity::class.java).apply {
            putExtra(COVIDPASS_EXTRA,  lastScannedBarcode as String)
            putExtra(SCANSTATUS_EXTRA, lastScannedStatus as Serializable)
        }

        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun showWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }


    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET
        )
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val TAG = "CovidPassChecker"
    }
}