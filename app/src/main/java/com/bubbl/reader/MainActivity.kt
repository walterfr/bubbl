package com.bubbl.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var loadingBox: View
    private lateinit var progressText: TextView
    private lateinit var btnOpenFile: Button
    private lateinit var btnOpenFolder: Button
    private var loadJob: Job? = null

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
        loadingBox = findViewById(R.id.loadingBox)
        progressText = findViewById(R.id.progressText)
        btnOpenFile = findViewById(R.id.btnOpenFile)
        btnOpenFolder = findViewById(R.id.btnOpenFolder)

        btnOpenFile.setOnClickListener {
            // MIME amplo; muitos comics não têm MIME registrado, então */*
            pickFile.launch(arrayOf("*/*"))
        }
        btnOpenFolder.setOnClickListener {
            pickFolder.launch(null)
        }
        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            // a carga para no próximo item; some já, sem toast de erro
            loadJob?.cancel()
            showLoading(false)
        }
        findViewById<android.widget.ImageButton>(R.id.btnAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun open(uri: Uri, folder: Boolean) {
        // mantém o acesso após reiniciar p/ reabrir dos recentes
        ReadingStore.takePermission(this, uri)
        val previous = loadJob
        previous?.cancel()
        showLoading(true)
        loadJob = lifecycleScope.launch {
            val job = coroutineContext[Job]
            // espera a carga anterior parar: as duas usariam o mesmo dir de cache
            previous?.join()
            try {
                val pages = withContext(Dispatchers.IO) {
                    val onProgress: LoadProgress = { done, total ->
                        runOnUiThread {
                            if (loadJob === job && job?.isActive == true) showProgress(done, total)
                        }
                    }
                    if (folder) PageLoader.loadFolder(this@MainActivity, uri, onProgress)
                    else PageLoader.load(this@MainActivity, uri, onProgress)
                }
                if (pages.isEmpty()) {
                    toast(getString(R.string.no_pages))
                } else {
                    BookHolder.pages = pages
                    BookHolder.source = uri.toString()
                    ReadingStore.opened(this@MainActivity, uri.toString(), folder, BookHolder.title, pages.size)
                    startActivity(ReaderActivity.intent(this@MainActivity, uri, folder))
                }
            } catch (e: CancellationException) {
                throw e // cancelado pelo usuário (ou Activity destruída): sem erro
            } catch (e: Throwable) {
                toast(e.message ?: getString(R.string.open_failed))
            } finally {
                if (loadJob === job) showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingBox.visibility = if (show) View.VISIBLE else View.GONE
        btnOpenFile.isEnabled = !show
        btnOpenFolder.isEnabled = !show
        if (show) progressText.setText(R.string.opening)
    }

    private fun showProgress(done: Int, total: Int?) {
        progressText.text =
            if (total != null) getString(R.string.opening_progress, done, total)
            else getString(R.string.opening_count, done)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
