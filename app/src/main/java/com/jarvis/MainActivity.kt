package com.jarvis

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#050A14"))
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "J.A.R.V.I.S"
            textSize = 36f
            setTextColor(Color.parseColor("#00D4FF"))
            gravity = Gravity.CENTER
        }

        val sub = TextView(this).apply {
            text = "Yes Boss, main zinda hoon."
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 48)
        }

        val button = Button(this).apply {
            text = "Jarvis ko bolne do"
            setOnClickListener {
                if (ready) {
                    tts?.speak(
                        "नमस्ते बॉस, मैं जार्विस हूँ। मैं ज़िंदा हूँ।",
                        TextToSpeech.QUEUE_FLUSH, null, "jarvis1"
                    )
                }
            }
        }

        layout.addView(title)
        layout.addView(sub)
        layout.addView(button)
        setContentView(layout)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale("hi", "IN"))
            ready = true
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
