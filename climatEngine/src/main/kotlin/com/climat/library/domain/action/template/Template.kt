package com.climat.library.domain.action.template

import com.climat.library.domain.action.ActionSegment
import com.climat.library.domain.action.LiteralText
import com.climat.library.domain.action.ResolvedValue
import com.climat.library.domain.ref.RefWithAnyValue

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
     * The template with refs resolved, keeping the text/value boundary intact.
     *
     * Literal text is emitted **verbatim** — [SimpleString.str]'s whitespace collapsing must not be
     * applied, because whoever consumes this does its own tokenising. Values are emitted without
     * [com.climat.library.utils.shellSingleQuote]: they are handed over as discrete segments, so
     * quoting them would put quote characters *inside* the value.
     *
     * A ref carrying several values (a vararg or passthrough) emits one [ResolvedValue] each,
     * separated by a literal space so they stay distinct rather than merging into one.
     */
    fun resolveToSegments(values: Collection<RefWithAnyValue>): List<ActionSegment> =
        pieces.flatMap { piece ->
            when (piece) {
                is SimpleString -> listOf(LiteralText(piece.value))
                is Interpolation ->
                    piece.rawValues(values)
                        .map<String, ActionSegment> { ResolvedValue(it) }
                        .ifEmpty { listOf(ResolvedValue("")) }
                        .flatMapIndexed { index, segment ->
                            if (index == 0) listOf(segment) else listOf(LiteralText(" "), segment)
                        }
                else -> throw IllegalStateException("Unsupported template piece `${piece::class}`")
            }
        }
}
