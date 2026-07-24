package com.bubbl.reader

import android.net.Uri

/** Passa páginas entre MainActivity e ReaderActivity sem estourar tamanho de Intent. */
object BookHolder {
    var pages: List<Uri> = emptyList()
    var title: String = ""
}
