package com.bubbl.reader

/**
 * Estado de leitura de um livro. Puro Kotlin (sem Android) p/ ser testável em JVM.
 * [key] é a string do Uri de origem (arquivo ou árvore de pasta); [page] é 0-based.
 */
data class BookEntry(
    val key: String,
    val folder: Boolean,
    val title: String,
    val page: Int,
    val total: Int,
    val rtl: Boolean,
    val lastOpened: Long
)

/** Regras da lista de recentes: mais recente primeiro, sem chave repetida, no máximo [MAX]. */
object Recents {

    const val MAX = 10

    /** Ordena por último acesso (desc), mantém só a entrada mais recente por chave e corta em [max]. */
    fun normalize(list: List<BookEntry>, max: Int = MAX): List<BookEntry> =
        list.sortedByDescending { it.lastOpened }
            .distinctBy { it.key }
            .take(max)

    /** Insere/substitui [entry]; em empate de timestamp, a nova vence a antiga. */
    fun upsert(list: List<BookEntry>, entry: BookEntry, max: Int = MAX): List<BookEntry> =
        normalize(listOf(entry) + list.filterNot { it.key == entry.key }, max)

    fun remove(list: List<BookEntry>, key: String): List<BookEntry> =
        list.filterNot { it.key == key }

    /**
     * Entrada para um livro recém-aberto: preserva página e direção de [existing]
     * (se houver), atualiza título/total/timestamp e garante a página dentro do livro.
     */
    fun opened(
        existing: BookEntry?,
        key: String,
        folder: Boolean,
        title: String,
        total: Int,
        now: Long
    ): BookEntry = BookEntry(
        key = key,
        folder = folder,
        title = title,
        page = clampPage(existing?.page ?: 0, total),
        total = total,
        rtl = existing?.rtl ?: false,
        lastOpened = now
    )

    /** Página válida (0-based) para um livro de [total] páginas. */
    fun clampPage(page: Int, total: Int): Int =
        if (total <= 0) 0 else page.coerceIn(0, total - 1)
}

/**
 * Mapeamento índice de página (0-based) ↔ valor do slider (1..total).
 * Em RTL o slider é invertido: a página 1 fica na ponta direita, então arrastar
 * para a direita volta páginas — coerente com o ViewPager2 em RTL.
 */
object PageSlider {

    fun valueFor(index: Int, total: Int, rtl: Boolean): Float {
        val i = Recents.clampPage(index, total)
        return (if (rtl) total - i else i + 1).toFloat()
    }

    fun indexFor(value: Float, total: Int, rtl: Boolean): Int {
        val v = Math.round(value)
        return Recents.clampPage(if (rtl) total - v else v - 1, total)
    }
}
