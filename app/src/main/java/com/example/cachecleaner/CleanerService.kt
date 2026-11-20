package com.example.cachecleaner

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CleanerService : AccessibilityService() {
    private enum class Stage { IDLE, FORCE, CONFIRM, STORAGE, CLEAR }

    companion object { var instance: CleanerService? = null }

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private var doForce = true
    private var doCache = true
    private var stage = Stage.IDLE
    private var gen = 0

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onInterrupt() {}

    fun start(pkgs: List<String>, force: Boolean, cache: Boolean) {
        if (!force && !cache) return
        queue.clear(); queue.addAll(pkgs)
        doForce = force; doCache = cache
        next()
    }

    private fun next() {
        val pkg = queue.removeFirstOrNull()
        if (pkg == null) {
            stage = Stage.IDLE
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        stage = if (doForce) Stage.FORCE else Stage.STORAGE
        val my = ++gen
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        // Safety timeout: skip this app if the Settings UI isn't recognised
        handler.postDelayed({ if (gen == my && stage != Stage.IDLE) next() }, 10000)
    }

    private fun finishApp() {
        stage = Stage.IDLE
        val my = gen
        handler.postDelayed({ if (gen == my) next() }, 700)
    }

    private fun afterForce() { if (doCache) stage = Stage.STORAGE else finishApp() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (stage == Stage.IDLE) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.contains("settings", true) != true) return

        when (stage) {
            Stage.FORCE -> {
                val n = find(root) { it.equals("force stop", true) } ?: return
                val target = clickable(n)
                if (target != null && target.isEnabled) { target.performAction(AccessibilityNodeInfo.ACTION_CLICK); stage = Stage.CONFIRM }
                else afterForce()
            }
            Stage.CONFIRM -> {
                if (find(root) { it.equals("cancel", true) } == null) return // dialog not up yet
                val n = find(root) { it.equals("ok", true) } ?: find(root) { it.equals("force stop", true) } ?: return
                clickable(n)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                afterForce()
            }
            Stage.STORAGE -> {
                val n = find(root) { it.startsWith("storage", true) } ?: return
                clickable(n)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                stage = Stage.CLEAR
            }
            Stage.CLEAR -> {
                val n = find(root) { it.equals("clear cache", true) } ?: return
                val target = clickable(n)
                if (target != null && target.isEnabled) target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                finishApp()
            }
            else -> {}
        }
    }

    private fun find(n: AccessibilityNodeInfo?, p: (String) -> Boolean): AccessibilityNodeInfo? {
        if (n == null) return null
        val t = n.text?.toString()?.trim()
        if (t != null && p(t)) return n
        for (i in 0 until n.childCount) find(n.getChild(i), p)?.let { return it }
        return null
    }

    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        return n
    }
}
