package com.hamrahghatat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // اگر نیاز به استفاده از LayoutInflater یا ViewGroup داری، اینجا بنویس
        // val inflater = LayoutInflater.from(this)
        // val container: ViewGroup = findViewById(R.id.container)
    }
}
