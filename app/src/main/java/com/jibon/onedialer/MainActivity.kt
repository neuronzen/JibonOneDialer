package com.jibon.onedialer

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private var page = "Keypad"
    private var phoneNumber = ""

    private val ink = Color.rgb(32, 35, 39)
    private val muted = Color.rgb(112, 117, 123)
    private val green = Color.rgb(35, 150, 95)
    private val paper = Color.rgb(248, 249, 247)

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
        (value * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, color: Int): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
        }

    private fun addTabRow() {
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("Keypad", "Recents", "Contacts").forEach { name ->
            val tab = Button(this).apply {
                text = name
                isAllCaps = false
                textSize = 14f
                setTextColor(if (page == name) green else muted)
                setOnClickListener {
                    page = name
                    render()
                }
                backgroundTintList =
                    android.content.res.ColorStateList.valueOf(paper)
            }
            tabs.addView(tab, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(tabs)
    }

    private fun makeButton(
        text: String,
        onTap: () -> Unit
    ): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 22f
        setTextColor(ink)
        setOnClickListener { onTap() }
        backgroundTintList =
            android.content.res.ColorStateList.valueOf(Color.WHITE)
    }

    private fun render() {
        root.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }
        val title = TextView(this).apply {
            text = "Phone"
            textSize = 29f
            setTextColor(ink)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        header.addView(
            title,
            LinearLayout.LayoutParams(0, dp(52), 1f)
        )
        header.addView(label("⋮", 28f, ink))
        root.addView(header)
        addTabRow()

        when (page) {
            "Keypad" -> showKeypad()
            "Recents" -> showEmptyPage(
                "Recent calls",
                "Your recent calls will appear here in a later version."
            )
            else -> showEmptyPage(
                "Contacts",
                "Contact search and saved contacts will be added next."
            )
        }
    }

    private fun showKeypad() {
        val number = label(
            if (phoneNumber.isEmpty()) "Enter number" else phoneNumber,
            27f,
            if (phoneNumber.isEmpty()) muted else ink
        )
        number.setPadding(dp(12), dp(16), dp(12), dp(8))
        number.maxLines = 2
        root.addView(number, LinearLayout.LayoutParams(-1, dp(82)))

        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        )

        keys.forEach { rowKeys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowKeys.forEach { key ->
                val btn = makeButton(key) {
                    phoneNumber += key
                    render()
                }
                row.addView(
                    btn,
                    LinearLayout.LayoutParams(0, dp(61), 1f).apply {
                        setMargins(dp(5), dp(3), dp(5), dp(3))
                    }
                )
            }
            root.addView(row)
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(5), dp(24), dp(4))
        }

        val delete = makeButton("⌫") {
            if (phoneNumber.isNotEmpty()) {
                phoneNumber = phoneNumber.dropLast(1)
                render()
            }
        }
        delete.textSize = 24f
        actions.addView(
            delete,
            LinearLayout.LayoutParams(dp(64), dp(55))
        )

        val call = Button(this).apply {
            text = "Call"
            isAllCaps = false
            textSize = 18f
            setTextColor(Color.WHITE)
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(green)
            setOnClickListener {
                if (phoneNumber.isNotBlank()) {
                    val intent = Intent(
                        Intent.ACTION_DIAL,
                        Uri.parse("tel:${Uri.encode(phoneNumber)}")
                    )
                    startActivity(intent)
                }
            }
        }
        actions.addView(
            call,
            LinearLayout.LayoutParams(dp(150), dp(55)).apply {
                leftMargin = dp(18)
            }
        )
        root.addView(actions)
        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, 0, 1f)
        )
        root.addView(
            label("Jibon One Dialer • UI preview", 12f, muted),
            LinearLayout.LayoutParams(-1, dp(32))
        )
    }

    private fun showEmptyPage(titleText: String, message: String) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(20), dp(28), dp(20))
        }
        val title = label(titleText, 23f, ink)
        title.typeface = Typeface.DEFAULT_BOLD
        content.addView(title)
        val description = label(message, 15f, muted)
        description.setPadding(0, dp(12), 0, 0)
        content.addView(description)
        root.addView(
            content,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )
    }
}
