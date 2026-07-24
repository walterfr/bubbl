package com.bubbl.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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
