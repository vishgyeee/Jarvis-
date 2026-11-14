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

    private val SYSTEM = "Tum Jarvis ho, Iron Man jaisa smart aur respectful voice assistant. " +
        "User ko 'Boss' bolo. Hinglish ya Hindi mein chhote, saaf jawab do (2-3 vaakya). " +
        "Emoji, bullets aur markdown mat use karo."

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private lateinit var statusView: TextView
    private lateinit var engine: CommandEngine
    private lateinit var providerButton: Button
    private val client = OkHttpClient()
    private val history = JSONArray()
    private var hindi = true
    // 0 = Claude, 1 = Gemini, 2 = OpenRouter
    private var provider = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val prefs = getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        provider = prefs.getInt("provider", 0)

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
        val scroll = ScrollView(this).apply { addView(statusView) }

        providerButton = Button(this).apply {
            text = "Dimaag: " + providerName()
            setOnClickListener {
                provider = (provider + 1) % 3
                prefs.edit().putInt("provider", provider).apply()
                text = "Dimaag: " + providerName()
                statusView.text = "Ab " + providerName() + " use hoga, Boss. Neeche iski key daalo."
            }
        }

        val keyBox = EditText(this).apply {
            hint = "Is provider ki API key daalo"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val saveButton = Button(this).apply {
            text = "Key save karo"
            setOnClickListener {
                val k = keyBox.text.toString().trim()
                if (k.isNotEmpty()) {
                    prefs.edit().putString("key_$provider", k).apply()
                    keyBox.setText("")
                    statusView.text = providerName() + " ki key save ho gayi, Boss."
                }
            }
        }

        val micButton = Button(this).apply {
            text = "🎤  Bolo"
            textSize = 20f
            setOnClickListener { startListening() }
        }

        root.addView(title)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(providerButton)
        root.addView(keyBox)
        root.addView(saveButton)
        root.addView(micButton)
        setContentView(root)

        engine = CommandEngine(this, tts) { reply ->
            statusView.text = statusView.text.toString() + "\n\nJarvis: " + reply
        }
    }

    private fun providerName(): String = when (provider) {
        0 -> "Claude"
        1 -> "Gemini"
        else -> "OpenRouter"
    }

    private fun currentKey(): String {
        val prefs = getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        return prefs.getString("key_$provider", "") ?: ""
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
                handleOrAsk(text)
            }
        }
    }

    // Pehle fixed commands try karo, wo na samjhe to AI ko bhejo
    private fun handleOrAsk(raw: String) {
        val t = raw.lowercase(Locale.ROOT)
        val knownWords = listOf(
            "torch", "volume", "timer", "alarm", "kitne baje", "time", "tarikh",
            "call", "youtube", "maps", "raasta", "wifi", "bluetooth", "kholo",
            "chalu karo", "search", "google", "kaun ho", "namaste", "व्हाट्सएप",
            "टॉर्च", "टाइमर", "कॉल", "यूट्यूब", "खोलो"
        )
        if (knownWords.any { t.contains(it) }) {
            engine.handle(raw)
        } else {
            askAI(raw)
        }
    }

    private fun askAI(userText: String) {
        val key = currentKey()
        if (key.isEmpty()) {
            val reply = "Boss, ye command mujhe nahi aata. ${providerName()} ki API key daalo to main har sawal ka jawab de paunga."
            statusView.text = statusView.text.toString() + "\n\nJarvis: " + reply
            tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
            return
        }
        statusView.text = statusView.text.toString() + "\n\nJarvis soch raha hai..."

        history.put(JSONObject().put("role", "user").put("content", userText))
        while (history.length() > 20) { history.remove(0); history.remove(0) }

        when (provider) {
            0 -> askClaude(userText, key)
            1 -> askGemini(userText, key)
            else -> askOpenRouter(userText, key)
        }
    }

    private fun say(reply: String) {
        history.put(JSONObject().put("role", "assistant").put("content", reply))
        statusView.text = statusView.text.toString() + "\n\nJarvis: " + reply
        tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    private fun showError(msg: String) {
        if (history.length() > 0) history.remove(history.length() - 1)
        statusView.text = statusView.text.toString() + "\n\n$msg"
    }

    // ---------- Claude ----------
    private fun askClaude(userText: String, key: String) {
        val body = JSONObject()
            .put("model", "claude-opus-5-5")
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
                runOnUiThread { showError("Network error: ${e.message}") }
            }
            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string() ?: ""
                runOnUiThread {
                    if (!response.isSuccessful) { showError("Error ${response.code}: ${raw.take(300)}"); return@runOnUiThread }
                    try {
                        val reply = JSONObject(raw).getJSONArray("content").getJSONObject(0).getString("text")
                        say(reply)
                    } catch (e: Exception) { showError("Jawab padhne mein dikkat: ${e.message}") }
                }
            }
        })
    }

    // ---------- Gemini ----------
    private fun askGemini(userText: String, key: String) {
        val contents = JSONArray()
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
        contents.put(JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", "Theek hai Boss."))))
        for (i in 0 until history.length()) {
            val m = history.getJSONObject(i)
            val role = if (m.getString("role") == "assistant") "model" else "user"
            contents.put(JSONObject().put("role", role).put("parts",
                JSONArray().put(JSONObject().put("text", m.getString("content")))))
        }
        val body = JSONObject().put("contents", contents)
            .toString().toRequestBody("application/json".toMediaType())

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$key"
        val request = Request.Builder().url(url)
            .addHeader("content-type", "application/json")
            .post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { showError("Network error: ${e.message}") }
            }
            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string() ?: ""
                runOnUiThread {
                    if (!response.isSuccessful) { showError("Error ${response.code}: ${raw.take(300)}"); return@runOnUiThread }
                    try {
                        val reply = JSONObject(raw)
                            .getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts")
                            .getJSONObject(0).getString("text")
                        say(reply)
                    } catch (e: Exception) { showError("Jawab padhne mein dikkat: ${e.message}") }
                }
            }
        })
    }

    // ---------- OpenRouter (Qwen / GPT / koi bhi) ----------
    private fun askOpenRouter(userText: String, key: String) {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", SYSTEM))
        for (i in 0 until history.length()) messages.put(history.getJSONObject(i))

        val body = JSONObject()
            .put("model", "qwen/qwen3.6-plus-preview:free")
            .put("messages", messages)
            .toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("content-type", "application/json")
            .post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { showError("Network error: ${e.message}") }
            }
            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string() ?: ""
                runOnUiThread {
                    if (!response.isSuccessful) { showError("Error ${response.code}: ${raw.take(300)}"); return@runOnUiThread }
                    try {
                        val reply = JSONObject(raw)
                            .getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content")
                        say(reply)
                    } catch (e: Exception) { showError("Jawab padhne mein dikkat: ${e.message}") }
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
