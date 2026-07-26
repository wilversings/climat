package com.climat.library.domain.action.template

import com.climat.library.domain.ref.RefWithAnyValue
import com.climat.microshell.Hole
import com.climat.microshell.Literal
import com.climat.microshell.Segment

internal class Template(
    internal val pieces: List<IPiece>,
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

    /**
     * The template as microshell input: literal text **verbatim** (the microshell does its own
     * whitespace handling, so [SimpleString.str]'s collapsing must not be applied here) and each
     * interpolation as an opaque hole indexing into [refReferences].
     */
    fun toSegments(): List<Segment> {
        var hole = 0
        return pieces.map { piece ->
            when (piece) {
                is SimpleString -> Literal(piece.value)
                is Interpolation -> Hole(hole++)
                else -> throw IllegalStateException("Unsupported template piece `${piece::class}`")
            }
        }
    }
}
