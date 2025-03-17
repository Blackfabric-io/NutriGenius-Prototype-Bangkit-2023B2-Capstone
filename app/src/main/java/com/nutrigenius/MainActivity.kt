package com.nutrigenius

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nutrigenius.ui.ResultFragment
import com.nutrigenius.ui.ScannerFragment

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Load scanner fragment by default
        if (savedInstanceState == null) {
            loadFragment(ScannerFragment())
        }
        
        // Setup bottom navigation
        val bottomNav: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_scanner -> {
                    loadFragment(ScannerFragment())
                    return@setOnItemSelectedListener true
                }
                R.id.navigation_results -> {
                    loadFragment(ResultFragment())
                    return@setOnItemSelectedListener true
                }
                else -> return@setOnItemSelectedListener false
            }
        }
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
} 