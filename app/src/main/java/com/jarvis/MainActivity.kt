package com.jarvis

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private val MODEL = "claude-opus-5-5"
    private val SYSTEM = "Tum Jarvis ho, Iron Man jaisa smart aur respectful voice assistant. " +
        "User ko 'Boss' bolo. Hinglish ya Hindi mein chhote, saaf jawab do (2-3 vaakya), " +
        "kyunki tumhara jawab bolkar sunaya jayega. Emoji, bullets aur markdown mat use karo."

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private lateinit var statusView: TextView
    private lateinit var micButton: Button
    private val client = OkHttpClient()
    private val history = JSONArray()

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
            text = "Boss, mic dabao aur bolo."
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(0, 24, 0, 24)
        }
        val scroll = ScrollView(this).apply {
            addView(statusView)
        }

        val keyBox = EditText(this).apply {
            hint = if (prefs.getString("key", "").isNullOrEmpty())
                "Claude API key yahan daalo" else "Key saved hai (nayi daalni ho to yahan)"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val saveButton = Button(this).apply {
            text = "Key save karo"
            setOnClickListener {
                val k = keyBox.text.toString().trim()
                if (k.isNotEmpty()) {
                    prefs.edit().putString("key", k).apply()
                    keyBox.setText("")
                    statusView.text = "Key save ho gayi, Boss."
                }
            }
        }

        micButton = Button(this).apply {
            text = "🎤  Bolo"
            textSize = 20f
            setOnClickListener { startListening() }
        }

        root.addView(title)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(keyBox)
        root.addView(saveButton)
        root.addView(micButton)
        setContentView(root)
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }
        statusView.text = "Sun raha hoon..."
        recognizer?.startListening(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            statusView.text = "Mic ki permission chahiye, Boss."
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
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
                statusView.text = "Aap: $text\n\nJarvis soch raha hai..."
                askClaude(text)
            }
        }
    }

    private fun askClaude(userText: String) {
        val key = getSharedPreferences("jarvis", Context.MODE_PRIVATE)
            .getString("key", "") ?: ""
        if (key.isEmpty()) {
            statusView.text = "Pehle neeche Claude API key daalkar save karo, Boss."
            return
        }

        history.put(JSONObject().put("role", "user").put("content", userText))
        while (history.length() > 20) { history.remove(0); history.remove(0) }

        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 400)
            .put("system", SYSTEM)
            .put("messages", JSONArray(history.toString()))
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    history.remove(history.length() - 1)
                    statusView.text = "Internet ya network ki dikkat: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string() ?: ""
                runOnUiThread {
                    if (!response.isSuccessful) {
                        history.remove(history.length() - 1)
                        statusView.text = "Error ${response.code}: ${raw.take(300)}"
                        return@runOnUiThread
                    }
                    try {
                        val reply = JSONObject(raw)
                            .getJSONArray("content")
                            .getJSONObject(0)
                            .getString("text")
                        history.put(JSONObject().put("role", "assistant").put("content", reply))
                        statusView.text = "Aap: $userText\n\nJarvis: $reply"
                        if (ttsReady) {
                            tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
                        }
                    } catch (e: Exception) {
                        history.remove(history.length() - 1)
                        statusView.text = "Jawab padhne mein dikkat: ${e.message}"
                    }
                }
            }
        })
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
