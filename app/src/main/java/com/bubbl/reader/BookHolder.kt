package com.bubbl.reader

import android.net.Uri

/** Passa páginas entre MainActivity e ReaderActivity sem estourar tamanho de Intent. */
object BookHolder {
    var pages: List<Uri> = emptyList()
    var title: String = ""
    /** Uri de origem (string) das [pages]; confere se ainda são do livro do reader. */
    var source: String? = null
}
