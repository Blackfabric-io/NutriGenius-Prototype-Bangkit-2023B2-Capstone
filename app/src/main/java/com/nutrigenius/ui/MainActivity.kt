package com.nutrigenius.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nutrigenius.R
import com.nutrigenius.ml.DetectionViewModel

/**
 * MainActivity - Activity utama untuk aplikasi NutriGenius
 * 
 * Activity ini mengelola navigasi antar fragment, termasuk ScannerFragment
 * untuk deteksi objek, HistoryFragment untuk melihat riwayat deteksi,
 * dan ArticleRecommendationFragment untuk rekomendasi artikel.
 */
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Setup bottom navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when(menuItem.itemId) {
                R.id.nav_scanner -> {
                    loadFragment(ScannerFragment())
                    true
                }
                R.id.nav_recommendation -> {
                    loadFragment(ArticleRecommendationFragment())
                    true
                }
                R.id.nav_history -> {
                    loadFragment(HistoryFragment())
                    true
                }
                R.id.nav_profile -> {
                    // Placeholder untuk fitur profil
                    showComingSoonFragment()
                    true
                }
                else -> false
            }
        }
        
        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(ScannerFragment())
        }
    }
    
    /**
     * Memuat fragment ke container utama
     * 
     * @param fragment Fragment yang akan dimuat
     */
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    
    /**
     * Menampilkan fragment untuk fitur yang belum diimplementasikan
     */
    private fun showComingSoonFragment() {
        val comingSoonFragment = Fragment().apply {
            arguments = Bundle().apply {
                putString("message", "This feature is coming soon!")
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, comingSoonFragment)
            .commit()
    }
} 