package com.bubbl.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var progress: ProgressBar

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { open(it, folder = false) } }

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { open(it, folder = true) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.root).padForInsets()
        progress = findViewById(R.id.progress)

        findViewById<Button>(R.id.btnOpenFile).setOnClickListener {
            // MIME amplo; muitos comics não têm MIME registrado, então */*
            pickFile.launch(arrayOf("*/*"))
        }
        findViewById<Button>(R.id.btnOpenFolder).setOnClickListener {
            pickFolder.launch(null)
        }
        findViewById<android.widget.ImageButton>(R.id.btnAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        if (savedInstanceState == null) handleViewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    /** "Abrir com…": arquivo vindo de outro app via ACTION_VIEW. */
    private fun handleViewIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return
        // reaberto pelo histórico de recentes: não reabrir o mesmo arquivo
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
        val uri = intent.data ?: return
        open(uri, folder = false)
    }

    private fun open(uri: Uri, folder: Boolean) {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (folder) PageLoader.loadFolder(this@MainActivity, uri)
                    else PageLoader.load(this@MainActivity, uri)
                }
            }
            progress.visibility = View.GONE
            result.onSuccess { pages ->
                if (pages.isEmpty()) {
                    toast("Nenhuma página encontrada")
                } else {
                    BookHolder.pages = pages
                    startActivity(Intent(this@MainActivity, ReaderActivity::class.java))
                }
            }.onFailure { toast(it.message ?: "Falha ao abrir") }
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
