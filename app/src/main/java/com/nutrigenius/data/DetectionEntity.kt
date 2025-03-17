package com.nutrigenius.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.nutrigenius.ml.DetectionViewModel
import java.util.Date

/**
 * DetectionEntity - Entity class untuk menyimpan hasil deteksi di database
 *
 * Entity ini menyimpan informasi tentang deteksi yang dilakukan,
 * termasuk hasil deteksi dan gambar yang diproses.
 */
@Entity(tableName = "detection_results")
data class DetectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Waktu deteksi
    val timestamp: Date,
    
    // Jenis deteksi (makanan atau wajah)
    val detectionType: DetectionViewModel.DetectionMode,
    
    // Objek yang terdeteksi (nama makanan atau informasi wajah)
    val objectClass: String,
    
    // Tingkat kepercayaan hasil deteksi (0.0-1.0)
    val confidence: Float,
    
    // Informasi nutrisi dalam format JSON (untuk deteksi makanan)
    val nutritionInfoJson: String?,
    
    // Data gambar dalam bentuk byte array
    val imageData: ByteArray?
) {
    // Override equals dan hashCode untuk menangani ByteArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DetectionEntity

        if (id != other.id) return false
        if (timestamp != other.timestamp) return false
        if (detectionType != other.detectionType) return false
        if (objectClass != other.objectClass) return false
        if (confidence != other.confidence) return false
        if (nutritionInfoJson != other.nutritionInfoJson) return false
        if (imageData != null) {
            if (other.imageData == null) return false
            if (!imageData.contentEquals(other.imageData)) return false
        } else if (other.imageData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + detectionType.hashCode()
        result = 31 * result + objectClass.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + (nutritionInfoJson?.hashCode() ?: 0)
        result = 31 * result + (imageData?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * TypeConverter untuk Date dalam Room database
 */
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
    
    @TypeConverter
    fun fromDetectionMode(mode: DetectionViewModel.DetectionMode): Int {
        return mode.ordinal
    }
    
    @TypeConverter
    fun toDetectionMode(value: Int): DetectionViewModel.DetectionMode {
        return DetectionViewModel.DetectionMode.values()[value]
    }
} 