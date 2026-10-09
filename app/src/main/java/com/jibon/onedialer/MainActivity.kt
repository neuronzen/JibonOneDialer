package com.jibon.onedialer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private var page = "Keypad"
    private var phoneNumber = ""
    private var contactSearch = ""

    private val ink = Color.rgb(30, 34, 40)
    private val muted = Color.rgb(119, 126, 135)
    private val green = Color.rgb(28, 150, 91)
    private val paper = Color.rgb(247, 248, 250)
    private val white = Color.WHITE
    private val line = Color.rgb(232, 235, 239)
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

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    private fun text(
        value: String,
        size: Float,
        color: Int = ink,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        if (bold) typeface = Typeface.create(
            "sans-serif-medium", Typeface.NORMAL
        )
    }

    private fun rounded(color: Int, radius: Int = 22): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun button(
        title: String,
        background: Int = white,
        foreground: Int = ink,
        size: Float = 16f,
        action: () -> Unit
    ) = TextView(this).apply {
        text = title
        textSize = size
        setTextColor(foreground)
        gravity = Gravity.CENTER
        this.background = rounded(background, 24)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun render() {
        root.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(8))
        }
        header.addView(text("Jibon One", 14f, green, true))
        header.addView(
            text(
                when (page) {
                    "Keypad" -> "Phone"
                    "Recents" -> "Recent calls"
                    else -> "Contacts"
                },
                30f, ink, true
            ),
            LinearLayout.LayoutParams(-1, dp(48))
        )
        root.addView(header)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(4), dp(14), dp(12))
        }
        listOf("Keypad", "Recents", "Contacts").forEach { name ->
            val active = page == name
            val tab = button(
                name,
                if (active) ink else paper,
                if (active) white else muted,
                14f
            ) {
                page = name
                render()
            }
            tabs.addView(
                tab,
                LinearLayout.LayoutParams(0, dp(43), 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
            )
        }
        root.addView(tabs)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(16), dp(4), dp(16), dp(12))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        when (page) {
            "Keypad" -> showKeypad(content)
            "Recents" -> showRecents(content)
            else -> showContacts(content)
        }
    }

    private fun showKeypad(content: LinearLayout) {
        val display = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(18), dp(12), dp(12))
            background = rounded(white, 28)
        }
        display.addView(
            text(
                if (phoneNumber.isBlank()) "Enter number" else phoneNumber,
                if (phoneNumber.length > 13) 23f else 29f,
                if (phoneNumber.isBlank()) muted else ink,
                true
            ).apply {
                gravity = Gravity.CENTER
                maxLines = 2
            },
            LinearLayout.LayoutParams(-1, dp(65))
        )
        content.addView(
            display,
            LinearLayout.LayoutParams(-1, dp(105)).apply {
                bottomMargin = dp(14)
            }
        )

        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        )
        val hints = listOf(
            listOf("", "ABC", "DEF"),
            listOf("GHI", "JKL", "MNO"),
            listOf("PQRS", "TUV", "WXYZ"),
            listOf("", "+", "")
        )

        keys.forEachIndexed { rowIndex, rowKeys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            rowKeys.forEachIndexed { colIndex, key ->
                val keyView = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = rounded(white, 24)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        phoneNumber += key
                        render()
                    }
                }
                keyView.addView(
                    text(key, 25f, ink, true).apply {
                        gravity = Gravity.CENTER
                    }
                )
                if (hints[rowIndex][colIndex].isNotEmpty()) {
                    keyView.addView(
                        text(hints[rowIndex][colIndex], 9f, muted).apply {
                            gravity = Gravity.CENTER
                        }
                    )
                }
                row.addView(
                    keyView,
                    LinearLayout.LayoutParams(0, dp(66), 1f).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    }
                )
            }
            content.addView(row)
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), dp(4), dp(8))
        }
        actions.addView(
            button("⌫", white, ink, 25f) {
                if (phoneNumber.isNotEmpty()) {
                    phoneNumber = phoneNumber.dropLast(1)
                    render()
                }
            },
            LinearLayout.LayoutParams(dp(64), dp(58)).apply {
                rightMargin = dp(12)
            }
        )
        actions.addView(
            button("Call", green, white, 19f) {
                if (phoneNumber.isNotBlank()) {
                    val intent = Intent(
                        Intent.ACTION_DIAL,
                        Uri.parse("tel:${Uri.encode(phoneNumber)}")
                    )
                    startActivity(intent)
                }
            },
            LinearLayout.LayoutParams(0, dp(58), 1f)
        )
        content.addView(actions)

        content.addView(
            text("Tap a number to begin", 12f, muted).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(12))
            }
        )
    }

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun askPermission(permission: String) {
        requestPermissions(arrayOf(permission), permissionRequest)
    }

    private fun showPermissionMessage(
        content: LinearLayout,
        title: String,
        detail: String,
        permission: String
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(white, 26)
            setPadding(dp(20), dp(22), dp(20), dp(22))
        }
        card.addView(text(title, 20f, ink, true))
        card.addView(
            text(detail, 14f, muted).apply {
                setPadding(0, dp(10), 0, dp(18))
            }
        )
        card.addView(
            button("Allow access", green, white) {
                askPermission(permission)
            },
            LinearLayout.LayoutParams(-1, dp(48))
        )
        content.addView(card)
    }

    private fun showContacts(content: LinearLayout) {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            showPermissionMessage(
                content,
                "Contacts permission",
                "তোমার ফোনের সেভ করা কন্টাক্ট দেখতে অনুমতি দাও।",
                Manifest.permission.READ_CONTACTS
            )
            return
        }

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val search = EditText(this).apply {
            hint = "Search name or number"
            isSingleLine = true
            textSize = 15f
            setText(contactSearch)
            setPadding(dp(14), 0, dp(8), 0)
            background = rounded(white, 18)
        }
        searchRow.addView(
            search,
            LinearLayout.LayoutParams(0, dp(50), 1f).apply {
                rightMargin = dp(8)
            }
        )
        searchRow.addView(
            button("Search", ink, white, 13f) {
                contactSearch = search.text.toString().trim()
                render()
            },
            LinearLayout.LayoutParams(dp(78), dp(50))
        )
        content.addView(searchRow)

        val results = mutableListOf<Pair<String, String>>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val filter = contactSearch.trim()
        val selection: String?
        val args: Array<String>?
        if (filter.isNotEmpty()) {
            selection =
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            args = arrayOf("%$filter%", "%$filter%")
        } else {
            selection = null
            args = null
        }

        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                args,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: "Unknown"
                    val number = cursor.getString(1) ?: ""
                    results.add(name to number)
                }
            }
        } catch (_: SecurityException) {
            showPermissionMessage(
                content, "Permission needed",
                "Contacts access পাওয়া যায়নি। আবার অনুমতি দাও।",
                Manifest.permission.READ_CONTACTS
            )
            return
        }

        if (results.isEmpty()) {
            content.addView(emptyCard(
                "No contacts found",
                if (filter.isEmpty()) "এই ফোনে দেখানোর মতো কন্টাক্ট পাওয়া যায়নি।"
                else "অন্য নাম বা নম্বর দিয়ে খুঁজে দেখো।"
            ))
        } else {
            content.addView(
                text("${results.size} phone entries", 12f, muted).apply {
                    setPadding(dp(4), dp(14), 0, dp(8))
                }
            )
            results.forEach { (name, number) ->
                addPersonCard(content, name, number) {
                    phoneNumber = number
                    page = "Keypad"
                    startActivity(
                        Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse("tel:${Uri.encode(number)}")
                        )
                    )
                }
            }
        }
    }

    private fun showRecents(content: LinearLayout) {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            showPermissionMessage(
                content,
                "Call history permission",
                "সাম্প্রতিক কল দেখানোর জন্য call log access অনুমতি দাও।",
                Manifest.permission.READ_CALL_LOG
            )
            return
        }

        val calls = mutableListOf<Array<String>>()
        val projection = arrayOf(
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
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
                    val time = SimpleDateFormat(
                        "dd MMM, hh:mm a", Locale.getDefault()
                    ).format(Date(date))
                    calls.add(arrayOf(name, number, typeText, time, duration.toString()))
                }
            }
        } catch (_: SecurityException) {
            showPermissionMessage(
                content, "Permission needed",
                "Call history access পাওয়া যায়নি। আবার অনুমতি দাও।",
                Manifest.permission.READ_CALL_LOG
            )
            return
        }

        if (calls.isEmpty()) {
            content.addView(emptyCard(
                "No recent calls",
                "এই ফোনের call history-তে কোনো এন্ট্রি পাওয়া যায়নি।"
            ))
            return
        }

        calls.forEach { item ->
            val name = item[0].ifBlank { item[1].ifBlank { "Unknown number" } }
            val duration = item[4].toLongOrNull() ?: 0L
            val sub = "${item[2]}  •  ${item[3]}" +
                if (duration > 0) "  •  ${duration / 60}m ${duration % 60}s"
                else ""
            addPersonCard(content, name, sub) {
                val number = item[1]
                if (number.isNotBlank()) {
                    phoneNumber = number
                    page = "Keypad"
                    startActivity(
                        Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse("tel:${Uri.encode(number)}")
                        )
                    )
                }
            }
        }
    }

    private fun addPersonCard(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        action: () -> Unit
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(white, 22)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        val initial = title.firstOrNull()?.uppercaseChar()?.toString() ?: "•"
        val avatar = TextView(this).apply {
            text = initial
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(226, 245, 235), 30)
        }
        card.addView(
            avatar,
            LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                rightMargin = dp(12)
            }
        )
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        details.addView(text(title, 16f, ink, true))
        details.addView(
            text(subtitle, 12f, muted).apply {
                maxLines = 2
                setPadding(0, dp(4), 0, 0)
            }
        )
        card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(
            card,
            LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        )
    }

    private fun emptyCard(title: String, subtitle: String) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(white, 24)
            setPadding(dp(18), dp(28), dp(18), dp(28))
            addView(text(title, 19f, ink, true).apply {
                gravity = Gravity.CENTER
            })
            addView(text(subtitle, 13f, muted).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
        }.also {
            // Keep empty-state cards comfortably spaced.
            it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            }
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest) render()
    }
}
