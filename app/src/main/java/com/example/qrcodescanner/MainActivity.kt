package com.example.qrcodescanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.qrcodescanner.Extensions.getBitmapFromUri
import com.example.qrcodescanner.Extensions.scanQRCode
import com.example.qrcodescanner.client.ScanClient
import com.example.qrcodescanner.databinding.ActivityMainBinding
import com.example.qrcodescanner.models.ScanResponse

import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Main activity for QR code scanning and reporting functionality.
 */
class MainActivity : AppCompatActivity() {

    // View binding for the activity
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        event()
    }

    /**
     * Set up event listeners for buttons.
     */
    private fun event() {
        binding.btnCamera.setOnClickListener {
            checkPermissionCamera()
        }
        binding.btnGallery.setOnClickListener {
            checkGalleryPermission()
        }
        binding.btnReport.setOnClickListener {
            startActivity(Intent(this@MainActivity, ReportActivity::class.java))
        }
    }

    /**
     * Reset UI components to their initial state.
     */
    private fun resetUI() {
        binding.tvResponse.text = "Response"
        binding.textResult.text = "Result"
        binding.tvResponse.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.black))
    }

    /**
     * Set the result text on the UI.
     * @param string The text to be set as the result.
     */
    private fun setResult(string: String) {
        binding.textResult.text = string
    }

    /**
     * Show the response received from the server.
     * @param scanResponse The response object containing code and message.
     */
    private fun showResponse(scanResponse: ScanResponse) {
            when (scanResponse.code) {
                "00" -> {
                    // safe
                    binding.tvResponse.setTextColor(
                        ContextCompat.getColor(
                            this@MainActivity, R.color.safe
                        )
                    )
                    binding.tvResponse.text = "This link is safe"
                }

                "01" -> {
                    //no data
                    binding.tvResponse.setTextColor(
                        ContextCompat.getColor(
                            this@MainActivity, R.color.no_data
                        )
                    )
                    binding.tvResponse.text = "This link may be harmful"
                }

                "13" -> {
                    //waring
                    binding.tvResponse.setTextColor(
                        ContextCompat.getColor(
                            this@MainActivity, R.color.warning
                        )
                    )
                    binding.tvResponse.text = "WARNING! This link is malicious"
                }
            }
    }

    /**
     * Post scan data to the server.
     * @param value The value to be posted.
     */
    private fun postScan(value: String) {
        val client = ScanClient.getClient()
        val call: Call<ScanResponse> = client.sendScan(Extensions.generateKey(), value)

        call.enqueue(object : Callback<ScanResponse> {
            override fun onResponse(call: Call<ScanResponse>, response: Response<ScanResponse>) {
                if (response.isSuccessful) {
                    val scanResponse: ScanResponse? = response.body()
                    if (scanResponse != null) {
                        showResponse(scanResponse)
                    }
                } else {
                    binding.tvResponse.text = "Check the internet connection"
                }
            }
            override fun onFailure(call: Call<ScanResponse>, t: Throwable) {
                binding.tvResponse.text = "onFailure: " + t.message
            }
        })

    }

    /**
     * Show the camera for scanning QR codes.
     */
    private fun showCamera() {
        scannerLauncher.launch(Extensions.initScan())
    }

    /**
     * This function check camera permission.
     * If permission is granted then run camera.
     * If not display notification message and request permission.
     */
    private fun checkPermissionCamera() {
        if (ContextCompat.checkSelfPermission(
                this@MainActivity, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            showCamera()
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            Toast.makeText(this@MainActivity, "Camera permission required", Toast.LENGTH_SHORT).show()
        } else requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    /**
     * This function request permission
     */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                showCamera()
            }
        }

    /**
     * Check and request gallery permission.
     */
    private fun checkGalleryPermission() {
        val p1 = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (p1 != PackageManager.PERMISSION_GRANTED ) {
            requestGalleryPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            getImageGallery()
        }
    }

    /**
     * This function triggers system to ask user accept or deny permission.
     */
    private val requestGalleryPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
               getImageGallery()
            }
        }

    /**
     * Activity responsible for handling the reporting functionality.
     * Users can input a URL, and this activity sends a report request to the server.
     */
    private fun getImageGallery() {
        try {
            // Create Intent to pick an image from external storage
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            // Launch image picker
            startForResult.launch(intent)
        } catch (exp: Exception) {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
        }
    }


    /**
     * Handle the result of scanning from the camera.
     */
    private val scannerLauncher =
        registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
            run {
                if (result.contents == null) {
                    Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
                } else {
                    setResult(result.contents)
                    postScan(result.contents)
                }
            }
        }



    /**
     * Handle the result of selecting an image from the gallery.
     */
    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->

            if (result.resultCode == Activity.RESULT_OK) {
                if (result.data!!.data != null) {
                    val imageUri: Uri? = result.data!!.data
                    val bitmap: Bitmap? = getBitmapFromUri(this, imageUri)
                    if (bitmap != null) {
                        val it = scanQRCode(bitmap)
                        if (it != null) {
                            if (URLUtil.isNetworkUrl(it)) {
                                postScan(it)
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "This is not a valid network URL",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            setResult(it)
                        } else {
                            resetUI()
                        }
                    } else {
                        resetUI()
                    }
                }
            }
        }
}


