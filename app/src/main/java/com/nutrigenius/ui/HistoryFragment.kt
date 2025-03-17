package com.nutrigenius.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.nutrigenius.R
import com.nutrigenius.data.DetectionEntity
import com.nutrigenius.ml.DetectionViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * HistoryFragment - Fragment untuk menampilkan riwayat deteksi
 *
 * Fragment ini menampilkan riwayat deteksi yang telah disimpan di database,
 * dan menyediakan filter untuk melihat jenis deteksi tertentu.
 */
class HistoryFragment : Fragment() {
    
    // ViewModel untuk akses data
    private val viewModel: DetectionViewModel by activityViewModels()
    
    // UI Components
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var tabLayout: TabLayout
    
    // Adapter untuk RecyclerView
    private val adapter = DetectionAdapter { detection ->
        showDetectionDetails(detection)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize UI components
        recyclerView = view.findViewById(R.id.historyRecyclerView)
        emptyView = view.findViewById(R.id.emptyHistoryText)
        tabLayout = view.findViewById(R.id.historyTabLayout)
        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        
        // Setup tab selection listener
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                updateHistoryList(tab.position)
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        
        // Clear history button
        view.findViewById<View>(R.id.clearHistoryButton).setOnClickListener {
            showClearHistoryDialog()
        }
        
        // Load initial data (All)
        updateHistoryList(0)
    }
    
    /**
     * Menampilkan dialog konfirmasi untuk menghapus semua riwayat
     */
    private fun showClearHistoryDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear History")
            .setMessage("Are you sure you want to delete all detection history? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteAllDetections()
                Snackbar.make(requireView(), "History cleared", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Mengupdate daftar riwayat berdasarkan filter yang dipilih
     * 
     * @param tabPosition posisi tab yang dipilih (0=All, 1=Food, 2=Face)
     */
    private fun updateHistoryList(tabPosition: Int) {
        // Observe appropriate LiveData based on selected tab
        when (tabPosition) {
            0 -> { // All
                viewModel.allDetections.observe(viewLifecycleOwner) { detections ->
                    updateUI(detections)
                }
            }
            1 -> { // Food
                viewModel.foodDetections.observe(viewLifecycleOwner) { detections ->
                    updateUI(detections)
                }
            }
            2 -> { // Face
                viewModel.faceDetections.observe(viewLifecycleOwner) { detections ->
                    updateUI(detections)
                }
            }
        }
    }
    
    /**
     * Memperbarui UI berdasarkan data yang diterima
     */
    private fun updateUI(detections: List<DetectionEntity>) {
        if (detections.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            adapter.submitList(detections)
        }
    }
    
    /**
     * Menampilkan dialog dengan detail hasil deteksi
     */
    private fun showDetectionDetails(detection: DetectionEntity) {
        val detectionType = when (detection.detectionType) {
            DetectionViewModel.DetectionMode.OBJECT -> "Food Detection"
            DetectionViewModel.DetectionMode.FACE -> "Face Detection"
        }
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDate = dateFormat.format(detection.timestamp)
        
        val bitmap = detection.imageData?.let { 
            BitmapFactory.decodeByteArray(it, 0, it.size) 
        }
        
        val nutritionInfo = if (detection.nutritionInfoJson != null) {
            "\n\nNutrition Information:\n${detection.nutritionInfoJson}"
        } else {
            ""
        }
        
        val detailView = layoutInflater.inflate(R.layout.dialog_detection_detail, null)
        detailView.findViewById<TextView>(R.id.detailTitle).text = detection.objectClass
        detailView.findViewById<TextView>(R.id.detailTimestamp).text = "Detected on: $formattedDate"
        detailView.findViewById<TextView>(R.id.detailConfidence).text = 
            "Confidence: ${String.format("%.1f", detection.confidence * 100)}%"
        detailView.findViewById<TextView>(R.id.detailExtraInfo).text = nutritionInfo
        
        val imageView = detailView.findViewById<ImageView>(R.id.detailImage)
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
            imageView.visibility = View.VISIBLE
        } else {
            imageView.visibility = View.GONE
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(detectionType)
            .setView(detailView)
            .setPositiveButton("Close", null)
            .setNegativeButton("Delete") { _, _ ->
                viewModel.deleteDetection(detection.id)
                Snackbar.make(requireView(), "Detection deleted", Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }
    
    /**
     * Adapter untuk menampilkan item deteksi dalam RecyclerView
     */
    private class DetectionAdapter(
        private val onItemClick: (DetectionEntity) -> Unit
    ) : ListAdapter<DetectionEntity, DetectionAdapter.DetectionViewHolder>(DetectionDiffCallback()) {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetectionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_detection, parent, false)
            return DetectionViewHolder(view, onItemClick)
        }
        
        override fun onBindViewHolder(holder: DetectionViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
        
        /**
         * ViewHolder untuk item deteksi
         */
        class DetectionViewHolder(
            itemView: View,
            private val onItemClick: (DetectionEntity) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            
            private val imageView: ImageView = itemView.findViewById(R.id.detectionImageView)
            private val typeText: TextView = itemView.findViewById(R.id.detectionTypeText)
            private val nameText: TextView = itemView.findViewById(R.id.detectionNameText)
            private val confidenceText: TextView = itemView.findViewById(R.id.confidenceText)
            private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
            
            /**
             * Mengikat data deteksi ke tampilan
             */
            fun bind(detection: DetectionEntity) {
                // Set detection type
                val typeLabel = when (detection.detectionType) {
                    DetectionViewModel.DetectionMode.OBJECT -> "FOOD"
                    DetectionViewModel.DetectionMode.FACE -> "FACE"
                }
                typeText.text = typeLabel
                
                // Set detection name
                nameText.text = detection.objectClass
                
                // Set confidence
                confidenceText.text = 
                    "Confidence: ${String.format("%.1f", detection.confidence * 100)}%"
                
                // Set timestamp
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                timestampText.text = dateFormat.format(detection.timestamp)
                
                // Set image if available
                detection.imageData?.let {
                    val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                    imageView.setImageBitmap(bitmap)
                } ?: run {
                    // Set placeholder if no image
                    imageView.setImageResource(R.drawable.ic_image_placeholder)
                }
                
                // Set click listener
                itemView.setOnClickListener {
                    onItemClick(detection)
                }
            }
        }
        
        /**
         * DiffCallback untuk efisiensi update RecyclerView
         */
        private class DetectionDiffCallback : DiffUtil.ItemCallback<DetectionEntity>() {
            override fun areItemsTheSame(oldItem: DetectionEntity, newItem: DetectionEntity): Boolean {
                return oldItem.id == newItem.id
            }
            
            override fun areContentsTheSame(oldItem: DetectionEntity, newItem: DetectionEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
} 