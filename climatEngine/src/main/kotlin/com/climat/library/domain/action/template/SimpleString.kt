package com.climat.library.domain.action.template

import com.climat.library.domain.ref.RefWithAnyValue

private val whitespace = "\\s+".toRegex()

internal class SimpleString(val value: String) : IPiece {
    // Collapse runs of whitespace (indentation, newlines from a multi-line action body) to a
    // single space. This only ever runs on literal template text, never on interpolated values,
    // so single-quoted argument values keep their internal whitespace intact.
    override fun str(values: Collection<RefWithAnyValue>) = value.replace(whitespace, " ")
}
