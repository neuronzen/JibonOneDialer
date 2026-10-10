package com.jibon.onedialer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var rootLayout: LinearLayout
    private var currentTab = 0
    private var displayRef: TextView? = null

    private val dialedNumber = StringBuilder()
    private val contactsList = mutableListOf<Triple<String, String, String?>>()
    private val recentsList = mutableListOf<RecentCall>()
    private var deleteBtnRef: ImageView? = null

    private val PERM_REQ = 1001

    data class RecentCall(val name: String?, val number: String, val type: Int, val date: Long)

    private val isDark: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private val bgColor: Int get() = if (isDark) Color.BLACK else Color.WHITE
    private val surfaceColor: Int get() = if (isDark) Color.parseColor("#1C1C1E") else Color.parseColor("#F2F2F7")
    private val primaryText: Int get() = if (isDark) Color.WHITE else Color.parseColor("#000000")
    private val secondaryText: Int get() = if (isDark) Color.parseColor("#8E8E93") else Color.parseColor("#8A8A8E")
    private val navInactive: Int get() = if (isDark) Color.parseColor("#8E8E93") else Color.parseColor("#8A8A8E")
    private val accentGreen = Color.parseColor("#1EA362")

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val dir = getExternalFilesDir(null) ?: filesDir
                java.io.File(dir, "crash.txt")
                    .writeText("Thread: ${t.name}\n\n${e.stackTraceToString()}")
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.statusBarColor = bgColor
            window.navigationBarColor = bgColor
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (isDark) 0
            else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

            rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(bgColor)
            }
            setContentView(rootLayout)
            showKeypad()
            requestPermissionsIfNeeded()
        } catch (t: Throwable) {
            showCrash(t)
        }
    }

    private fun showCrash(t: Throwable) {
        val tv = TextView(this).apply {
            text = "CRASH:\n\n" + t.stackTraceToString()
            textSize = 11f
            setPadding(30, 80, 30, 30)
            setTextColor(Color.RED)
            setBackgroundColor(Color.WHITE)
        }
        val sc = ScrollView(this)
        sc.addView(tv)
        setContentView(sc)
    }

    private fun clearRoot() { rootLayout.removeAllViews() }

    // ============ KEYPAD ============
    private fun showKeypad() {
        clearRoot()
        deleteBtnRef = null

        rootLayout.addView(TextView(this).apply {
            text = "Phone"
            textSize = 26f
            setTextColor(primaryText)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(24), dp(20), dp(24), 0)
        })

        val display = TextView(this).apply {
            text = dialedNumber.toString()
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(primaryText)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            minHeight = dp(56)
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        displayRef = display
        rootLayout.addView(display)

        val gridWrapper = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val grid = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }

        val keys = listOf(
            "1" to "", "2" to "ABC", "3" to "DEF",
            "4" to "GHI", "5" to "JKL", "6" to "MNO",
            "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
            "*" to "", "0" to "+", "#" to ""
        )
        keys.forEach { (digit, letters) -> grid.addView(makeDialKey(digit, letters)) }
        gridWrapper.addView(grid)
        rootLayout.addView(gridWrapper)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96)
            )
            setPadding(dp(48), 0, dp(48), 0)
        }

        val callContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        callContainer.addView(makeCallButton())

        val delContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        val delBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_delete)
            setColorFilter(primaryText)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(dp(44), dp(44)).apply {
                gravity = Gravity.CENTER
            }
            visibility = if (dialedNumber.isEmpty()) View.INVISIBLE else View.VISIBLE
            setOnClickListener {
                if (dialedNumber.isNotEmpty()) {
                    dialedNumber.deleteCharAt(dialedNumber.length - 1)
                    displayRef?.text = dialedNumber.toString()
                    if (dialedNumber.isEmpty()) visibility = View.INVISIBLE
                }
            }
        }
        deleteBtnRef = delBtn
        delContainer.addView(delBtn)

        actions.addView(callContainer)
        actions.addView(delContainer)
        rootLayout.addView(actions)

        rootLayout.addView(buildBottomNav())
    }

    private fun makeDialKey(digit: String, letters: String): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                width = 0
                height = dp(76)
            }
            isClickable = true
            isFocusable = true
        }
        cell.addView(TextView(this).apply {
            text = digit
            textSize = 32f
            setTextColor(primaryText)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER
        })
        if (letters.isNotEmpty()) {
            cell.addView(TextView(this).apply {
                text = letters
                textSize = 10f
                setTextColor(secondaryText)
                letterSpacing = 0.08f
                gravity = Gravity.CENTER
            })
        }
        cell.setOnClickListener {
            dialedNumber.append(digit)
            displayRef?.text = dialedNumber.toString()
            deleteBtnRef?.visibility = View.VISIBLE
        }
        return cell
    }

    private fun makeCallButton(): View = ImageView(this).apply {
        setImageResource(android.R.drawable.ic_menu_call)
        setColorFilter(Color.WHITE)
        setPadding(dp(20), dp(20), dp(20), dp(20))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accentGreen)
        }
        elevation = dp(3).toFloat()
        layoutParams = FrameLayout.LayoutParams(dp(68), dp(68)).apply {
            gravity = Gravity.CENTER
        }
        setOnClickListener {
            val num = dialedNumber.toString()
            if (num.isEmpty()) return@setOnClickListener
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
        }
    }

    private fun buildBottomNav(): View {
        val wrapper = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(80)
            )
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(30).toFloat()
                setColor(surfaceColor)
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(60)
            ).apply { gravity = Gravity.CENTER }
            setPadding(dp(16), 0, dp(16), 0)
        }
        listOf(
            android.R.drawable.ic_menu_call to 0,
            android.R.drawable.ic_menu_recent_history to 1,
            android.R.drawable.ic_menu_myplaces to 2
        ).forEach { (icon, idx) ->
            pill.addView(ImageView(this).apply {
                setImageResource(icon)
                setColorFilter(if (idx == currentTab) accentGreen else navInactive)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    marginStart = dp(4); marginEnd = dp(4)
                }
                setOnClickListener { switchTab(idx) }
            })
        }
        wrapper.addView(pill)
        return wrapper
    }

    private fun switchTab(index: Int) {
        currentTab = index
        when (index) {
            0 -> showKeypad()
            1 -> showRecents()
            2 -> showContacts()
        }
    }

    // ============ RECENTS ============
    private fun showRecents() {
        clearRoot()
        rootLayout.addView(TextView(this).apply {
            text = "Recents"
            textSize = 26f
            setTextColor(primaryText)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(24), dp(20), dp(24), dp(12))
        })
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (recentsList.isEmpty()) {
            list.addView(emptyState("No recent calls", "Calls will appear here."))
        } else {
            recentsList.forEach { list.addView(makeRecentRow(it)) }
        }
        scroll.addView(list)
        rootLayout.addView(scroll)
        rootLayout.addView(buildBottomNav())
    }

    private fun makeRecentRow(r: RecentCall): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }
        row.addView(TextView(this).apply {
            text = (r.name?.firstOrNull() ?: r.number.firstOrNull() ?: '?').uppercase()
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(pickAvatarColor(r.name ?: r.number))
            }
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        })
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(this).apply {
            text = r.name ?: r.number
            textSize = 16f
            setTextColor(if (r.type == CallLog.Calls.MISSED_TYPE) Color.parseColor("#E53935") else primaryText)
        })
        info.addView(TextView(this).apply {
            val t = when (r.type) {
                CallLog.Calls.INCOMING_TYPE -> "Incoming"
                CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                CallLog.Calls.MISSED_TYPE -> "Missed"
                else -> "Call"
            }
            text = "$t · ${r.number}"
            textSize = 13f
            setTextColor(secondaryText)
        })
        row.addView(info)
        row.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${r.number}")))
        }
        return row
    }

    // ============ CONTACTS ============
    private fun showContacts() {
        clearRoot()
        rootLayout.addView(TextView(this).apply {
            text = "Contacts"
            textSize = 26f
            setTextColor(primaryText)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(24), dp(20), dp(24), dp(12))
        })
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (contactsList.isEmpty()) {
            list.addView(emptyState("No contacts", "Add contacts to see them here."))
        } else {
            contactsList.forEach { (name, number, _) -> list.addView(makeContactRow(name, number)) }
        }
        scroll.addView(list)
        rootLayout.addView(scroll)
        rootLayout.addView(buildBottomNav())
    }

    private fun makeContactRow(name: String, number: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }
        row.addView(TextView(this).apply {
            text = (name.firstOrNull() ?: '?').uppercase()
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(pickAvatarColor(name))
            }
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        })
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(this).apply {
            text = name; textSize = 16f; setTextColor(primaryText)
        })
        info.addView(TextView(this).apply {
            text = number; textSize = 13f; setTextColor(secondaryText)
        })
        row.addView(info)
        row.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        }
        return row
    }

    private fun emptyState(title: String, sub: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(80), dp(32), dp(32))
            addView(TextView(this@MainActivity).apply {
                text = title; textSize = 18f; setTextColor(primaryText)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = sub; textSize = 14f; setTextColor(secondaryText)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
        }

    private fun pickAvatarColor(seed: String): Int {
        val palette = intArrayOf(
            Color.parseColor("#5C6BC0"), Color.parseColor("#26A69A"),
            Color.parseColor("#EF5350"), Color.parseColor("#AB47BC"),
            Color.parseColor("#FFA726"), Color.parseColor("#42A5F5"),
            Color.parseColor("#66BB6A")
        )
        return palette[Math.abs(seed.hashCode()) % palette.size]
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ============ PERMISSIONS ============
    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.READ_CONTACTS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.READ_CALL_LOG)
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERM_REQ)
        } else loadData()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQ) {
            loadData()
            switchTab(currentTab)
        }
    }

    private fun loadData() {
        loadContacts()
        loadRecents()
    }

    private fun loadContacts() {
        contactsList.clear()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ), null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                val p = c.getString(1) ?: continue
                contactsList.add(Triple(n, p, null))
            }
        }
    }

    private fun loadRecents() {
        recentsList.clear()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) return
        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE, CallLog.Calls.DATE), null, null,
            CallLog.Calls.DATE + " DESC"
        )?.use { c ->
            var count = 0
            while (c.moveToNext() && count < 200) {
                val num = c.getString(1) ?: continue
                recentsList.add(RecentCall(c.getString(0), num, c.getInt(2), c.getLong(3)))
                count++
            }
        }
    }
}
