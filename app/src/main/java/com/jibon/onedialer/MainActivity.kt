package com.jibon.onedialer

import android.Manifest
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
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: LinearLayout
    private lateinit var tabKeypad: ImageView
    private lateinit var tabRecents: ImageView
    private lateinit var tabContacts: ImageView
    private lateinit var tabIndicator: View
    private var currentTab = 0

    private val dialedNumber = StringBuilder()
    private var contactsList = mutableListOf<Triple<String, String, String?>>()
    private var recentsList = mutableListOf<RecentCall>()

    private val PERM_REQ = 1001

    data class RecentCall(
        val name: String?,
        val number: String,
        val type: Int,
        val date: Long
    )

    // -------- Theme helpers --------
    private val isDark: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private val bgColor: Int get() = if (isDark) Color.BLACK else Color.WHITE
    private val surfaceColor: Int get() = if (isDark) Color.parseColor("#1A1A1A") else Color.parseColor("#F5F5F5")
    private val primaryText: Int get() = if (isDark) Color.WHITE else Color.parseColor("#111111")
    private val secondaryText: Int get() = if (isDark) Color.parseColor("#9AA0A6") else Color.parseColor("#5F6368")
    private val pillBg: Int get() = if (isDark) Color.parseColor("#222222") else Color.parseColor("#EDEDED")
    private val accentGreen = Color.parseColor("#23A26D")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
        if (isDark) {
            window.decorView.systemUiVisibility = 0
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        buildRoot()
        requestPermissionsIfNeeded()
    }

    private fun buildRoot() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            fitsSystemWindows = true
        }
        setContentView(rootLayout)
        showKeypad()
    }

    // ---------------- TAB CONTENT BUILDERS ----------------

    private fun clearRoot() {
        rootLayout.removeAllViews()
        tabIndicator = View(this)
    }

    private fun buildHeader(title: String): LinearLayout {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 26f
            setTextColor(primaryText)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(titleView)
        return header
    }

    private fun buildBottomNav(): LinearLayout {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(10), dp(16), dp(16))
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(32).toFloat()
                setColor(surfaceColor)
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(60)
            )
        }

        tabKeypad = makeTabIcon(android.R.drawable.ic_menu_call, 0)
        tabRecents = makeTabIcon(android.R.drawable.ic_menu_recent_history, 1)
        tabContacts = makeTabIcon(android.R.drawable.ic_menu_myplaces, 2)

        pill.addView(tabKeypad)
        pill.addView(tabRecents)
        pill.addView(tabContacts)
        wrapper.addView(pill)
        return wrapper
    }

    private fun makeTabIcon(resId: Int, index: Int): ImageView {
        val iv = ImageView(this).apply {
            setImageResource(resId)
            setColorFilter(if (index == currentTab) accentGreen else secondaryText)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(14), dp(14), dp(14), dp(14))
            val lp = LinearLayout.LayoutParams(dp(56), dp(44))
            lp.setMargins(dp(4), 0, dp(4), 0)
            layoutParams = lp
            setOnClickListener { switchTab(index) }
        }
        return iv
    }

    private fun switchTab(index: Int) {
        currentTab = index
        when (index) {
            0 -> showKeypad()
            1 -> showRecents()
            2 -> showContacts()
        }
    }

    // ---------------- KEYPAD ----------------

    private fun showKeypad() {
        clearRoot()

        val content = androidx.constraintlayout.widget.ConstraintLayout(this).apply {
            setBackgroundColor(bgColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // --- Header "Phone" ---
        val header = TextView(this).apply {
            text = "Phone"
            textSize = 26f
            setTextColor(primaryText)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            id = View.generateViewId()
        }
        content.addView(header)
        val headerLp = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = dp(20)
            marginStart = dp(20)
        }
        header.layoutParams = headerLp

        // --- Number display ---
        val display = TextView(this).apply {
            text = dialedNumber.toString()
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(primaryText)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(dp(24), dp(12), dp(24), dp(12))
            minHeight = dp(70)
            id = View.generateViewId()
            tag = "display"
        }
        content.addView(display)

        // --- Bottom nav pill (anchor at bottom) ---
        val navWrapper = buildBottomNav()
        navWrapper.id = View.generateViewId()
        content.addView(navWrapper)

        // --- Actions row (call + delete) ---
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            id = View.generateViewId()
        }
        val callBtn = makeCallButton()
        val delBtn = makeDeleteButton(display)

        val callWrap = FrameLayout(this)
        callWrap.layoutParams = LinearLayout.LayoutParams(0, dp(80), 1f)
        callWrap.addView(callBtn)
        val delWrap = FrameLayout(this)
        delWrap.layoutParams = LinearLayout.LayoutParams(0, dp(80), 1f)
        delWrap.addView(delBtn)
        actions.addView(callWrap)
        actions.addView(delWrap)
        content.addView(actions)

        // --- Dial pad grid ---
        val grid = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            id = View.generateViewId()
        }
        val keys = listOf(
            Triple("1", "", ""),
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
        keys.forEach { (digit, letters, _) ->
            grid.addView(makeDialKey(digit, letters, display))
        }
        content.addView(grid)

        // --- Constraints ---
        val displayLp = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topToBottom = header.id
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        }
        display.layoutParams = displayLp

        val navLp = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            bottomMargin = dp(12)
        }
        navWrapper.layoutParams = navLp

        val actionsLp = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(90)
        ).apply {
            bottomToTop = navWrapper.id
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            bottomMargin = dp(8)
        }
        actions.layoutParams = actionsLp

        val gridLp = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0
        ).apply {
            topToBottom = display.id
            bottomToTop = actions.id
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            setMargins(dp(16), dp(8), dp(16), dp(8))
            height = 0
        }
        grid.layoutParams = gridLp

        rootLayout.addView(content)
    }

    private fun makeDialKey(digit: String, letters: String, display: TextView): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            isClickable = true
            isFocusable = true
        }
        val digitView = TextView(this).apply {
            text = digit
            textSize = 32f
            setTextColor(primaryText)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        cell.addView(digitView)
        if (letters.isNotEmpty()) {
            val letterView = TextView(this).apply {
                text = letters
                textSize = 10f
                setTextColor(secondaryText)
                letterSpacing = 0.15f
                gravity = Gravity.CENTER
            }
            cell.addView(letterView)
        }
        cell.setOnClickListener {
            dialedNumber.append(digit)
            display.text = dialedNumber.toString()
        }
        return cell
    }

    private fun makeCallButton(): View {
        val btn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_call)
            setColorFilter(Color.WHITE)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accentGreen)
            }
            elevation = dp(4).toFloat()
            layoutParams = FrameLayout.LayoutParams(dp(64), dp(64)).apply {
                gravity = Gravity.CENTER
            }
            setOnClickListener {
                val num = dialedNumber.toString()
                if (num.isEmpty()) return@setOnClickListener
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))
                startActivity(intent)
            }
        }
        return btn
    }

    private fun makeDeleteButton(display: TextView): View {
        val btn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_delete)
            setColorFilter(primaryText)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56)).apply {
                gravity = Gravity.CENTER
            }
            setOnClickListener {
                if (dialedNumber.isNotEmpty()) {
                    dialedNumber.deleteCharAt(dialedNumber.length - 1)
                    display.text = dialedNumber.toString()
                }
            }
        }
        return btn
    }


    // ---------------- RECENTS ----------------

    private fun showRecents() {
        clearRoot()
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }
        wrapper.addView(buildHeader("Recents"))

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        if (recentsList.isEmpty()) {
            list.addView(emptyState("No recent calls", "Calls you make or receive will appear here."))
        } else {
            recentsList.forEach { r ->
                list.addView(makeRecentRow(r))
            }
        }
        scroll.addView(list)
        listContainer.addView(scroll)
        wrapper.addView(listContainer)
        wrapper.addView(buildBottomNav())
        rootLayout.addView(wrapper)
    }

    private fun makeRecentRow(r: RecentCall): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val avatar = TextView(this).apply {
            text = (r.name?.firstOrNull() ?: r.number.firstOrNull() ?: '?').uppercase()
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(pickAvatarColor(r.name ?: r.number))
            }
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val nameView = TextView(this).apply {
            text = r.name ?: r.number
            textSize = 16f
            setTextColor(if (r.type == CallLog.Calls.MISSED_TYPE) Color.parseColor("#E53935") else primaryText)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val subView = TextView(this).apply {
            val typeLabel = when (r.type) {
                CallLog.Calls.INCOMING_TYPE -> "Incoming"
                CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                CallLog.Calls.MISSED_TYPE -> "Missed"
                else -> "Call"
            }
            text = "$typeLabel · ${r.number}"
            textSize = 13f
            setTextColor(secondaryText)
        }
        info.addView(nameView)
        info.addView(subView)
        row.addView(avatar)
        row.addView(info)
        row.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${r.number}")))
        }
        return row
    }

    // ---------------- CONTACTS ----------------

    private fun showContacts() {
        clearRoot()
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }
        wrapper.addView(buildHeader("Contacts"))

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        if (contactsList.isEmpty()) {
            list.addView(emptyState("No contacts", "Grant permission or add contacts to see them here."))
        } else {
            contactsList.forEach { (name, number, photo) ->
                list.addView(makeContactRow(name, number))
            }
        }
        scroll.addView(list)
        wrapper.addView(scroll)
        wrapper.addView(buildBottomNav())
        rootLayout.addView(wrapper)
    }

    private fun makeContactRow(name: String, number: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val avatar = TextView(this).apply {
            text = (name.firstOrNull() ?: '?').uppercase()
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(pickAvatarColor(name))
            }
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val nameView = TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(primaryText)
        }
        val numView = TextView(this).apply {
            text = number
            textSize = 13f
            setTextColor(secondaryText)
        }
        info.addView(nameView)
        info.addView(numView)
        row.addView(avatar)
        row.addView(info)
        row.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        }
        return row
    }

    // ---------------- Helpers ----------------

    private fun emptyState(title: String, sub: String): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(80), dp(32), dp(32))
        }
        box.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(primaryText)
            gravity = Gravity.CENTER
        })
        box.addView(TextView(this).apply {
            text = sub
            textSize = 14f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
        return box
    }

    private fun pickAvatarColor(seed: String): Int {
        val palette = intArrayOf(
            Color.parseColor("#5C6BC0"),
            Color.parseColor("#26A69A"),
            Color.parseColor("#EF5350"),
            Color.parseColor("#AB47BC"),
            Color.parseColor("#FFA726"),
            Color.parseColor("#42A5F5"),
            Color.parseColor("#66BB6A")
        )
        return palette[Math.abs(seed.hashCode()) % palette.size]
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------------- Permissions ----------------

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.READ_CONTACTS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.READ_CALL_LOG)

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERM_REQ)
        } else {
            loadData()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQ) loadData()
    }

    private fun loadData() {
        loadContacts()
        loadRecents()
        // refresh current tab
        switchTab(currentTab)
    }

    private fun loadContacts() {
        contactsList.clear()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                contactsList.add(Triple(name, number, null))
            }
        }
    }

    private fun loadRecents() {
        recentsList.clear()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) return
        val cursor: Cursor? = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
            null, null,
            CallLog.Calls.DATE + " DESC"
        )
        cursor?.use { c ->
            var count = 0
            while (c.moveToNext() && count < 200) {
                val name = c.getString(0)
                val number = c.getString(1) ?: continue
                val type = c.getInt(2)
                val date = c.getLong(3)
                recentsList.add(RecentCall(name, number, type, date))
                count++
            }
        }
    }
}
