package com.nutrigenius.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class FoodDetector(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var labelList: List<String> = emptyList()
    private val modelName = "food_model.tflite"
    private val labelName = "labels.txt"
    private val imageSize = 224 // Input size for the model
    
    init {
        try {
            // Load model
            val model = loadModelFile(context, modelName)
            interpreter = Interpreter(model)
            
            // Load labels
            labelList = FileUtil.loadLabels(context, labelName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun detectFood(bitmap: Bitmap): Pair<String, Float> {
        // Preprocess the image
        val processedImage = preprocessImage(bitmap)
        
        // Create output buffer
        val outputBuffer = Array(1) { FloatArray(labelList.size) }
        
        // Run inference
        interpreter?.run(processedImage, outputBuffer)
        
        // Get results
        val result = outputBuffer[0]
        val maxIndex = result.indices.maxByOrNull { result[it] } ?: 0
        val confidence = result[maxIndex]
        val foodName = if (maxIndex < labelList.size) labelList[maxIndex] else "Unknown"
        
        return Pair(foodName, confidence)
    }
    
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(imageSize, imageSize, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val processedImage = imageProcessor.process(tensorImage)
        
        return processedImage.buffer
    }
    
    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    fun close() {
        interpreter?.close()
    }
} 