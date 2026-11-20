package com.example.cachecleaner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        status = TextView(this)
        val enable = Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val force = CheckBox(this).apply { text = "Force stop"; isChecked = true }
        val cache = CheckBox(this).apply { text = "Clear cache"; isChecked = true }
        val list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_list_item_multiple_choice,
                apps.map { it.second }
            )
        }
        val go = Button(this).apply {
            text = "Start"
            setOnClickListener {
                val svc = CleanerService.instance
                if (svc == null) {
                    Toast.makeText(context, "Enable the accessibility service first", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val sel = (0 until list.count).filter { list.isItemChecked(it) }.map { apps[it].first }
                if (sel.isEmpty() || (!force.isChecked && !cache.isChecked)) {
                    Toast.makeText(context, "Pick at least one app and one action", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                svc.start(sel, force.isChecked, cache.isChecked)
            }
        }
        root.addView(status)
        root.addView(enable)
        root.addView(force)
        root.addView(cache)
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(go)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (CleanerService.instance != null) "Service: enabled" else "Service: NOT enabled"
    }
}
