package com.jarvis

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.tts.TextToSpeech
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandEngine(
    private val context: Context,
    private val tts: TextToSpeech?,
    private val onSay: (String) -> Unit
) {
    private val aliases = listOf(
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वॉट्सऐप" to "whatsapp",
        "यू ट्यूब" to "youtube", "यूट्यूब" to "youtube",
        "इंस्टाग्राम" to "instagram", "फेसबुक" to "facebook",
        "क्रोम" to "chrome", "गूगल" to "google",
        "मैप्स" to "maps", "मैप" to "maps",
        "कैमरा" to "camera", "गैलरी" to "gallery",
        "सेटिंग्स" to "settings", "सेटिंग" to "settings",
        "टॉर्च" to "torch", "फ्लैशलाइट" to "torch",
        "कॉल" to "call",
        "खोलो" to "kholo", "ओपन" to "kholo",
        "चालू" to "chalu", "बंद" to "band",
        "टाइमर" to "timer", "अलार्म" to "alarm",
        "मिनट" to "minute", "घंटे" to "ghanta", "घंटा" to "ghanta",
        "वॉल्यूम" to "volume", "बढ़ाओ" to "badhao", "घटाओ" to "ghatao",
        "सर्च" to "search", "खोजो" to "dhundo", "ढूंढो" to "dhundo",
        "वाईफाई" to "wifi", "ब्लूटूथ" to "bluetooth",
        "तुम कौन हो" to "kaun ho", "आप कौन हो" to "kaun ho",
        "नमस्ते" to "namaste", "हेलो" to "namaste",
        "कितने बजे" to "kitne baje", "टाइम" to "time", "समय" to "time",
        "तारीख" to "tarikh", "रास्ता" to "raasta",
        "पांच" to "5", "तीन" to "3", "चार" to "4", "दस" to "10"
    )

    private val fillers = setOf(
        "jarvis", "please", "ko", "ka", "ki", "ke", "par", "pe", "mein", "me", "se",
        "karo", "kar", "do", "de", "dena", "ek", "mujhe", "hey", "hello", "zara", "na",
        "aur", "tum", "mera", "meri"
    )

    private fun has(t: String, vararg words: String) = words.any { t.contains(it) }

    private fun query(t: String, vararg remove: String) =
        t.split(" ").filter { it.isNotBlank() && it !in fillers && it !in remove }
            .joinToString(" ")

    fun handle(raw: String) {
        var t = raw.lowercase(Locale.ROOT)
        for ((from, to) in aliases) t = t.replace(from, to)
        t = t.replace(Regex("[.,!?।]"), " ").replace(Regex("\\s+"), " ").trim()

        try {
            when {
                has(t, "torch", "flashlight") -> setTorch(!has(t, "band", "off"))
                has(t, "volume") -> changeVolume(!has(t, "ghatao", "kam", "down"))
                has(t, "timer") -> setTimer(t)
                has(t, "alarm") -> {
                    context.startActivity(
                        Intent(AlarmClock.ACTION_SHOW_ALARMS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    say("Alarm ki list khol di, Boss.")
                }
                has(t, "kitne baje", "time", "tarikh") -> tellTime(t)
                has(t, "call") -> doCall(t)
                has(t, "youtube") -> {
                    val q = query(t, "youtube", "search", "play", "chalao", "kholo")
                    if (q.isEmpty()) openApp("youtube") else {
                        view("https://www.youtube.com/results?search_query=" + Uri.encode(q))
                        say("YouTube par $q dhundh raha hoon.")
                    }
                }
                has(t, "maps", "raasta") -> {
                    val q = query(t, "maps", "raasta", "kholo", "dikhao", "tak", "jana", "hai")
                    if (q.isEmpty()) openApp("maps") else {
                        view("geo:0,0?q=" + Uri.encode(q))
                        say("$q ka raasta dikha raha hoon.")
                    }
                }
                has(t, "wifi") -> {
                    context.startActivity(
                        Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    say("Wifi settings khol di.")
                }
                has(t, "bluetooth") -> {
                    context.startActivity(
                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    say("Bluetooth settings khol di.")
                }
                has(t, "kholo", "chalu karo", "start") ->
                    openApp(query(t, "kholo", "chalu", "start"))
                has(t, "search", "google", "dhundo") -> {
                    val q = query(t, "search", "google", "dhundo", "karo")
                    if (q.isEmpty()) openApp("google") else {
                        view("https://www.google.com/search?q=" + Uri.encode(q))
                        say("Google par $q dhundh raha hoon.")
                    }
                }
                has(t, "kaun ho") -> say("Main Jarvis hoon, Boss. Aapka personal assistant.")
                has(t, "namaste") -> say("Namaste Boss, kya hukm hai?")
                else -> say("Boss, ye command mujhe abhi nahi aata.")
            }
        } catch (e: Exception) {
            say("Boss, ye kaam nahi ho paya: ${e.message}")
        }
    }

    private fun say(text: String) {
        onSay(text)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    private fun view(url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun openApp(name: String) {
        if (name.isBlank()) { say("Kaunsa app kholun, Boss?"); return }
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val match = apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase(Locale.ROOT) == name
        } ?: apps.firstOrNull {
            val label = it.loadLabel(pm).toString().lowercase(Locale.ROOT)
            label.contains(name) || (label.length >= 3 && name.contains(label))
        }
        if (match == null) { say("$name naam ka app nahi mila, Boss."); return }
        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            say("${match.loadLabel(pm)} khol diya.")
        }
    }

    private fun setTorch(on: Boolean) {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) { say("Is phone mein torch nahi mili."); return }
        cm.setTorchMode(id, on)
        say(if (on) "Torch chalu kar di." else "Torch band kar di.")
    }

    private fun changeVolume(up: Boolean) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val dir = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        repeat(3) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI) }
        say(if (up) "Volume badha diya." else "Volume kam kar diya.")
    }

    private fun setTimer(t: String) {
        val n = Regex("\\d+").find(t)?.value?.toIntOrNull()
        if (n == null) { say("Kitne minute ka timer lagaun, Boss?"); return }
        val secs = when {
            has(t, "ghanta", "hour") -> n * 3600
            has(t, "second", "sec") -> n
            else -> n * 60
        }
        context.startActivity(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, secs)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        say("Timer laga diya, Boss.")
    }

    private fun tellTime(t: String) {
        if (has(t, "tarikh")) {
            val d = SimpleDateFormat("d MMMM yyyy", Locale("hi", "IN")).format(Date())
            say("Aaj ki tareekh $d hai.")
        } else {
            val s = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date())
            say("Abhi $s baje hain, Boss.")
        }
    }

    private fun dial(number: String) {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        say("Dialer khol diya, call ka button aap dabana, Boss.")
    }

    private fun doCall(t: String) {
        val digits = t.filter { it.isDigit() }
        if (digits.length >= 5) { dial(digits); return }
        val name = query(t, "call", "phone", "dial", "lagao", "milao")
        if (name.isEmpty()) { say("Kisko call karun, Boss?"); return }
        var found: String? = null
        var foundName = ""
        val c = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            arrayOf("%$name%"), null
        )
        c?.use { if (it.moveToFirst()) { found = it.getString(0); foundName = it.getString(1) } }
        val number = found
        if (number == null) say("$name naam ka contact nahi mila.")
        else { say("$foundName mil gaya."); dial(number) }
    }
}
