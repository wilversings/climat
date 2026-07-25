package com.climat.library.domain.action.template

import com.climat.library.domain.ref.RefWithAnyValue

internal class Template(
    private val pieces: List<IPiece>,
) {
    // Renders the template, collapsing whitespace at the seams between pieces (e.g. around an
    // interpolation that expanded to nothing) to a single space. Interpolated argument values are
    // single-quoted, so they start/end with `'` and are appended verbatim — their internal
    // whitespace is never collapsed.
    fun str(values: Collection<RefWithAnyValue>): String {
        val sb = StringBuilder()
        pieces.forEach { piece ->
            val rendered = piece.str(values)
            var start = 0
            while (sb.isNotEmpty() && sb.last() == ' ' && start < rendered.length && rendered[start] == ' ') {
                start++
            }
            sb.append(rendered, start, rendered.length)
        }
        return sb.toString()
    }

    val refReferences: List<Interpolation> = pieces.filterIsInstance<Interpolation>()
}
