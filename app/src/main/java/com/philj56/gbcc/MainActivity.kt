package com.philj56.gbcc

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun switchToGL(view: View) {
        var intent = Intent(this, GLActivity::class.java).apply {

        }
        startActivity(intent)
    }
}
