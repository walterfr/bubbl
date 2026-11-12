package com.bubbl.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
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
    private lateinit var recentSection: View
    private lateinit var recentList: LinearLayout
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
        recentSection = findViewById(R.id.recentSection)
        recentList = findViewById(R.id.recentList)

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

    override fun onResume() {
        super.onResume()
        refreshRecents()
    }

    // ---- Continuar lendo ----

    private fun refreshRecents() {
        val items = ReadingStore.all(this)
        recentList.removeAllViews()
        recentSection.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        val inflater = LayoutInflater.from(this)
        items.forEach { e ->
            val v = inflater.inflate(R.layout.item_recent, recentList, false)
            v.findViewById<ImageView>(R.id.recentIcon)
                .setImageResource(if (e.folder) R.drawable.ic_folder else R.drawable.ic_file)
            v.findViewById<TextView>(R.id.recentTitle).text = e.title
            v.findViewById<TextView>(R.id.recentProgress).text =
                getString(R.string.recent_progress, e.page + 1, e.total)
            v.setOnClickListener { if (loadJob?.isActive != true) load(Uri.parse(e.key), e.folder, e) }
            v.setOnLongClickListener { removeRecent(e); true }
            recentList.addView(v)
        }
    }

    /** Remove com "Desfazer"; só solta a permissão do Uri se não desfizer. */
    private fun removeRecent(e: BookEntry) {
        ReadingStore.remove(this, e.key)
        refreshRecents()
        var undone = false
        Snackbar.make(findViewById(R.id.root), getString(R.string.recent_removed, e.title), Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                undone = true
                ReadingStore.upsert(this, e)
                refreshRecents()
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    // reaberto nesse meio-tempo? então a permissão ainda é usada
                    if (!undone && ReadingStore.get(this@MainActivity, e.key) == null) {
                        ReadingStore.releasePermission(this@MainActivity, e.key)
                    }
                }
            })
            .show()
    }

    /** Recente que não abre mais (movido/apagado/permissão perdida): oferece remover. */
    private fun recentFailed(e: BookEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(e.title)
            .setMessage(R.string.recent_unavailable)
            .setPositiveButton(R.string.remove) { _, _ ->
                ReadingStore.remove(this, e.key)
                ReadingStore.releasePermission(this, e.key)
                refreshRecents()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    // ---- abertura ----

    private fun open(uri: Uri, folder: Boolean) = load(uri, folder, recent = null)

    /** [recent] != null: aberto pela lista; falha vira o diálogo de remover. */
    private fun load(uri: Uri, folder: Boolean, recent: BookEntry?) {
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
                if (recent != null) recentFailed(recent)
                else toast(e.message ?: getString(R.string.open_failed))
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
