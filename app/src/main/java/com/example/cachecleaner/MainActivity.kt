package com.example.cachecleaner

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cachecleaner.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: AppAdapter
    private val all = mutableListOf<AppItem>()
    private var query = ""
    private val io = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("prefs", MODE_PRIVATE) }
    private val onBinder = Shizuku.OnBinderReceivedListener { runOnUiThread { refreshStatus() } }
    private val onDead = Shizuku.OnBinderDeadListener { runOnUiThread { refreshStatus() } }
    private val onPerm = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { refreshStatus() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(prefs.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        adapter = AppAdapter { updateSummary() }
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter
        b.search.doAfterTextChanged { query = it?.toString().orEmpty(); applyFilter() }
        b.chipSort.setOnCheckedChangeListener { _, _ -> applyFilter() }
        b.fabStart.setOnClickListener { startClean() }
        b.btnShizuku.setOnClickListener { onShizukuButton() }
        b.btnAccess.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        b.btnUsage.setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        Shizuku.addBinderReceivedListenerSticky(onBinder)
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onPerm)
        loadApps()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(onBinder)
        Shizuku.removeBinderDeadListener(onDead)
        Shizuku.removeRequestPermissionResultListener(onPerm)
        super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        refreshStatus()
        if (all.isNotEmpty()) loadCacheSizes()
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean { menuInflater.inflate(R.menu.menu_main, menu); return true }
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_select_all -> { adapter.setAllShown(true); true }
        R.id.action_clear_sel -> { all.forEach { it.checked = false }; adapter.refresh(); updateSummary(); true }
        R.id.action_theme -> {
            val modes = listOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)
            val names = listOf("System", "Light", "Dark")
            val cur = modes.indexOf(prefs.getInt("night", modes[0])).coerceAtLeast(0)
            val next = (cur + 1) % modes.size
            prefs.edit().putInt("night", modes[next]).apply()
            Toast.makeText(this, "Theme: ${names[next]}", Toast.LENGTH_SHORT).show()
            AppCompatDelegate.setDefaultNightMode(modes[next]); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun loadApps() {
        io.execute {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val items = pm.queryIntentActivities(launcher, 0)
                .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
                .filter { it.pkg != packageName }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
            runOnUiThread { all.clear(); all.addAll(items); applyFilter(); loadCacheSizes() }
        }
    }
    private fun loadCacheSizes() {
        if (!hasUsage()) return
        io.execute {
            val ssm = getSystemService(StorageStatsManager::class.java)
            val user = Process.myUserHandle()
            for (a in all) {
                a.cache = try { ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, a.pkg, user).cacheBytes }
                catch (e: Exception) { -1 }
            }
            runOnUiThread { applyFilter() }
        }
    }
    private fun applyFilter() {
        var list = all.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
        if (b.chipSort.isChecked) list = list.sortedByDescending { it.cache }
        adapter.submit(list); updateSummary()
    }
    private fun updateSummary() {
        val sel = all.filter { it.checked }
        val known = sel.filter { it.cache >= 0 }.sumOf { it.cache }
        b.txtSummary.text = if (!hasUsage()) "${sel.size} selected · grant usage access to see cache sizes"
            else "${sel.size} selected · ${Formatter.formatShortFileSize(this, known)} cache"
    }
    private fun shizukuState(): Int = try {
        when {
            !Shizuku.pingBinder() -> 0
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> 2
            else -> 1
        }
    } catch (t: Throwable) { 0 }
    @Suppress("DEPRECATION")
    private fun hasUsage(): Boolean {
        val ao = getSystemService(AppOpsManager::class.java)
        return ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    }
    private fun refreshStatus() {
        val sz = shizukuState()
        b.txtShizuku.text = when (sz) { 2 -> "Shizuku: ready"; 1 -> "Shizuku: permission needed"; else -> "Shizuku: not running (optional)" }
        b.btnShizuku.visibility = if (sz == 2) View.GONE else View.VISIBLE
        b.btnShizuku.text = if (sz == 1) "Grant" else "Open"
        val acc = CleanerService.instance != null
        b.txtAccess.text = if (acc) "Accessibility: enabled" else "Accessibility: off"
        b.btnAccess.visibility = if (acc) View.GONE else View.VISIBLE
        b.btnAccess.text = "Enable"
        val usage = hasUsage()
        b.txtUsage.text = if (usage) "Usage access: granted" else "Usage access: needed for cache sizes"
        b.btnUsage.visibility = if (usage) View.GONE else View.VISIBLE
        b.btnUsage.text = "Grant"
        b.txtMode.text = "Method: " + when { sz == 2 -> "Shizuku (fast, no screen needed)"; acc -> "Accessibility (taps Settings for you)"; else -> "none available yet" }
        updateSummary()
    }
    private fun onShizukuButton() {
        try {
            if (shizukuState() == 1) Shizuku.requestPermission(100)
            else {
                val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                startActivity(i)
            }
        } catch (t: Throwable) { Toast.makeText(this, "Couldn't open Shizuku", Toast.LENGTH_SHORT).show() }
    }
    private fun startClean() {
        val sel = all.filter { it.checked }.map { it.pkg }
        val force = b.chipForce.isChecked
        val cache = b.chipCache.isChecked
        if (sel.isEmpty() || (!force && !cache)) {
            Toast.makeText(this, "Pick at least one app and one action", Toast.LENGTH_SHORT).show(); return
        }
        if (shizukuState() == 2) {
            b.fabStart.isEnabled = false; b.txtSummary.text = "Working…"
            io.execute {
                val (ok, fail) = ShizukuShell.clean(sel, force, cache)
                runOnUiThread {
                    b.fabStart.isEnabled = true
                    Toast.makeText(this, "Done: $ok ok, $fail failed", Toast.LENGTH_LONG).show()
                    loadCacheSizes()
                }
            }
        } else {
            val svc = CleanerService.instance
            if (svc == null) {
                Toast.makeText(this, "Enable Shizuku or the accessibility service first", Toast.LENGTH_LONG).show(); return
            }
            svc.start(sel, force, cache)
        }
    }
}
