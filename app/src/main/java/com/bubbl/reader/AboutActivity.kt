package com.bubbl.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        findViewById<TextView>(R.id.version).text = "versão ${BuildConfig.VERSION_NAME}"
        setAuthorLine(findViewById(R.id.authorLine))

        findViewById<MaterialButton>(R.id.btnGithub).setOnClickListener { openUrl(R.string.github_url) }
        findViewById<MaterialButton>(R.id.btnEmail).setOnClickListener {
            startActivity(Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${getString(R.string.contact_email)}")
                putExtra(Intent.EXTRA_SUBJECT, "Bubbl.")
            })
        }
        findViewById<MaterialButton>(R.id.btnSponsors).setOnClickListener { openUrl(R.string.sponsors_url) }
        findViewById<MaterialButton>(R.id.btnKofi).setOnClickListener { openUrl(R.string.kofi_url) }
        findViewById<MaterialButton>(R.id.btnBmc).setOnClickListener { openUrl(R.string.bmc_url) }
        findViewById<MaterialButton>(R.id.btnCopyPix).setOnClickListener {
            val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip.setPrimaryClip(ClipData.newPlainText("PIX", getString(R.string.pix_payload)))
            Toast.makeText(this, "PIX copia-e-cola copiado", Toast.LENGTH_SHORT).show()
        }
    }

    /** "Feito com 💜 em Fortaleza-CE por @prof.walterfr" — handle clicável -> Instagram. */
    private fun setAuthorLine(tv: TextView) {
        // resource trima espaço final; garante separador com nbsp
        val prefix = getString(R.string.about_made_in_prefix).trimEnd() + " "
        val handle = getString(R.string.ig_handle)
        val sb = SpannableStringBuilder(prefix).append(handle)
        val start = prefix.length
        val end = sb.length
        sb.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) = openUrl(R.string.instagram_url)
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.violet)),
            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tv.text = sb
        tv.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun openUrl(resId: Int) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(resId)))) }
    }
}
