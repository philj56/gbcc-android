package com.philj56.gbcc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun switchToGL(view: View) {
        val intent = Intent(this, GLActivity::class.java).apply {

        }
        startActivity(intent)
    }
}
