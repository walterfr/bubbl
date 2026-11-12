package com.bubbl.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.github.junrar.Archive
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Progresso da carga: (páginas prontas, total ou nulo se desconhecido). */
typealias LoadProgress = (done: Int, total: Int?) -> Unit

/**
 * Converte qualquer entrada (arquivo comic ou pasta) numa lista ordenada de Uris de página.
 * Estratégia única: extrai/renderiza tudo para o cacheDir como imagens e devolve file:// Uris,
 * assim o SubsamplingScaleImageView faz tiling nativo em qualquer formato.
 *
 * As cargas são `suspend` e canceláveis (checam o Job a cada página). [LoadProgress] recebe
 * páginas prontas e o total (nulo quando desconhecido, como no ZIP lido por stream);
 * é chamado na thread de IO — quem atualiza UI deve repostar na main.
 */
object PageLoader {

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    class UnsupportedFormat(msg: String) : Exception(msg)

    /** Ordena páginas de forma natural: 1,2,10 (não 1,10,2). Extraído p/ ser testável. */
    val naturalOrder = Comparator<String> { a, b -> compareNatural(a, b) }

    fun compareNatural(a: String, b: String): Int {
        var i = 0; var j = 0
        val sa = a.lowercase(); val sb = b.lowercase()
        while (i < sa.length && j < sb.length) {
            val ca = sa[i]; val cb = sb[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ni = i; while (ni < sa.length && sa[ni].isDigit()) ni++
                var nj = j; while (nj < sb.length && sb[nj].isDigit()) nj++
                // compara numericamente ignorando zeros à esquerda
                val na = sa.substring(i, ni).trimStart('0').ifEmpty { "0" }
                val nb = sb.substring(j, nj).trimStart('0').ifEmpty { "0" }
                if (na.length != nb.length) return na.length - nb.length
                val cmp = na.compareTo(nb)
                if (cmp != 0) return cmp
                i = ni; j = nj
            } else {
                if (ca != cb) return ca - cb
                i++; j++
            }
        }
        return (sa.length - i) - (sb.length - j)
    }

    /** Chamada em thread de IO. Devolve páginas prontas. */
    suspend fun load(ctx: Context, uri: Uri, onProgress: LoadProgress = { _, _ -> }): List<Uri> {
        val name = displayName(ctx, uri)
        BookHolder.title = name.substringBeforeLast('.')
        val work = freshDir(ctx)

        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in IMAGE_EXT -> listOf(copyTo(ctx, uri, File(work, "0.$ext"))).also { onProgress(1, 1) }
            ext == "cbz" || ext == "zip" || ext == "epub" -> fromZip(ctx, uri, work, onProgress)
            ext == "cbr" || ext == "rar" -> fromRar(ctx, uri, work, onProgress)
            ext == "pdf" -> fromPdf(ctx, uri, work, onProgress)
            else -> throw UnsupportedFormat("Formato não suportado: .$ext")
        }
    }

    /** Pasta escolhida via ACTION_OPEN_DOCUMENT_TREE. */
    suspend fun loadFolder(ctx: Context, treeUri: Uri, onProgress: LoadProgress = { _, _ -> }): List<Uri> {
        val dir = DocumentFile.fromTreeUri(ctx, treeUri)
            ?: throw UnsupportedFormat("Pasta inválida")
        BookHolder.title = dir.name ?: "Pasta"
        val files = dir.listFiles()
        // cada name/isFile é uma consulta ao provider: progresso e cancelamento por item
        val images = mutableListOf<Pair<String, Uri>>()
        files.forEachIndexed { i, f ->
            currentCoroutineContext().ensureActive()
            val name = f.name ?: ""
            if (f.isFile && name.substringAfterLast('.', "").lowercase() in IMAGE_EXT) images.add(name to f.uri)
            onProgress(i + 1, files.size)
        }
        return images.sortedWith(compareBy(naturalOrder) { it.first }).map { it.second }
    }

    // ---- formatos ----

    private suspend fun fromZip(ctx: Context, uri: Uri, work: File, onProgress: LoadProgress): List<Uri> {
        val out = mutableListOf<Pair<String, File>>()
        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
            ZipInputStream(ins).use { zip ->
                var e = zip.nextEntry
                var idx = 0
                while (e != null) {
                    currentCoroutineContext().ensureActive()
                    val entryName = e.name
                    if (!e.isDirectory && entryName.substringAfterLast('.', "").lowercase() in IMAGE_EXT) {
                        val f = File(work, "%05d_%s".format(idx++, entryName.substringAfterLast('/')))
                        FileOutputStream(f).use { zip.copyTo(it) }
                        out.add(entryName to f)
                        onProgress(out.size, null) // stream: total desconhecido
                    }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
        }
        if (out.isEmpty()) throw UnsupportedFormat("Nenhuma imagem no arquivo")
        return out.sortedWith(compareBy(naturalOrder) { it.first }).map { Uri.fromFile(it.second) }
    }

    private suspend fun fromRar(ctx: Context, uri: Uri, work: File, onProgress: LoadProgress): List<Uri> {
        val tmp = copyToFile(ctx, uri, File(work, "_in.rar"))
        val out = mutableListOf<Pair<String, File>>()
        Archive(tmp).use { arc ->
            val images = arc.fileHeaders.filter { h ->
                !h.isDirectory &&
                    h.fileName.replace('\\', '/').substringAfterLast('.', "").lowercase() in IMAGE_EXT
            }
            images.forEachIndexed { idx, h ->
                currentCoroutineContext().ensureActive()
                val hn = h.fileName.replace('\\', '/')
                val f = File(work, "%05d_%s".format(idx, hn.substringAfterLast('/')))
                FileOutputStream(f).use { arc.extractFile(h, it) }
                out.add(hn to f)
                onProgress(out.size, images.size)
            }
        }
        tmp.delete()
        if (out.isEmpty()) throw UnsupportedFormat("Nenhuma imagem no CBR")
        return out.sortedWith(compareBy(naturalOrder) { it.first }).map { Uri.fromFile(it.second) }
    }

    private suspend fun fromPdf(ctx: Context, uri: Uri, work: File, onProgress: LoadProgress): List<Uri> {
        val tmp = copyToFile(ctx, uri, File(work, "_in.pdf"))
        val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
        val pages = mutableListOf<Uri>()
        PdfRenderer(pfd).use { renderer ->
            for (i in 0 until renderer.pageCount) {
                currentCoroutineContext().ensureActive()
                renderer.openPage(i).use { page ->
                    // ~2000px de largura p/ leitura nítida sem estourar memória
                    val scale = (2000f / page.width).coerceIn(1f, 4f)
                    val w = (page.width * scale).toInt()
                    val h = (page.height * scale).toInt()
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val f = File(work, "%05d.png".format(i))
                    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                    pages.add(Uri.fromFile(f))
                    onProgress(pages.size, renderer.pageCount)
                }
            }
        }
        tmp.delete()
        if (pages.isEmpty()) throw UnsupportedFormat("PDF sem páginas")
        return pages
    }

    // ---- util ----

    private fun freshDir(ctx: Context): File {
        val d = File(ctx.cacheDir, "book")
        if (d.exists()) d.deleteRecursively()
        d.mkdirs()
        return d
    }

    private fun copyTo(ctx: Context, uri: Uri, dest: File): Uri =
        Uri.fromFile(copyToFile(ctx, uri, dest))

    private fun copyToFile(ctx: Context, uri: Uri, dest: File): File {
        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
            FileOutputStream(dest).use { ins.copyTo(it) }
        }
        return dest
    }

    private fun displayName(ctx: Context, uri: Uri): String {
        if (uri.scheme == "file") return File(uri.path!!).name
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) return c.getString(i)
        }
        return uri.lastPathSegment ?: "livro"
    }
}
