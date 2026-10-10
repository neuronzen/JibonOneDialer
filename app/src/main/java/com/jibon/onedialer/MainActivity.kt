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
    private var deleteBtnRef: ImageView? = null
    private var callRowRef: LinearLayout? = null

    private val dialedNumber = StringBuilder()
    private val contactsList = mutableListOf<Triple<String, String, String?>>()
    private val recentsList = mutableListOf<RecentCall>()

    private val PERM_REQ = 1001

    data class RecentCall(val name: String?, val number: String, val type: Int, val date: Long)

    private val isDark: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private val bgColor: Int get() = if (isDark) Color.BLACK else Color.WHITE
    private val primaryText: Int get() = if (isDark) Color.WHITE else Color.parseColor("#000000")
    private val secondaryText: Int get() = if (isDark) Color.parseColor("#8E8E93") else Color.parseColor("#7A7A7E")
    private val dividerColor: Int get() = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")
    private val navInactive: Int get() = if (isDark) Color.parseColor("#8E8E93") else Color.parseColor("#3C3C43")
    private val accentGreen = Color.parseColor("#1DAF5A")

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val dir = getExternalFilesDir(null) ?: filesDir
                java.io.File(dir, "crash.txt").writeText(e.stackTraceToString())
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
        setContentView(ScrollView(this).apply { addView(tv) })
    }

    private fun clearRoot() { rootLayout.removeAllViews() }

    // ============================================================
    //  KEYPAD (Samsung layout: top icons → number → keypad → bottom nav)
    // ============================================================
    private fun showKeypad() {
        clearRoot()

        // ---------- Top bar (search + menu, right aligned) ----------
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(dp(16), dp(12), dp(16), dp(0))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        topBar.addView(makeTopIcon(android.R.drawable.ic_menu_search))
        topBar.addView(makeTopIcon(android.R.drawable.ic_menu_more))
        rootLayout.addView(topBar)

        // ---------- Number display (top, appears when typing) ----------
        val display = TextView(this).apply {
            text = dialedNumber.toString()
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(primaryText)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(dp(24), dp(24), dp(24), dp(8))
            minHeight = dp(0)
            visibility = if (dialedNumber.isEmpty()) View.GONE else View.VISIBLE
        }
        displayRef = display
        rootLayout.addView(display)

        // ---------- Middle spacer (grows when empty) ----------
        val topSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        rootLayout.addView(topSpacer)

        // ---------- Dial pad ----------
        val grid = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val keys = listOf(
            Triple("1", "voicemail", ""),
            Triple("2", "ABC", ""),
            Triple("3", "DEF", ""),
            Triple("4", "GHI", ""),
            Triple("5", "JKL", ""),
            Triple("6", "MNO", ""),
            Triple("7", "PQRS", ""),
            Triple("8", "TUV", ""),
            Triple("9", "WXYZ", ""),
            Triple("*", "", ""),
            Triple("0", "+", ""),
            Triple("#", "", "")
        )
        keys.forEach { (d, l, _) -> grid.addView(makeDialKey(d, l, display)) }
        rootLayout.addView(grid)

        // ---------- Bottom spacer ----------
        val bottomSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16))
        }
        rootLayout.addView(bottomSpacer)

        // ---------- Call row (call + delete, appears when typing) ----------
        val callRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(80)
            )
            setPadding(dp(60), 0, dp(60), 0)
        }
        val callBtn = makeCallButton()
        val delBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_delete)
            setColorFilter(primaryText)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            setOnClickListener {
                if (dialedNumber.isNotEmpty()) {
                    dialedNumber.deleteCharAt(dialedNumber.length - 1)
                    display.text = dialedNumber.toString()
                    if (dialedNumber.isEmpty()) {
                        display.visibility = View.GONE
                        callRow.visibility = View.GONE
                        topSpacer.layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                    }
                }
            }
        }
        deleteBtnRef = delBtn

        val leftSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        val rightSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        callRow.addView(leftSpacer)
        callRow.addView(callBtn)
        callRow.addView(rightSpacer)
        callRow.addView(delBtn)
        callRow.visibility = if (dialedNumber.isEmpty()) View.GONE else View.VISIBLE
        callRowRef = callRow
        rootLayout.addView(callRow)

        // ---------- Bottom nav (edge-to-edge, labels) ----------
        rootLayout.addView(buildBottomNav())
    }

    private fun makeTopIcon(resId: Int): ImageView = ImageView(this).apply {
        setImageResource(resId)
        setColorFilter(primaryText)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
    }

    private fun makeDialKey(digit: String, letters: String, display: TextView): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                width = 0
                height = dp(84)
            }
            isClickable = true
            isFocusable = true
        }
        // Display digit with Samsung-style: "*" as asterisk, "1" plain
        val digitDisplay = if (digit == "*") "✱" else digit
        cell.addView(TextView(this).apply {
            text = digitDisplay
            textSize = 32f
            setTextColor(primaryText)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
        })
        if (letters.isNotEmpty() && letters != "voicemail") {
            cell.addView(TextView(this).apply {
                text = letters
                textSize = 10f
                setTextColor(secondaryText)
                letterSpacing = 0.05f
                gravity = Gravity.CENTER
            })
        } else if (letters == "voicemail") {
            // Small voicemail icon below 1
            val vm = TextView(this).apply {
                text = "◯◯"
                textSize = 9f
                setTextColor(secondaryText)
                gravity = Gravity.CENTER
            }
            cell.addView(vm)
        }
        cell.setOnClickListener {
            dialedNumber.append(digit)
            display.text = dialedNumber.toString()
            display.visibility = View.VISIBLE
            callRowRef?.visibility = View.VISIBLE
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
        layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        setOnClickListener {
            val num = dialedNumber.toString()
            if (num.isEmpty()) return@setOnClickListener
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
        }
    }

    // ============================================================
    //  BOTTOM NAV (edge-to-edge with labels)
    // ============================================================
    private fun buildBottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bgColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(72)
            )
            setPadding(0, dp(8), 0, dp(8))
        }

        val items = listOf(
            Triple("Keypad", android.R.drawable.ic_menu_call, 0),
            Triple("Recents", android.R.drawable.ic_menu_recent_history, 1),
            Triple("Contacts", android.R.drawable.ic_menu_myplaces, 2)
        )

        items.forEach { (label, icon, idx) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                isClickable = true
                setOnClickListener { switchTab(idx) }
            }
            val tint = if (idx == currentTab) accentGreen else navInactive
            item.addView(ImageView(this).apply {
                setImageResource(icon)
                setColorFilter(tint)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            })
            item.addView(TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(tint)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
            nav.addView(item)
        }
        return nav
    }

    private fun switchTab(index: Int) {
        currentTab = index
        when (index) {
            0 -> showKeypad()
            1 -> showRecents()
            2 -> showContacts()
        }
    }

    // ============================================================
    //  RECENTS (Samsung: top bar, searchable list with time on right)
    // ============================================================
    private fun showRecents() {
        clearRoot()
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        topBar.addView(makeTopIcon(android.R.drawable.ic_menu_search))
        topBar.addView(makeTopIcon(android.R.drawable.ic_menu_more))
        rootLayout.addView(topBar)

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
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
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
            text = r.number
            textSize = 13f
            setTextColor(secondaryText)
        })
        row.addView(info)

        // time on right
        row.addView(TextView(this).apply {
            text = android.text.format.DateFormat.format("h:mm a", r.date).toString()
            textSize = 12f
            setTextColor(secondaryText)
        })

        row.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${r.number}")))
        }
        return row
    }

    // ============================================================
    //  CONTACTS
    // ============================================================
    private fun showContacts() {
        clearRoot()
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        topBar.addView(makeTopIcon(android.R.drawable.ic_menu_add))
        topBar.addView(makeTopIcon(android.R.drawable.ic_menu_search))
        topBar.addView(makeTopIcon(android.R.drawable.ic_menu_more))
        rootLayout.addView(topBar)

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
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
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
        if (requestCode == PERM_REQ) loadData()
    }

    private fun loadData() { loadContacts(); loadRecents() }

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
