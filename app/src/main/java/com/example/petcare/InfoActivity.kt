package com.example.petcare

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import kotlin.math.roundToInt

/** In-app Help Center, Privacy Policy and Terms and Conditions (content in [InfoContent]). */
class InfoActivity : AppCompatActivity() {

    enum class Page(val title: String) {
        HELP("Help Center"),
        PRIVACY("Privacy Policy"),
        TERMS("Terms and Conditions")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_info)

        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val page = Page.entries.getOrNull(intent.getIntExtra(EXTRA_PAGE, 0)) ?: Page.HELP
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = page.title
            setNavigationOnClickListener { finish() }
        }

        val subtitle = findViewById<TextView>(R.id.textInfoSubtitle)
        when (page) {
            Page.HELP -> {
                subtitle.text = "Answers to common questions. Tap a question to expand it."
                renderFaq()
                findViewById<View>(R.id.cardContactSupport).visibility = View.VISIBLE
                findViewById<View>(R.id.buttonContactSupport).setOnClickListener { emailSupport() }
            }
            Page.PRIVACY -> {
                subtitle.text = "Last updated ${InfoContent.LAST_UPDATED}"
                renderDocument(InfoContent.privacy)
            }
            Page.TERMS -> {
                subtitle.text = "Last updated ${InfoContent.LAST_UPDATED}"
                renderDocument(InfoContent.terms)
            }
        }

        findViewById<TextView>(R.id.textAppVersion).text = "PetCare ${appVersion()}"
    }

    /** Question rows that expand to show the answer. */
    private fun renderFaq() {
        val container = findViewById<LinearLayout>(R.id.layoutInfoSections)
        InfoContent.faq.forEachIndexed { index, item ->
            if (index > 0) container.addView(divider())

            val answer = bodyText(item.body).apply {
                visibility = View.GONE
                setPadding(16.dp(), 0, 16.dp(), 16.dp())
            }
            val chevron = ImageView(this).apply {
                setImageResource(R.drawable.ic_chevron_right)
                imageTintList = ContextCompat.getColorStateList(this@InfoActivity, R.color.app_text_secondary)
                rotation = 90f
                layoutParams = LinearLayout.LayoutParams(20.dp(), 20.dp())
            }
            val question = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = 56.dp()
                setPadding(16.dp(), 12.dp(), 12.dp(), 12.dp())
                isClickable = true
                isFocusable = true
                setBackgroundResource(selectableBackground())
                addView(TextView(this@InfoActivity).apply {
                    text = item.title
                    setTextColor(color(R.color.app_text_primary))
                    textSize = 15f
                    typeface = context.figtree(android.graphics.Typeface.BOLD)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(chevron)
                setOnClickListener {
                    val expand = answer.visibility != View.VISIBLE
                    answer.visibility = if (expand) View.VISIBLE else View.GONE
                    chevron.animate().rotation(if (expand) 270f else 90f).setDuration(150).start()
                    contentDescription = "${item.title}, ${if (expand) "expanded" else "collapsed"}"
                }
                contentDescription = "${item.title}, collapsed"
            }
            container.addView(question)
            container.addView(answer)
        }
    }

    /** Numbered headings with body text, for the policy pages. */
    private fun renderDocument(sections: List<InfoContent.Section>) {
        val container = findViewById<LinearLayout>(R.id.layoutInfoSections)
        container.setPadding(0, 4.dp(), 0, 8.dp())
        sections.forEachIndexed { index, section ->
            container.addView(TextView(this).apply {
                text = "${index + 1}. ${section.title}"
                setTextColor(color(R.color.app_text_primary))
                textSize = 15f
                typeface = context.figtree(android.graphics.Typeface.BOLD)
                setPadding(16.dp(), 14.dp(), 16.dp(), 4.dp())
            })
            container.addView(bodyText(section.body).apply { setPadding(16.dp(), 0, 16.dp(), 6.dp()) })
        }
    }

    private fun bodyText(value: String) = TextView(this).apply {
        text = value
        setTextColor(color(R.color.app_text_secondary))
        textSize = 14f
        setLineSpacing(4.dp().toFloat(), 1f)
        setTextIsSelectable(true)
    }

    private fun emailSupport() {
        // Pre-fill device details so support can help without back-and-forth.
        val body = "\n\n---\nApp: PetCare ${appVersion()}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}"
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(InfoContent.SUPPORT_EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, "PetCare support")
            .putExtra(Intent.EXTRA_TEXT, body)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No email app found. Write to ${InfoContent.SUPPORT_EMAIL}", Toast.LENGTH_LONG).show()
        }
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(color(R.color.md_outline_variant))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { marginStart = 16.dp() }
    }

    private fun selectableBackground(): Int {
        val value = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        return value.resourceId
    }

    private fun color(res: Int) = ContextCompat.getColor(this, res)

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_PAGE = "extra_info_page"

        fun open(context: Context, page: Page) {
            context.startActivity(Intent(context, InfoActivity::class.java).putExtra(EXTRA_PAGE, page.ordinal))
        }
    }
}
