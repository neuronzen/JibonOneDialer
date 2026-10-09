package com.jibon.onedialer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private var page = "Keypad"
    private var phoneNumber = ""
    private var contactSearch = ""

    private val ink = Color.rgb(26, 29, 31)
    private val muted = Color.rgb(112, 117, 121)
    private val green = Color.rgb(28, 137, 75)
    private val paper = Color.WHITE
    private val field = Color.rgb(244, 246, 246)
    private val line = Color.rgb(237, 239, 240)
    private val permissionRequest = 41

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = paper
        window.navigationBarColor = paper
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
        }
        setContentView(root)
        render()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun text(value: String, size: Float, color: Int = ink, medium: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    private fun rounded(color: Int, radius: Int = 22): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun iconView(name: String, tint: Int, size: Int = 24): View =
        object : View(this) {
            private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tint
                style = Paint.Style.STROKE
                strokeWidth = 1.8f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tint
                style = Paint.Style.FILL
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val scale = min(width, height) / 24f
                if (scale <= 0f) return
                canvas.save()
                canvas.translate((width - 24f * scale) / 2f, (height - 24f * scale) / 2f)
                canvas.scale(scale, scale)
                when (name) {
                    "search" -> {
                        canvas.drawCircle(10.5f, 10.5f, 6.3f, stroke)
                        canvas.drawLine(15.1f, 15.1f, 20.2f, 20.2f, stroke)
                    }
                    "more" -> {
                        canvas.drawCircle(12f, 5f, 1.45f, fillPaint)
                        canvas.drawCircle(12f, 12f, 1.45f, fillPaint)
                        canvas.drawCircle(12f, 19f, 1.45f, fillPaint)
                    }
                    "keypad" -> {
                        val xs = floatArrayOf(5.5f, 12f, 18.5f)
                        val ys = floatArrayOf(4.5f, 9.5f, 14.5f, 19.5f)
                        ys.forEach { y -> xs.forEach { x -> canvas.drawCircle(x, y, 1.35f, fillPaint) } }
                    }
                    "recents" -> {
                        canvas.drawCircle(12f, 12f, 8.5f, stroke)
                        canvas.drawLine(12f, 6.8f, 12f, 12f, stroke)
                        canvas.drawLine(12f, 12f, 15.7f, 14.1f, stroke)
                    }
                    "contacts" -> {
                        canvas.drawCircle(12f, 7.7f, 3.35f, stroke)
                        val shoulders = Path().apply {
                            moveTo(4.1f, 20.1f)
                            cubicTo(4.5f, 16.2f, 7.4f, 14.4f, 12f, 14.4f)
                            cubicTo(16.6f, 14.4f, 19.5f, 16.2f, 19.9f, 20.1f)
                        }
                        canvas.drawPath(shoulders, stroke)
                    }
                    "call" -> {
                        val receiver = Path().apply {
                            moveTo(6.1f, 3.1f)
                            cubicTo(5.5f, 2.5f, 4.6f, 2.7f, 4f, 3.4f)
                            lineTo(3f, 4.6f)
                            cubicTo(2.3f, 5.5f, 4.3f, 10f, 8.5f, 14.2f)
                            cubicTo(12.7f, 18.4f, 17.7f, 20.4f, 18.6f, 19.5f)
                            lineTo(19.7f, 18.3f)
                            cubicTo(20.3f, 17.7f, 20.2f, 16.9f, 19.5f, 16.4f)
                            lineTo(16.5f, 14.3f)
                            cubicTo(15.8f, 13.8f, 15f, 13.9f, 14.5f, 14.5f)
                            lineTo(12.9f, 16f)
                            cubicTo(10.4f, 14.7f, 8.3f, 12.6f, 7f, 10.1f)
                            lineTo(8.5f, 8.6f)
                            cubicTo(9.1f, 8f, 9.2f, 7.3f, 8.6f, 6.7f)
                            close()
                        }
                        canvas.drawPath(receiver, fillPaint)
                    }
                    "backspace" -> {
                        val outline = Path().apply {
                            moveTo(8.5f, 5f)
                            lineTo(21f, 5f)
                            lineTo(21f, 19f)
                            lineTo(8.5f, 19f)
                            lineTo(2.8f, 12f)
                            close()
                        }
                        canvas.drawPath(outline, stroke)
                        canvas.drawLine(11f, 9f, 16.3f, 15f, stroke)
                        canvas.drawLine(16.3f, 9f, 11f, 15f, stroke)
                    }
                }
                canvas.restore()
            }
        }.apply {
            contentDescription = name
            layoutParams = ViewGroup.LayoutParams(dp(size), dp(size))
        }

    private fun iconButton(name: String, description: String, tint: Int = muted, action: () -> Unit): View =
        FrameLayout(this).apply {
            contentDescription = description
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            addView(iconView(name, tint), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        }

    private fun render() {
        root.removeAllViews()
        root.setBackgroundColor(paper)
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(10), 0)
        }
        if (page == "Keypad") {
            toolbar.addView(View(this), LinearLayout.LayoutParams(0, dp(48), 1f))
        } else {
            toolbar.addView(
                text(if (page == "Recents") "Recents" else "Contacts", 29f, ink, true),
                LinearLayout.LayoutParams(0, dp(52), 1f)
            )
        }
        toolbar.addView(iconButton("search", "Open contacts search") {
            if (page != "Contacts") {
                page = "Contacts"
                render()
            } else {
                root.findViewWithTag<EditText>("contactSearchInput")?.requestFocus()
            }
        }, LinearLayout.LayoutParams(dp(44), dp(48)))
        val more = iconButton("more", "More options", ink) { }
        more.setOnClickListener { showOptions(more) }
        toolbar.addView(more, LinearLayout.LayoutParams(dp(36), dp(48)))
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(52)))

        if (page == "Keypad") {
            val body = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, dp(16), dp(3))
            }
            root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
            showKeypad(body)
        } else {
            val scroll = ScrollView(this).apply {
                isFillViewport = true
                clipToPadding = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                setPadding(0, dp(3), 0, dp(12))
            }
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(3), dp(20), dp(8))
            }
            scroll.addView(content)
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            if (page == "Recents") showRecents(content) else showContacts(content)
        }
        root.addView(View(this).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1)))
        addBottomNavigation()
    }

    private fun showOptions(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Refresh")
        popup.menu.add("About Jibon One Dialer")
        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Refresh" -> render()
                "About Jibon One Dialer" -> AlertDialog.Builder(this)
                    .setTitle("Jibon One Dialer")
                    .setMessage("A simple dialer inspired by Samsung One UI.\n\nCalls open in your phone's dialer; this app does not place calls by itself.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            true
        }
        popup.show()
    }

    private fun addBottomNavigation() {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(3), dp(8), dp(4))
            setBackgroundColor(paper)
        }
        listOf(
            Triple("Keypad", "Keypad", "keypad"),
            Triple("Recents", "Recents", "recents"),
            Triple("Contacts", "Contacts", "contacts")
        ).forEach { item ->
            val active = page == item.first
            val color = if (active) green else muted
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = item.second
                setOnClickListener {
                    if (page != item.first) {
                        page = item.first
                        render()
                    }
                }
                addView(iconView(item.third, color, 22), LinearLayout.LayoutParams(dp(24), dp(24)))
                addView(text(item.second, 11f, color, active).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(-2, dp(18)).apply { topMargin = dp(3) })
            }
            nav.addView(tab, LinearLayout.LayoutParams(0, dp(58), 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(60)))
    }

    private fun showKeypad(content: LinearLayout) {
        content.addView(View(this), LinearLayout.LayoutParams(-1, 0, 0.65f))
        val display = FrameLayout(this)
        display.addView(text(phoneNumber, if (phoneNumber.length > 13) 26f else 31f, ink).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }, FrameLayout.LayoutParams(-1, -1))
        if (phoneNumber.isNotEmpty()) {
            display.addView(iconButton("backspace", "Delete last digit", muted) {
                phoneNumber = phoneNumber.dropLast(1)
                render()
            }, FrameLayout.LayoutParams(dp(46), -1, Gravity.END or Gravity.CENTER_VERTICAL))
        }
        content.addView(display, LinearLayout.LayoutParams(-1, dp(40)))

        val keyRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val keys = listOf(
            listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"), listOf("*", "0", "#")
        )
        val letters = listOf(
            listOf("", "ABC", "DEF"), listOf("GHI", "JKL", "MNO"),
            listOf("PQRS", "TUV", "WXYZ"), listOf("", "+", "")
        )
        keys.forEachIndexed { rowIndex, keysInRow ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            keysInRow.forEachIndexed { colIndex, key ->
                val hint = letters[rowIndex][colIndex]
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    contentDescription = if (hint.isEmpty()) key else "$key, $hint"
                    setOnClickListener {
                        phoneNumber += key
                        render()
                    }
                }
                cell.addView(text(key, 27f, ink).apply {
                    gravity = Gravity.CENTER
                    typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                })
                if (hint.isNotEmpty()) {
                    cell.addView(text(hint, 9f, muted).apply {
                        gravity = Gravity.CENTER
                        letterSpacing = 0.09f
                        setPadding(0, dp(2), 0, 0)
                    })
                } else {
                    cell.addView(View(this), LinearLayout.LayoutParams(1, dp(11)))
                }
                row.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
            }
            keyRows.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        content.addView(keyRows, LinearLayout.LayoutParams(-1, 0, 4f))

        val callButton = FrameLayout(this).apply {
            background = rounded(green, 40)
            isClickable = true
            isFocusable = true
            contentDescription = "Call number"
            setOnClickListener {
                if (phoneNumber.isBlank()) {
                    Toast.makeText(this@MainActivity, "Enter a number first", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phoneNumber)}")))
                }
            }
            addView(iconView("call", Color.WHITE, 27), FrameLayout.LayoutParams(dp(27), dp(27), Gravity.CENTER))
        }
        content.addView(callButton, LinearLayout.LayoutParams(dp(56), dp(56)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(4)
            bottomMargin = dp(2)
        })
    }

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun askPermission(permission: String) {
        requestPermissions(arrayOf(permission), permissionRequest)
    }

    private fun showPermissionMessage(content: LinearLayout, title: String, detail: String, permission: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(22))
            background = rounded(field, 24)
        }
        card.addView(text(title, 20f, ink, true))
        card.addView(text(detail, 14f, muted).apply { setPadding(0, dp(10), 0, dp(20)) })
        val allow = TextView(this).apply {
            text = "Allow access"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(green, 26)
            isClickable = true
            isFocusable = true
            setOnClickListener { askPermission(permission) }
        }
        card.addView(allow, LinearLayout.LayoutParams(-1, dp(48)))
        content.addView(card, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showContacts(content: LinearLayout) {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            showPermissionMessage(content, "Contacts permission",
                "তোমার ফোনের সেভ করা কন্টাক্ট দেখতে অনুমতি দাও।", Manifest.permission.READ_CONTACTS)
            return
        }

        val searchField = FrameLayout(this).apply { background = rounded(field, 26) }
        searchField.addView(iconView("search", muted, 22),
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.START or Gravity.CENTER_VERTICAL).apply {
                leftMargin = dp(15)
            })
        val search = EditText(this).apply {
            tag = "contactSearchInput"
            hint = "Search name or number"
            textSize = 15f
            setTextColor(ink)
            setHintTextColor(muted)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(0, 0, 0, 0)
            background = null
            setText(contactSearch)
            setSelection(text?.length ?: 0)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    contactSearch = this.text.toString().trim()
                    (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.hideSoftInputFromWindow(windowToken, 0)
                    render()
                    true
                } else false
            }
        }
        searchField.addView(search, FrameLayout.LayoutParams(-1, -1).apply {
            leftMargin = dp(48)
            rightMargin = dp(45)
        })
        searchField.addView(iconButton("search", "Search contacts", ink) {
            contactSearch = search.text.toString().trim()
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(search.windowToken, 0)
            render()
        }, FrameLayout.LayoutParams(dp(44), -1, Gravity.END or Gravity.CENTER_VERTICAL))
        content.addView(searchField, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(18) })

        val results = mutableListOf<Pair<String, String>>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val filter = contactSearch.trim()
        val selection: String?
        val args: Array<String>?
        if (filter.isNotEmpty()) {
            selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            args = arrayOf("%$filter%", "%$filter%")
        } else {
            selection = null
            args = null
        }
        try {
            contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, selection, args,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    results.add((cursor.getString(0) ?: "Unknown") to (cursor.getString(1) ?: ""))
                }
            }
        } catch (_: SecurityException) {
            showPermissionMessage(content, "Permission needed",
                "Contacts access পাওয়া যায়নি। আবার অনুমতি দাও।", Manifest.permission.READ_CONTACTS)
            return
        }

        if (results.isEmpty()) {
            content.addView(emptyCard("No contacts found",
                if (filter.isEmpty()) "এই ফোনে দেখানোর মতো কন্টাক্ট পাওয়া যায়নি।"
                else "অন্য নাম বা নম্বর দিয়ে খুঁজে দেখো."), LinearLayout.LayoutParams(-1, -2))
        } else {
            content.addView(text("All contacts  ·  ${results.size}", 13f, muted, true),
                LinearLayout.LayoutParams(-1, dp(30)))
            results.forEach { (name, number) ->
                addPersonRow(content, name, number, "call") {
                    phoneNumber = number
                    page = "Keypad"
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")))
                }
            }
        }
    }

    private fun showRecents(content: LinearLayout) {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            showPermissionMessage(content, "Call history permission",
                "সাম্প্রতিক কল দেখানোর জন্য call log access অনুমতি দাও।", Manifest.permission.READ_CALL_LOG)
            return
        }

        val calls = mutableListOf<Array<String>>()
        val projection = arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
        try {
            contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null,
                "${CallLog.Calls.DATE} DESC")?.use { cursor ->
                while (cursor.moveToNext() && calls.size < 100) {
                    val name = cursor.getString(0).orEmpty()
                    val number = cursor.getString(1).orEmpty()
                    val type = cursor.getInt(2)
                    val date = cursor.getLong(3)
                    val duration = cursor.getLong(4)
                    val typeText = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed call"
                        CallLog.Calls.REJECTED_TYPE -> "Rejected"
                        CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                        else -> "Call"
                    }
                    val time = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(date))
                    calls.add(arrayOf(name, number, typeText, time, duration.toString()))
                }
            }
        } catch (_: SecurityException) {
            showPermissionMessage(content, "Permission needed",
                "Call history access পাওয়া যায়নি। আবার অনুমতি দাও।", Manifest.permission.READ_CALL_LOG)
            return
        }

        if (calls.isEmpty()) {
            content.addView(emptyCard("No recent calls", "এই ফোনের call history-তে কোনো এন্ট্রি পাওয়া যায়নি."),
                LinearLayout.LayoutParams(-1, -2))
            return
        }
        content.addView(text("Recent activity", 13f, muted, true), LinearLayout.LayoutParams(-1, dp(30)))
        calls.forEach { item ->
            val name = item[0].ifBlank { item[1].ifBlank { "Unknown number" } }
            val duration = item[4].toLongOrNull() ?: 0L
            val sub = "${item[2]}  ·  ${item[3]}" +
                if (duration > 0) "  ·  ${duration / 60}m ${duration % 60}s" else ""
            addPersonRow(content, name, sub, "call") {
                val number = item[1]
                if (number.isNotBlank()) {
                    phoneNumber = number
                    page = "Keypad"
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")))
                }
            }
        }
    }

    private fun addPersonRow(parent: LinearLayout, title: String, subtitle: String,
                             trailingIcon: String, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val avatar = TextView(this).apply {
            text = initial
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.rgb(76, 91, 82))
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(239, 242, 241), 50)
        }
        row.addView(avatar, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(14) })
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        details.addView(text(title, 16f, ink, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        details.addView(text(subtitle, 12.5f,
            if (subtitle.startsWith("Missed call")) Color.rgb(185, 61, 61) else muted).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, 0)
        })
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(iconButton(trailingIcon, "Open dialer", green) { action() },
            LinearLayout.LayoutParams(dp(40), dp(46)))
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
        parent.addView(View(this).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun emptyCard(title: String, subtitle: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(field, 24)
            setPadding(dp(22), dp(30), dp(22), dp(30))
            addView(text(title, 19f, ink, true).apply { gravity = Gravity.CENTER })
            addView(text(subtitle, 13f, muted).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(9), 0, 0)
            })
        }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest) render()
    }
}
