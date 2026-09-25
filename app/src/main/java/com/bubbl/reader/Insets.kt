package com.bubbl.reader

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Edge-to-edge é obrigatório a partir do targetSdk 35: soma os insets (barras do
 * sistema + recorte da câmera) ao padding original da view, sem acumular.
 */
fun View.padForInsets(
    types: Int = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
    top: Boolean = true,
    bottom: Boolean = true
) {
    val l = paddingLeft; val t = paddingTop; val r = paddingRight; val b = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val i = insets.getInsets(types)
        v.setPadding(
            l + i.left,
            t + if (top) i.top else 0,
            r + i.right,
            b + if (bottom) i.bottom else 0
        )
        insets
    }
}
