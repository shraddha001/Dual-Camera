package com.sm.task.example.testcamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var frontCameraView: TextureView
    private lateinit var backCameraView: TextureView
    private lateinit var captureButton: Button

    private val cameraManager by lazy { getSystemService(CAMERA_SERVICE) as android.hardware.camera2.CameraManager }
    private var frontCameraId: String? = null
    private var backCameraId: String? = null

    private val CAMERA_PERMISSION_REQUEST_CODE = 101
    private val STORAGE_PERMISSION_REQUEST_CODE = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        frontCameraView = findViewById(R.id.front_camera_view)
        backCameraView = findViewById(R.id.back_camera_view)
        captureButton = findViewById(R.id.capture_button)


        initializeCameras()
        captureButton.setOnClickListener {
            if (!hasCameraPermission()) {
                requestCameraPermission()
            } else if (!hasStoragePermission() && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                requestStoragePermission()
            }
            captureAndSaveCombinedImage()
        }
    }


    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Camera permission granted
                    initializeCameras()
                } else {
                    // Camera permission denied
                    requestCameraPermission()
                    Toast.makeText(
                        this,
                        "Camera permission is required to use this feature",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Storage permission granted
                    Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
                    initializeCameras()
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
                    // Storage permission denied
                    if (!hasStoragePermission()) {
                        requestStoragePermission()
                        initializeCameras()
                    }
                    Toast.makeText(
                        this,
                        "Storage permission is required to save images",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }


    private fun initializeCameras() {
        cameraManager.cameraIdList.forEach { cameraId ->
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val lensFacing =
                characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
            when (lensFacing) {
                android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT -> frontCameraId =
                    cameraId

                android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK -> backCameraId =
                    cameraId
            }
        }

        frontCameraId?.let { setupCameraPreview(it, frontCameraView) }
        backCameraId?.let { setupCameraPreview(it, backCameraView) }
    }

    private fun setupCameraPreview(cameraId: String, textureView: TextureView) {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                openCamera(cameraId, Surface(surface))
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
        }
    }

    private fun openCamera(cameraId: String, surface: Surface) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission()
        }
        cameraManager.openCamera(
            cameraId,
            object : android.hardware.camera2.CameraDevice.StateCallback() {
                override fun onOpened(camera: android.hardware.camera2.CameraDevice) {
                    val requestBuilder =
                        camera.createCaptureRequest(android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW)
                    requestBuilder.addTarget(surface)

                    camera.createCaptureSession(
                        listOf(surface),
                        object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                                session.setRepeatingRequest(requestBuilder.build(), null, null)
                            }

                            override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                                Log.e("Camera", "Failed to configure camera: $cameraId")
                            }
                        },
                        null
                    )
                }

                override fun onDisconnected(camera: android.hardware.camera2.CameraDevice) {}
                override fun onError(camera: android.hardware.camera2.CameraDevice, error: Int) {}
            },
            null
        )
    }

    private fun captureAndSaveCombinedImage() {
        val frontBitmap = frontCameraView.bitmap
        val backBitmap = backCameraView.bitmap

        if (frontBitmap != null && backBitmap != null) {
            val combinedBitmap = Bitmap.createBitmap(
                frontBitmap.width, frontBitmap.height + backBitmap.height,
                Bitmap.Config.ARGB_8888
            )

            val canvas = android.graphics.Canvas(combinedBitmap)
            canvas.drawBitmap(frontBitmap, 0f, 0f, null)
            canvas.drawBitmap(backBitmap, 0f, frontBitmap.height.toFloat(), null)

            saveBitmapToFile(combinedBitmap)
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap) {

        val mediaStorageDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MyCameraApp"
        )
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        mediaStorageDir.apply {
            if (!exists()) {
                if (!mkdirs()) {
                    Log.d("MyCameraApp", "failed to create directory")
                }
            }
        }

        // Create a media file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val file = File(mediaStorageDir.path, "$timeStamp.jpg")
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            Log.d("Camera", "Image saved: ${file.absolutePath}")
        }
    }
}

