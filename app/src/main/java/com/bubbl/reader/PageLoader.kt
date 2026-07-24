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

/**
 * Converte qualquer entrada (arquivo comic ou pasta) numa lista ordenada de Uris de página.
 * Estratégia única: extrai/renderiza tudo para o cacheDir como imagens e devolve file:// Uris,
 * assim o SubsamplingScaleImageView faz tiling nativo em qualquer formato.
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
    fun load(ctx: Context, uri: Uri): List<Uri> {
        val name = displayName(ctx, uri)
        BookHolder.title = name.substringBeforeLast('.')
        val work = freshDir(ctx)

        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in IMAGE_EXT -> listOf(copyTo(ctx, uri, File(work, "0.$ext")))
            ext == "cbz" || ext == "zip" || ext == "epub" -> fromZip(ctx, uri, work)
            ext == "cbr" || ext == "rar" -> fromRar(ctx, uri, work)
            ext == "pdf" -> fromPdf(ctx, uri, work)
            else -> throw UnsupportedFormat("Formato não suportado: .$ext")
        }
    }

    /** Pasta escolhida via ACTION_OPEN_DOCUMENT_TREE. */
    fun loadFolder(ctx: Context, treeUri: Uri): List<Uri> {
        val dir = DocumentFile.fromTreeUri(ctx, treeUri)
            ?: throw UnsupportedFormat("Pasta inválida")
        BookHolder.title = dir.name ?: "Pasta"
        return dir.listFiles()
            .filter { it.isFile && (it.name ?: "").substringAfterLast('.', "").lowercase() in IMAGE_EXT }
            .sortedWith(compareBy(naturalOrder) { it.name ?: "" })
            .map { it.uri }
    }

    // ---- formatos ----

    private fun fromZip(ctx: Context, uri: Uri, work: File): List<Uri> {
        val out = mutableListOf<Pair<String, File>>()
        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
            ZipInputStream(ins).use { zip ->
                var e = zip.nextEntry
                var idx = 0
                while (e != null) {
                    val entryName = e.name
                    if (!e.isDirectory && entryName.substringAfterLast('.', "").lowercase() in IMAGE_EXT) {
                        val f = File(work, "%05d_%s".format(idx++, entryName.substringAfterLast('/')))
                        FileOutputStream(f).use { zip.copyTo(it) }
                        out.add(entryName to f)
                    }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
        }
        if (out.isEmpty()) throw UnsupportedFormat("Nenhuma imagem no arquivo")
        return out.sortedWith(compareBy(naturalOrder) { it.first }).map { Uri.fromFile(it.second) }
    }

    private fun fromRar(ctx: Context, uri: Uri, work: File): List<Uri> {
        val tmp = copyToFile(ctx, uri, File(work, "_in.rar"))
        val out = mutableListOf<Pair<String, File>>()
        Archive(tmp).use { arc ->
            var idx = 0
            for (h in arc.fileHeaders) {
                if (h.isDirectory) continue
                val hn = h.fileName.replace('\\', '/')
                if (hn.substringAfterLast('.', "").lowercase() !in IMAGE_EXT) continue
                val f = File(work, "%05d_%s".format(idx++, hn.substringAfterLast('/')))
                FileOutputStream(f).use { arc.extractFile(h, it) }
                out.add(hn to f)
            }
        }
        tmp.delete()
        if (out.isEmpty()) throw UnsupportedFormat("Nenhuma imagem no CBR")
        return out.sortedWith(compareBy(naturalOrder) { it.first }).map { Uri.fromFile(it.second) }
    }

    private fun fromPdf(ctx: Context, uri: Uri, work: File): List<Uri> {
        val tmp = copyToFile(ctx, uri, File(work, "_in.pdf"))
        val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
        val pages = mutableListOf<Uri>()
        PdfRenderer(pfd).use { renderer ->
            for (i in 0 until renderer.pageCount) {
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
