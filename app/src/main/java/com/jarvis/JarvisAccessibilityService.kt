package com.jarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class JarvisAccessibilityService : AccessibilityService() {

    private var tts: TextToSpeech? = null

    companion object {
        var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.setLanguage(Locale("hi", "IN"))
        }
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        tts?.shutdown()
        super.onDestroy()
    }

    fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun showRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun scroll(down: Boolean) {
        val root = rootInActiveWindow ?: return
        val node = findScrollable(root)
        node?.performAction(
            if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollable(child)
            if (found != null) return found
        }
        return null
    }

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "Screen par kuch nahi mil raha."
        val sb = StringBuilder()
        collectText(root, sb)
        val result = sb.toString().trim()
        return if (result.isEmpty()) "Is screen par koi text nahi mila." else result
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        if (!node.text.isNullOrBlank()) sb.append(node.text).append(". ")
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, sb) }
        }
    }

    fun tapByText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findByText(root, target.lowercase(Locale.ROOT))
        if (node != null) {
            var clickable: AccessibilityNodeInfo? = node
            while (clickable != null && !clickable.isClickable) {
                clickable = clickable.parent
            }
            (clickable ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        return false
    }

    private fun findByText(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase(Locale.ROOT)
        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT)
        if ((text != null && text.contains(target)) || (desc != null && desc.contains(target))) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findByText(child, target)
            if (found != null) return found
        }
        return null
    }
}
