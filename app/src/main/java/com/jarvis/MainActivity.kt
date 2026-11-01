package com.jarvis

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private lateinit var statusView: TextView
    private lateinit var engine: CommandEngine
    private var hindi = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val prefs = getSharedPreferences("jarvis", Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#050A14"))
            setPadding(40, 60, 40, 40)
        }

        val title = TextView(this).apply {
            text = "J.A.R.V.I.S"
            textSize = 32f
            setTextColor(Color.parseColor("#00D4FF"))
            gravity = Gravity.CENTER
        }

        statusView = TextView(this).apply {
            text = "Boss, mic dabao aur bolo, ya 'Hamesha sunta raho' on karo."
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(0, 24, 0, 24)
        }
        val scroll = ScrollView(this).apply { addView(statusView) }

        val keyBox = EditText(this).apply {
            hint = "API key (optional, baad mein)"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val saveButton = Button(this).apply {
            text = "API key save karo"
            setOnClickListener {
                val k = keyBox.text.toString().trim()
                if (k.isNotEmpty()) {
                    prefs.edit().putString("key", k).apply()
                    keyBox.setText("")
                    statusView.text = "API key save ho gayi, Boss."
                }
            }
        }

        val micButton = Button(this).apply {
            text = "🎤  Bolo (ek baar)"
            textSize = 18f
            setOnClickListener { startListening() }
        }

        val bgButton = Button(this).apply {
            text = "🟢 Hamesha sunta raho (ON karo)"
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                    return@setOnClickListener
                }
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3)
                }
                val svc = Intent(this@MainActivity, JarvisService::class.java)
                if (text.contains("ON")) {
                    startForegroundService(svc)
                    text = "🔴 Background service (OFF karo)"
                    statusView.text = "Ab Jarvis hamesha sunega. 'Jarvis' bolkar command do, Boss."
                } else {
                    stopService(svc)
                    text = "🟢 Hamesha sunta raho (ON karo)"
                    statusView.text = "Background service band kar diya."
                }
            }
        }

        root.addView(title)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(keyBox)
        root.addView(saveButton)
        root.addView(micButton)
        root.addView(bgButton)
        setContentView(root)

        engine = CommandEngine(this, tts) { reply ->
            statusView.text = statusView.text.toString() + "\n\nJarvis: " + reply
        }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusView.text = "Is phone mein speech recognition nahi mila."
            return
        }
        tts?.stop()
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(listener)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (hindi) "hi-IN" else "en-IN")
        }
        statusView.text = "Sun raha hoon..."
        recognizer?.startListening(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(p: Bundle?) {}
        override fun onEvent(e: Int, p: Bundle?) {}
        override fun onError(error: Int) {
            statusView.text = "Awaaz samajh nahi aayi (code $error). Dobara bolo."
        }
        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (text.isNullOrBlank()) {
                statusView.text = "Kuch sunai nahi diya. Dobara bolo."
            } else {
                statusView.text = "Aap: $text"
                engine.handle(text)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale("hi", "IN"))
            ttsReady = true
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
