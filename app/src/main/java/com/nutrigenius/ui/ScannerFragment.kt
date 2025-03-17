package com.nutrigenius.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.nutrigenius.R
import com.nutrigenius.ml.DetectionResult
import com.nutrigenius.ml.DetectionViewModel
import com.nutrigenius.ml.FaceDetector
import com.nutrigenius.ml.FaceResult
import com.nutrigenius.ml.ObjectDetector
import com.nutrigenius.ml.Recommender
import com.nutrigenius.ml.TextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * ScannerFragment - Fragment untuk scan makanan, wajah, atau teks
 * 
 * Fragment ini menyediakan interface kamera untuk mengambil foto dan
 * melakukan deteksi makanan, wajah, atau teks berdasarkan mode yang dipilih.
 */
class ScannerFragment : Fragment() {
    // Camera components
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    
    // ML components
    private lateinit var objectDetector: ObjectDetector
    private lateinit var faceDetector: FaceDetector
    private lateinit var textRecognizer: TextRecognizer
    private lateinit var recommender: Recommender
    
    // ViewModel untuk sharing hasil deteksi
    private val viewModel: DetectionViewModel by activityViewModels()
    
    // UI components
    private lateinit var progressBar: ProgressBar
    private lateinit var captureButton: Button
    private lateinit var viewFinder: PreviewView
    private lateinit var capturedImageView: ImageView
    private lateinit var resultTextView: TextView
    
    // Scan mode enum
    enum class ScanMode {
        OBJECT_DETECTION,
        FACE_DETECTION,
        TEXT_DETECTION
    }
    
    // Detection mode (saved in SharedPreferences)
    private var currentMode = DetectionViewModel.DetectionMode.OBJECT
    // Scan mode 
    private var currentScanMode = ScanMode.OBJECT_DETECTION
    
    // Orientation event listener untuk handling rotasi perangkat
    private lateinit var orientationEventListener: OrientationEventListener
    
    // Permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            showError("Camera permission is required to use this feature")
        }
    }
    
    // Flash components
    private var cameraControl: CameraControl? = null
    private var flashEnabled = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_scanner, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize UI components
        progressBar = view.findViewById(R.id.processingProgressBar)
        captureButton = view.findViewById(R.id.camera_capture_button)
        viewFinder = view.findViewById(R.id.viewFinder)
        capturedImageView = view.findViewById(R.id.capturedImageView)
        resultTextView = view.findViewById(R.id.resultTextView)
        
        // Initialize ML components
        objectDetector = ObjectDetector(requireContext())
        faceDetector = FaceDetector(requireContext())
        textRecognizer = TextRecognizer(requireContext())
        recommender = Recommender()
        
        // Set up camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Initialize orientation listener
        initOrientationListener()
        
        // Load saved detection mode
        loadDetectionMode()
        
        // Setup detection mode tabs
        setupDetectionModeTabs(view)
        
        // Check camera permission
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        
        // Set up capture button
        captureButton.setOnClickListener {
            // Disable button to prevent multiple clicks
            captureButton.isEnabled = false
            // Clear any previous results
            resultTextView.visibility = View.GONE
            capturedImageView.visibility = View.GONE
            // Take photo
            takePhoto()
        }
        
        // Load from gallery button
        view.findViewById<Button>(R.id.gallery_button).setOnClickListener {
            // In a full implementation, this would launch a gallery picker
            Toast.makeText(context, "Gallery functionality not implemented in prototype", Toast.LENGTH_SHORT).show()
        }
        
        // Observe viewModel for detection mode changes
        viewModel.detectionMode.observe(viewLifecycleOwner) { mode ->
            if (mode != null && mode != currentMode) {
                currentMode = mode
                updateModeUI(view)
            }
        }
        
        // Setup scan mode button
        setupScanModeButton(view)
    }
    
    /**
     * Setup tombol untuk mengganti mode scan
     */
    private fun setupScanModeButton(view: View) {
        val switchModeButton = view.findViewById<Button>(R.id.switch_mode_button)
        
        switchModeButton?.setOnClickListener {
            currentScanMode = when(currentScanMode) {
                ScanMode.OBJECT_DETECTION -> ScanMode.FACE_DETECTION
                ScanMode.FACE_DETECTION -> ScanMode.TEXT_DETECTION
                ScanMode.TEXT_DETECTION -> ScanMode.OBJECT_DETECTION
            }
            
            updateScanModeUI(view)
            
            // Update detection mode jika perlu
            val detectionMode = when(currentScanMode) {
                ScanMode.OBJECT_DETECTION -> DetectionViewModel.DetectionMode.OBJECT
                ScanMode.FACE_DETECTION -> DetectionViewModel.DetectionMode.FACE
                ScanMode.TEXT_DETECTION -> DetectionViewModel.DetectionMode.TEXT // Mode baru untuk TEXT
            }
            
            if (detectionMode != currentMode) {
                currentMode = detectionMode
                viewModel.setDetectionMode(currentMode)
                updateModeUI(view)
            }
        }
    }
    
    /**
     * Update UI berdasarkan mode scan saat ini
     */
    private fun updateScanModeUI(view: View) {
        val switchModeButton = view.findViewById<Button>(R.id.switch_mode_button)
        val titleText = view.findViewById<TextView>(R.id.title_text)
        val textOverlay = view.findViewById<View>(R.id.text_detection_overlay)
        
        when (currentScanMode) {
            ScanMode.OBJECT_DETECTION -> {
                switchModeButton?.text = "Mode: Deteksi Makanan"
                captureButton.text = "Capture Food"
                titleText.text = "Scan Food"
                textOverlay?.visibility = View.GONE
            }
            ScanMode.FACE_DETECTION -> {
                switchModeButton?.text = "Mode: Deteksi Wajah"
                captureButton.text = "Capture Face"
                titleText.text = "Growth Monitoring"
                textOverlay?.visibility = View.GONE
            }
            ScanMode.TEXT_DETECTION -> {
                switchModeButton?.text = "Mode: Deteksi Teks"
                captureButton.text = "Capture Text"
                titleText.text = "Scan Text"
                textOverlay?.visibility = View.VISIBLE
            }
        }
        
        // Update detection mode jika perlu
        val detectionMode = when(currentScanMode) {
            ScanMode.OBJECT_DETECTION -> DetectionViewModel.DetectionMode.OBJECT
            ScanMode.FACE_DETECTION -> DetectionViewModel.DetectionMode.FACE
            ScanMode.TEXT_DETECTION -> DetectionViewModel.DetectionMode.TEXT // Mode baru untuk TEXT
        }
        
        if (detectionMode != currentMode) {
            currentMode = detectionMode
            viewModel.setDetectionMode(currentMode)
        }
    }
    
    /**
     * Inisialisasi orientation listener untuk mendeteksi perubahan orientasi perangkat
     */
    private fun initOrientationListener() {
        orientationEventListener = object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                
                // Konversi orientasi ke rotasi Surface
                val rotation = when {
                    orientation >= 335 || orientation < 25 -> Surface.ROTATION_0
                    orientation >= 65 && orientation < 115 -> Surface.ROTATION_90
                    orientation >= 155 && orientation < 205 -> Surface.ROTATION_180
                    orientation >= 245 && orientation < 295 -> Surface.ROTATION_270
                    else -> return // Skipping diagonal orientations
                }
                
                // Update rotasi target untuk image capture
                imageCapture?.targetRotation = rotation
                
                Log.d(TAG, "Device orientation changed: $orientation, Rotation: $rotation")
            }
        }
        
        // Enable orientation listener jika sensor tersedia
        if (orientationEventListener.canDetectOrientation()) {
            Log.d(TAG, "Orientation detection is supported")
        } else {
            Log.d(TAG, "Orientation detection is not supported")
        }
    }
    
    private fun setupDetectionModeTabs(view: View) {
        val tabLayout = view.findViewById<TabLayout>(R.id.detection_mode_tabs)
        
        // Add tabs for different detection modes
        tabLayout.addTab(tabLayout.newTab().setText("Food Detection"))
        tabLayout.addTab(tabLayout.newTab().setText("Face Detection"))
        
        // Set the initial selected tab based on current mode
        when (currentMode) {
            DetectionViewModel.DetectionMode.OBJECT -> tabLayout.selectTab(tabLayout.getTabAt(0))
            DetectionViewModel.DetectionMode.FACE -> tabLayout.selectTab(tabLayout.getTabAt(1))
        }
        
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentMode = when (tab.position) {
                    0 -> DetectionViewModel.DetectionMode.OBJECT
                    1 -> DetectionViewModel.DetectionMode.FACE
                    else -> DetectionViewModel.DetectionMode.OBJECT
                }
                
                // Update UI hints based on selected mode
                updateModeUI(view)
                
                // Clear previous results
                resultTextView.visibility = View.GONE
                capturedImageView.visibility = View.GONE
                
                // Save mode preference
                saveDetectionMode()
                
                // Update viewModel
                viewModel.setDetectionMode(currentMode)
                
                // Update scan mode
                currentScanMode = if (currentMode == DetectionViewModel.DetectionMode.OBJECT) {
                    ScanMode.OBJECT_DETECTION
                } else {
                    ScanMode.FACE_DETECTION
                }
                updateScanModeUI(view)
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        
        // Initialize UI for the current mode
        updateModeUI(view)
    }
    
    private fun updateModeUI(view: View) {
        val titleText = view.findViewById<TextView>(R.id.title_text)
        
        when (currentMode) {
            DetectionViewModel.DetectionMode.OBJECT -> {
                titleText.text = "Scan Food"
                captureButton.text = "Capture Food"
            }
            DetectionViewModel.DetectionMode.FACE -> {
                titleText.text = "Growth Monitoring"
                captureButton.text = "Capture Face"
            }
        }
    }
    
    /**
     * Memulai kamera dengan optimasi untuk demo eksternal dengan Camo
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        
        cameraProviderFuture.addListener({
            try {
                // Get camera provider
                val cameraProvider = cameraProviderFuture.get()
                
                // Konfigurasi preview dengan resolusi optimal untuk demo
                val preview = Preview.Builder()
                    .setTargetResolution(Size(1280, 720)) // Resolusi optimal untuk demo
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }
                
                // Konfigurasi image capture dengan optimasi resolusi dan latency
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetResolution(Size(1280, 720))
                    .setJpegQuality(85) // Optimasi kualitas vs ukuran file
                    .build()
                
                // Konfigurasi image analysis jika diperlukan
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                
                // Select camera - prioritaskan kamera belakang
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                // Unbind semua use case sebelum rebind
                cameraProvider.unbindAll()
                
                // Bind use cases ke lifecycle
                val camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
                
                // Setup torch/flash jika tersedia (opsional untuk demo)
                if (camera.cameraInfo.hasFlashUnit()) {
                    cameraControl = camera.cameraControl
                    setupFlashButton()
                } else {
                    // Sembunyikan tombol flash jika tidak tersedia
                    view?.findViewById<Button>(R.id.flash_button)?.visibility = View.GONE
                }
                
                // Log untuk keperluan debugging
                Log.d(TAG, "Camera setup successfully with resolution: 1280x720")
                
            } catch (e: Exception) {
                Log.e(TAG, "Camera setup failed: ${e.message}", e)
                showError("Tidak dapat memulai kamera. Silakan periksa izin dan koneksi kamera.")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    
    /**
     * Setup tombol flash jika kamera mendukung
     */
    private fun setupFlashButton() {
        view?.findViewById<Button>(R.id.flash_button)?.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                toggleFlash()
            }
        }
    }
    
    /**
     * Toggle flash on/off
     */
    private fun toggleFlash() {
        flashEnabled = !flashEnabled
        cameraControl?.enableTorch(flashEnabled)
        
        // Update UI button flash
        view?.findViewById<Button>(R.id.flash_button)?.text = 
            if (flashEnabled) "Flash: ON" else "Flash: OFF"
    }
    
    /**
     * Proses gambar yang ditangkap dengan optimasi untuk demo
     */
    private fun processImage(bitmap: Bitmap) {
        try {
            // Tampilkan indikator proses
            view?.findViewById<ProgressBar>(R.id.processingProgressBar)?.visibility = View.VISIBLE
            
            when (currentScanMode) {
                ScanMode.OBJECT_DETECTION -> {
                    // Process using food detector
                    detectObject(bitmap)
                }
                ScanMode.FACE_DETECTION -> {
                    // Process using face detector
                    detectFace(bitmap)
                }
                ScanMode.TEXT_DETECTION -> {
                    // Process using text detector with enhanced contrast for demo
                    val enhancedBitmap = enhanceImageForTextDetection(bitmap)
                    detectText(enhancedBitmap)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}", e)
            showError("Gagal memproses gambar: ${e.message}")
            
            // Sembunyikan indikator
            view?.findViewById<ProgressBar>(R.id.processingProgressBar)?.visibility = View.GONE
        }
    }
    
    /**
     * Meningkatkan kualitas gambar untuk deteksi teks
     * Optimasi khusus untuk demo dengan Camo
     */
    private fun enhanceImageForTextDetection(bitmap: Bitmap): Bitmap {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            
            // Buat bitmap baru dengan konfigurasi yang sama
            val enhancedBitmap = Bitmap.createBitmap(width, height, bitmap.config)
            
            // Canvas untuk menggambar
            val canvas = Canvas(enhancedBitmap)
            
            // Tingkatkan kontras dan saturasi dengan ColorMatrix
            val paint = Paint()
            val colorMatrix = ColorMatrix()
            
            // Kontras lebih tinggi untuk teks
            colorMatrix.setScale(1.2f, 1.2f, 1.2f, 1.0f)
            
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            
            // Log untuk keperluan debugging
            Log.d(TAG, "Image enhanced for text detection: ${width}x${height}")
            
            enhancedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enhance image: ${e.message}", e)
            // Kembalikan bitmap asli jika terjadi error
            bitmap
        }
    }
    
    private fun takePhoto() {
        val imageCapture = imageCapture ?: run {
            showError("Camera not initialized")
            captureButton.isEnabled = true
            return
        }
        
        // Show progress indicator
        progressBar.visibility = View.VISIBLE
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "NutriGenius_${System.currentTimeMillis()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }
        
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()
            
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri ?: run {
                        showError("Could not save image")
                        progressBar.visibility = View.GONE
                        captureButton.isEnabled = true
                        return
                    }
                    
                    // Load the captured image
                    try {
                        requireContext().contentResolver.openInputStream(savedUri)?.use { inputStream ->
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            // Process image in background
                            processImage(bitmap)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading image: ${e.message}", e)
                        showError("Could not load captured image")
                        progressBar.visibility = View.GONE
                        captureButton.isEnabled = true
                    }
                }
                
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    showError("Failed to capture photo: ${exception.message}")
                    progressBar.visibility = View.GONE
                    captureButton.isEnabled = true
                }
            }
        )
    }
    
    /**
     * Memproses gambar secara asynchronous di background thread
     * 
     * @param originalBitmap Bitmap gambar yang diambil
     */
    private fun processImageAsync(originalBitmap: Bitmap) {
        // Resize bitmap for more efficient processing
        val resizedBitmap = resizeBitmapForProcessing(originalBitmap)
        
        // Process in background thread
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                when (currentScanMode) {
                    ScanMode.OBJECT_DETECTION -> {
                        // Process for object detection
                        val detections = objectDetector.detectObjects(resizedBitmap)
                        
                        // Get recommendation for top detected object
                        val recommendation = if (detections.isNotEmpty()) {
                            recommender.getRecommendation(detections[0].objectClass)
                        } else null
                        
                        // Save to ViewModel
                        viewModel.setFoodResult(detections, recommendation)
                        
                        // Draw bounding boxes on a copy of the bitmap
                        val bitmapWithBoxes = drawDetections(resizedBitmap, detections)
                        
                        // Save the processed image
                        val imageBytes = compressBitmap(bitmapWithBoxes)
                        viewModel.setCapturedImage(imageBytes)
                        
                        // Update UI on main thread
                        withContext(Dispatchers.Main) {
                            displayFoodResults(bitmapWithBoxes, detections, recommendation)
                            
                            // Save detection result to database
                            if (detections.isNotEmpty()) {
                                val topDetection = detections[0]
                                saveDetectionResultToDatabase(
                                    topDetection.objectClass,
                                    topDetection.confidence,
                                    recommendation,
                                    imageBytes
                                )
                            }
                        }
                    }
                    ScanMode.FACE_DETECTION -> {
                        // Process for face detection
                        val faceResult = faceDetector.detectFaceAndEstimateAge(resizedBitmap)
                        
                        // Save to ViewModel if face detected
                        faceResult?.let { viewModel.setFaceResult(it) }
                        
                        // Draw face detection on bitmap
                        val bitmapWithFace = if (faceResult != null) {
                            drawFaceDetection(resizedBitmap, faceResult)
                        } else {
                            resizedBitmap
                        }
                        
                        // Save the processed image
                        val imageBytes = compressBitmap(bitmapWithFace)
                        viewModel.setCapturedImage(imageBytes)
                        
                        // Update UI on main thread
                        withContext(Dispatchers.Main) {
                            displayFaceResults(bitmapWithFace, faceResult)
                            
                            // Save face detection result to database
                            faceResult?.let {
                                saveFaceDetectionResultToDatabase(
                                    it.age.toString(),
                                    it.confidence,
                                    it.gender,
                                    imageBytes
                                )
                            }
                        }
                    }
                    ScanMode.TEXT_DETECTION -> {
                        // Process for text detection
                        detectText(resizedBitmap)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showError("Error processing image: ${e.message}")
                }
            } finally {
                // Cleanup
                if (resizedBitmap != originalBitmap) {
                    recycleBitmap(resizedBitmap)
                }
                
                // Re-enable capture button and hide progress
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    captureButton.isEnabled = true
                }
            }
        }
    }
    
    /**
     * Mendeteksi teks dalam gambar menggunakan OCR
     */
    private fun detectText(bitmap: Bitmap) {
        // Gunakan fungsi yang di-cache untuk optimasi performa
        textRecognizer.recognizeTextWithCache(bitmap) { detectedText, keywords, error ->
            // Pindahkan pemrosesan ke Main Thread karena callback bisa dari thread lain
            lifecycleScope.launch(Dispatchers.Main) {
                if (error != null) {
                    showError("Text detection failed: ${error.message}")
                    return@launch
                }
                
                if (detectedText.isNullOrEmpty()) {
                    showError("No text detected in image")
                    return@launch
                }
                
                // Tampilkan teks yang terdeteksi
                displayTextResults(bitmap, detectedText, keywords ?: emptyList())
                
                // Simpan untuk navigasi ke artikel
                viewModel.setTextDetectionResult(detectedText, keywords ?: emptyList())
                
                // Navigasi ke rekomendasi artikel jika ada teks yang terdeteksi
                navigateToArticleRecommendation(detectedText, keywords ?: emptyList())
            }
        }
    }
    
    /**
     * Navigasi ke fragment rekomendasi artikel dengan teks terdeteksi
     * Memanfaatkan konteks dari hasil deteksi lain untuk meningkatkan relevansi
     */
    private fun navigateToArticleRecommendation(detectedText: String, keywords: List<String>) {
        // Dapatkan konteks dari deteksi makanan sebelumnya jika ada
        val detectionContext = viewModel.foodDetectionResults.value?.firstOrNull()?.objectClass
        
        val bundle = Bundle().apply {
            putString("query", detectedText)
            putStringArray("keywords", keywords.toTypedArray())
            putBoolean("fromTextDetection", true)
            
            // Tambahkan konteks dari deteksi makanan sebelumnya
            detectionContext?.let {
                putString("detectionContext", it)
            }
        }
        
        // Tampilkan tombol untuk melihat artikel sambil menunggu navigasi
        view?.findViewById<Button>(R.id.article_recommendation_button)?.apply {
            text = "Lihat Artikel Rekomendasi"
            visibility = View.VISIBLE
            setOnClickListener {
                navigateToRecommendationFragment(bundle)
            }
        }
        
        // Tunda navigasi otomatis untuk memberi kesempatan pengguna membaca teks
        Handler(Looper.getMainLooper()).postDelayed({
            if (isAdded) { // Check if fragment is still attached
                navigateToRecommendationFragment(bundle)
            }
        }, 2500) // Tunggu 2.5 detik sebelum navigasi otomatis
    }
    
    /**
     * Navigate to recommendation fragment
     */
    private fun navigateToRecommendationFragment(bundle: Bundle) {
        val recommendationFragment = ArticleRecommendationFragment().apply {
            arguments = bundle
        }
        
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, recommendationFragment)
            .addToBackStack(null)
            .commit()
    }
    
    /**
     * Tampilkan hasil deteksi teks dengan UI yang lebih baik
     */
    private fun displayTextResults(bitmap: Bitmap, detectedText: String, keywords: List<String>) {
        capturedImageView.apply {
            visibility = View.VISIBLE
            setImageBitmap(bitmap)
        }
        
        resultTextView.apply {
            visibility = View.VISIBLE
            
            // Format teks hasil deteksi dengan highlighting keywords
            val formattedText = StringBuilder()
            formattedText.append("Teks terdeteksi:\n\n")
            
            // Tampilkan max 150 karakter dari teks terdeteksi
            val displayText = if (detectedText.length > 150) 
                              "${detectedText.take(150)}..." 
                              else detectedText
            formattedText.append(displayText)
            
            // Tampilkan keywords yang terdeteksi
            if (keywords.isNotEmpty()) {
                formattedText.append("\n\nKata kunci: ")
                formattedText.append(keywords.joinToString(", "))
            }
            
            formattedText.append("\n\nMenganalisis dan mencari artikel terkait...")
            
            text = formattedText.toString()
        }
        
        // Tampilkan tombol rekomendasi artikel
        view?.findViewById<Button>(R.id.article_recommendation_button)?.visibility = View.VISIBLE
        
        // Save teks yang terdeteksi ke database
        val imageBytes = compressBitmap(bitmap)
        saveTextDetectionResultToDatabase(
            detectedText.take(50), // Ambil sebagian teks sebagai objek
            1.0f, // Confidence selalu 1.0 untuk OCR
            keywords,
            imageBytes
        )
    }
    
    /**
     * Simpan hasil deteksi teks ke database dengan keywords
     */
    private fun saveTextDetectionResultToDatabase(
        textSummary: String,
        confidence: Float,
        keywords: List<String>,
        imageBytes: ByteArray
    ) {
        // Simpan ke database melalui ViewModel
        viewModel.saveDetectionResult(
            objectClass = "Text: ${if (textSummary.length > 30) textSummary.take(30) + "..." else textSummary}",
            confidence = confidence,
            detectionType = DetectionViewModel.DetectionMode.TEXT, // Simpan sebagai tipe TEXT yang baru
            imageData = imageBytes,
            nutritionInfoJson = if (keywords.isNotEmpty()) {
                // Simpan keywords dalam format JSON
                "{\"keywords\":\"${keywords.joinToString(",")}\",\"source\":\"ocr\"}"
            } else null
        )
    }
    
    /**
     * Simpan hasil deteksi makanan ke database
     */
    private fun saveDetectionResultToDatabase(
        objectClass: String,
        confidence: Float,
        recommendation: com.nutrigenius.ml.Recommendation?,
        imageBytes: ByteArray
    ) {
        // Simpan ke database melalui ViewModel
        viewModel.saveDetectionResult(
            objectClass = objectClass,
            confidence = confidence,
            detectionType = DetectionViewModel.DetectionMode.OBJECT,
            imageData = imageBytes, 
            nutritionInfoJson = recommendation?.let {
                // Serialize nutrition info jika ada
                "{\"calories\":${it.nutritionInfo.calories},\"protein\":${it.nutritionInfo.protein}," +
                "\"carbs\":${it.nutritionInfo.carbs},\"fat\":${it.nutritionInfo.fat}}"
            }
        )
    }
    
    /**
     * Simpan hasil deteksi wajah ke database
     */
    private fun saveFaceDetectionResultToDatabase(
        age: String,
        confidence: Float,
        gender: String?,
        imageBytes: ByteArray
    ) {
        // Simpan ke database melalui ViewModel
        viewModel.saveDetectionResult(
            objectClass = "Face: $age years ${gender ?: ""}",
            confidence = confidence,
            detectionType = DetectionViewModel.DetectionMode.FACE,
            imageData = imageBytes,
            nutritionInfoJson = null
        )
    }
    
    /**
     * Menampilkan hasil deteksi makanan
     */
    private fun displayFoodResults(bitmap: Bitmap, detections: List<DetectionResult>, recommendation: com.nutrigenius.ml.Recommendation?) {
        capturedImageView.apply {
            visibility = View.VISIBLE
            setImageBitmap(bitmap)
        }
        
        // Display result for the first detected object
        if (detections.isNotEmpty()) {
            val topDetection = detections[0]
            
            resultTextView.apply {
                visibility = View.VISIBLE
                text = if (recommendation != null) {
                    "Detected: ${topDetection.objectClass} (${String.format("%.1f", topDetection.confidence * 100)}%)\n" +
                    "Calories: ${recommendation.nutritionInfo.calories} kcal\n" +
                    "Protein: ${recommendation.nutritionInfo.protein}g\n" +
                    "Carbs: ${recommendation.nutritionInfo.carbs}g\n" +
                    "Fat: ${recommendation.nutritionInfo.fat}g"
                } else {
                    "Detected: ${topDetection.objectClass} (${String.format("%.1f", topDetection.confidence * 100)}%)\n" +
                    "No nutrition data available for this food."
                }
            }
            
            // Tampilkan tombol rekomendasi artikel jika deteksi berhasil
            view?.findViewById<Button>(R.id.article_recommendation_button)?.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    val bundle = Bundle().apply {
                        putString("query", topDetection.objectClass)
                        putBoolean("fromObjectDetection", true)
                    }
                    
                    val recommendationFragment = ArticleRecommendationFragment().apply {
                        arguments = bundle
                    }
                    
                    requireActivity().supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, recommendationFragment)
                        .addToBackStack(null)
                        .commit()
                }
            }
        } else {
            resultTextView.apply {
                visibility = View.VISIBLE
                text = "No objects detected"
            }
            
            // Sembunyikan tombol rekomendasi karena tidak ada objek yang terdeteksi
            view?.findViewById<Button>(R.id.article_recommendation_button)?.visibility = View.GONE
        }
    }
    
    /**
     * Menampilkan hasil deteksi wajah
     */
    private fun displayFaceResults(bitmap: Bitmap, faceResult: FaceResult?) {
        capturedImageView.apply {
            visibility = View.VISIBLE
            setImageBitmap(bitmap)
        }
        
        // Display results
        resultTextView.apply {
            visibility = View.VISIBLE
            text = if (faceResult != null) {
                "Face Detected (${String.format("%.1f", faceResult.confidence * 100)}%)\n" +
                "Estimated Age: ${faceResult.age} years\n" +
                "Gender: ${faceResult.gender}\n\n" +
                "This is a prototype of Growth Monitoring."
            } else {
                "No face detected"
            }
        }
        
        // Sembunyikan tombol rekomendasi karena tidak relevan untuk deteksi wajah
        view?.findViewById<Button>(R.id.article_recommendation_button)?.visibility = View.GONE
    }
    
    /**
     * Mengompres bitmap untuk penyimpanan memori yang efisien
     */
    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return outputStream.toByteArray()
    }
    
    /**
     * Resize bitmap untuk processing yang lebih efisien
     */
    private fun resizeBitmapForProcessing(bitmap: Bitmap): Bitmap {
        val maxDimension = 640 // Ukuran maksimum untuk processing
        val width = bitmap.width
        val height = bitmap.height
        val scaleFactor = maxDimension.toFloat() / max(width, height)
        
        return if (scaleFactor < 1) {
            Bitmap.createScaledBitmap(
                bitmap,
                (width * scaleFactor).toInt(),
                (height * scaleFactor).toInt(),
                true
            )
        } else {
            bitmap
        }
    }
    
    /**
     * Menggambar bounding box deteksi pada bitmap
     */
    private fun drawDetections(originalBitmap: Bitmap, detections: List<DetectionResult>): Bitmap {
        val bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        val textPaint = Paint().apply {
            color = Color.RED
            textSize = 32f
            style = Paint.Style.FILL
        }
        
        detections.forEach { detection ->
            // Draw bounding box
            canvas.drawRect(detection.rect, paint)
            
            // Draw label with confidence
            val label = "${detection.objectClass} ${String.format("%.1f", detection.confidence * 100)}%"
            canvas.drawText(
                label, 
                detection.rect.left,
                detection.rect.top - 10f,
                textPaint
            )
        }
        
        return bitmap
    }
    
    /**
     * Menggambar hasil deteksi wajah pada bitmap
     */
    private fun drawFaceDetection(originalBitmap: Bitmap, faceResult: FaceResult): Bitmap {
        val bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        val textPaint = Paint().apply {
            color = Color.GREEN
            textSize = 32f
            style = Paint.Style.FILL
        }
        
        // Draw face bounding box
        canvas.drawRect(faceResult.faceRect, paint)
        
        // Draw age estimate
        val label = "Age: ${faceResult.age}"
        canvas.drawText(
            label,
            faceResult.faceRect.left,
            faceResult.faceRect.top - 10f,
            textPaint
        )
        
        return bitmap
    }
    
    /**
     * Membebaskan memori bitmap yang tidak digunakan
     */
    private fun recycleBitmap(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
    
    /**
     * Menyimpan preferensi mode deteksi
     */
    private fun saveDetectionMode() {
        requireActivity().getPreferences(Context.MODE_PRIVATE).edit().apply {
            putInt("last_detection_mode", currentMode.ordinal)
            apply()
        }
    }
    
    /**
     * Memuat preferensi mode deteksi
     */
    private fun loadDetectionMode() {
        val savedMode = requireActivity().getPreferences(Context.MODE_PRIVATE)
            .getInt("last_detection_mode", 0)
        currentMode = DetectionViewModel.DetectionMode.values()[savedMode]
        // Update viewModel
        viewModel.setDetectionMode(currentMode)
    }
    
    /**
     * Menampilkan pesan error
     */
    private fun showError(message: String) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
            .setAction("OK") { }
            .show()
    }
    
    override fun onResume() {
        super.onResume()
        // Aktifkan orientation listener saat fragment aktif
        if (::orientationEventListener.isInitialized) {
            orientationEventListener.enable()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Nonaktifkan orientation listener saat fragment tidak aktif
        if (::orientationEventListener.isInitialized) {
            orientationEventListener.disable()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        objectDetector.close()
        faceDetector.close()
        textRecognizer.close()
    }
    
    companion object {
        private const val TAG = "ScannerFragment"
    }
} 