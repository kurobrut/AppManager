package com.example.appmanager

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.Editable
import android.text.TextWatcher

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable
)

class MainActivity : Activity() {
    private val apps = mutableListOf<AppItem>()
    private val filteredApps = mutableListOf<AppItem>()
    private val selectedApps = linkedSetOf<String>()

    private lateinit var listView: ListView
    private lateinit var searchInput: EditText
    private lateinit var countText: TextView
    private lateinit var adapter: AppAdapter

    private val bg = Color.rgb(17, 19, 27)
    private val card = Color.rgb(29, 33, 45)
    private val accent = Color.rgb(120, 100, 255)
    private val secondary = Color.rgb(165, 170, 185)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
        loadApps()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 30, 20, 20)
            setBackgroundColor(bg)
        }

        val title = TextView(this).apply {
            text = "App Manager"
            textSize = 27f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "Manage your installed applications"
            textSize = 13f
            setTextColor(secondary)
            setPadding(0, 5, 0, 18)
        }
        searchInput = EditText(this).apply {
            hint = "Search applications..."
            textSize = 14f
            singleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(secondary)
            setPadding(16, 10, 16, 10)
            background = rounded(card, 14)
        }
        countText = TextView(this).apply {
            textSize = 13f
            setTextColor(secondary)
            setPadding(2, 14, 0, 10)
        }
        listView = ListView(this).apply {
            divider = null
            dividerHeight = 8
            selector = null
            cacheColorHint = Color.TRANSPARENT
        }

        val selectAll = makeButton("SELECT ALL", accent)
        val deselect = makeButton("DESELECT", card)
        val forceStop = makeButton("FORCE STOP", Color.rgb(190, 65, 75))
        val clearCache = makeButton("CLEAR CACHE", accent)

        val selectionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(selectAll, LinearLayout.LayoutParams(0, 48, 1f))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(8, 1))
            addView(deselect, LinearLayout.LayoutParams(0, 48, 1f))
        }
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(forceStop, LinearLayout.LayoutParams(0, 52, 1f))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(8, 1))
            addView(clearCache, LinearLayout.LayoutParams(0, 52, 1f))
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(searchInput)
        root.addView(countText)
        root.addView(selectionRow)
        root.addView(
            listView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply {
                topMargin = 12
                bottomMargin = 12
            }
        )
        root.addView(actionRow)
        setContentView(root)

        adapter = AppAdapter()
        listView.adapter = adapter

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        selectAll.setOnClickListener {
            filteredApps.forEach { selectedApps.add(it.packageName) }
            adapter.notifyDataSetChanged()
            updateCount()
        }
        deselect.setOnClickListener {
            selectedApps.clear()
            adapter.notifyDataSetChanged()
            updateCount()
        }
        forceStop.setOnClickListener { openSelectedAppSettings("Force Stop") }
        clearCache.setOnClickListener { openSelectedAppSettings("Clear Cache") }
    }

    private fun loadApps() {
        val launcherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val results = packageManager.queryIntentActivities(launcherIntent, 0)

        apps.clear()
        results.forEach { result ->
            val info = result.activityInfo.applicationInfo
            if (info.packageName == packageName) return@forEach
            if (apps.any { it.packageName == info.packageName }) return@forEach

            apps.add(
                AppItem(
                    name = info.loadLabel(packageManager).toString(),
                    packageName = info.packageName,
                    icon = info.loadIcon(packageManager)
                )
            )
        }
        apps.sortBy { it.name.lowercase() }
        filterApps("")
    }

    private fun filterApps(query: String) {
        filteredApps.clear()
        filteredApps.addAll(
            apps.filter {
                it.name.contains(query, true) ||
                    it.packageName.contains(query, true)
            }
        )
        adapter.notifyDataSetChanged()
        updateCount()
    }

    private fun updateCount() {
        countText.text =
            "${filteredApps.size} apps  •  ${selectedApps.size} selected"
    }

    private fun openSelectedAppSettings(actionName: String) {
        val first = apps.firstOrNull {
            selectedApps.contains(it.packageName)
        }
        if (first == null) {
            toast("Select at least one application")
            return
        }

        AlertDialog.Builder(this)
            .setTitle(actionName)
            .setMessage(
                "Android does not allow an ordinary app to perform this action silently. " +
                    "App Info will open for ${first.name}. Complete the action there."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open App Info") { _, _ ->
                try {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${first.packageName}")
                    )
                    startActivity(intent)
                    selectedApps.remove(first.packageName)
                    adapter.notifyDataSetChanged()
                    updateCount()
                } catch (_: Exception) {
                    toast("Unable to open app settings")
                }
            }
            .show()
    }

    private fun makeButton(label: String, color: Int): Button =
        Button(this).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = rounded(color, 12)
        }

    private fun rounded(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * resources.displayMetrics.density
        }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    inner class AppAdapter : BaseAdapter() {
        override fun getCount() = filteredApps.size
        override fun getItem(position: Int) = filteredApps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup?
        ): View {
            val app = getItem(position)
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(14, 12, 14, 12)
                background = rounded(card, 14)
            }

            val icon = ImageView(this@MainActivity).apply {
                setImageDrawable(app.icon)
                layoutParams = LinearLayout.LayoutParams(46, 46)
            }

            val info = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 0, 0, 0)
            }
            info.addView(TextView(this@MainActivity).apply {
                text = app.name
                textSize = 15f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
            })
            info.addView(TextView(this@MainActivity).apply {
                text = app.packageName
                textSize = 10f
                setTextColor(secondary)
            })

            val check = CheckBox(this@MainActivity).apply {
                buttonTintList =
                    android.content.res.ColorStateList.valueOf(accent)
                isChecked = selectedApps.contains(app.packageName)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedApps.add(app.packageName)
                    else selectedApps.remove(app.packageName)
                    updateCount()
                }
            }

            row.addView(icon)
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(check)
            row.setOnClickListener { check.isChecked = !check.isChecked }
            return row
        }
    }
}
