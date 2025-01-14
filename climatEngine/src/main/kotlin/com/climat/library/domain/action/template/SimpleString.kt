package com.climat.library.domain.action.template

import com.climat.library.domain.ref.RefWithAnyValue

internal class SimpleString(val value: String) : IPiece {
    override fun str(values: Collection<RefWithAnyValue>) = value
}
