package com.climat.library.domain.action.template

import com.climat.library.utils.emptyString

/**
 * The `"..."` text of a conditional interpolation (`@{x ? "--today-is={}"}`), rendered only when the
 * ref it hangs off is defined. Each piece is either literal DSL text or, when `null`, the `{}`
 * placeholder standing for the ref's value.
 */
internal class Mapping(private val pieces: List<String?>) {

    fun render(value: String): String = pieces.joinToString(emptyString()) { it ?: value }

    /** The mapping as written in the DSL — used in validation messages and to compare mappings. */
    val text: String = pieces.joinToString(emptyString()) { it ?: "{}" }
}
