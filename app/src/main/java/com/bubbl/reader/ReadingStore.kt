package com.bubbl.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistência do estado de leitura (página, direção, recentes) em SharedPreferences,
 * como um JSON pequeno (no máximo [Recents.MAX] livros). Regras de lista em [Recents].
 */
object ReadingStore {

    private const val PREFS = "reading"
    private const val KEY_BOOKS = "books"

    fun all(ctx: Context): List<BookEntry> {
        val raw = prefs(ctx).getString(KEY_BOOKS, null) ?: return emptyList()
        // JSON corrompido não pode derrubar a Home: começa do zero
        val list = runCatching { decode(raw) }.getOrDefault(emptyList())
        return Recents.normalize(list)
    }

    fun get(ctx: Context, key: String): BookEntry? = all(ctx).firstOrNull { it.key == key }

    /** Registra a abertura de um livro; preserva página/direção salvas. */
    fun opened(ctx: Context, key: String, folder: Boolean, title: String, total: Int): BookEntry {
        val list = all(ctx)
        val entry = Recents.opened(
            list.firstOrNull { it.key == key }, key, folder, title, total, System.currentTimeMillis()
        )
        upsert(ctx, entry)
        return entry
    }

    /** Insere/substitui; libera a permissão de quem saiu da lista por excesso. */
    fun upsert(ctx: Context, entry: BookEntry) {
        val old = all(ctx)
        val new = Recents.upsert(old, entry)
        save(ctx, new)
        old.filter { o -> new.none { it.key == o.key } }.forEach { releasePermission(ctx, it.key) }
    }

    fun savePage(ctx: Context, key: String, page: Int) {
        val e = get(ctx, key) ?: return
        upsert(ctx, e.copy(page = Recents.clampPage(page, e.total), lastOpened = System.currentTimeMillis()))
    }

    fun setRtl(ctx: Context, key: String, rtl: Boolean) {
        val e = get(ctx, key) ?: return
        upsert(ctx, e.copy(rtl = rtl))
    }

    fun remove(ctx: Context, key: String) = save(ctx, Recents.remove(all(ctx), key))

    /** Uris de "Abrir com" podem não ser persistíveis: falha silenciosa. */
    fun takePermission(ctx: Context, uri: Uri) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun releasePermission(ctx: Context, key: String) {
        runCatching {
            ctx.contentResolver.releasePersistableUriPermission(
                Uri.parse(key), Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    // ---- serialização ----

    private fun save(ctx: Context, list: List<BookEntry>) {
        prefs(ctx).edit { putString(KEY_BOOKS, encode(list)) }
    }

    private fun encode(list: List<BookEntry>): String {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("key", e.key)
                put("folder", e.folder)
                put("title", e.title)
                put("page", e.page)
                put("total", e.total)
                put("rtl", e.rtl)
                put("last", e.lastOpened)
            })
        }
        return arr.toString()
    }

    private fun decode(raw: String): List<BookEntry> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val key = o.optString("key")
            if (key.isEmpty()) return@mapNotNull null
            BookEntry(
                key = key,
                folder = o.optBoolean("folder"),
                title = o.optString("title"),
                page = o.optInt("page"),
                total = o.optInt("total"),
                rtl = o.optBoolean("rtl"),
                lastOpened = o.optLong("last")
            )
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
