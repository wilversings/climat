package com.climat.library.domain.action

import com.climat.library.domain.action.template.Template
import org.antlr.v4.kotlinruntime.ast.Position
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * An `act microsh { ... }` body.
 *
 * The engine only resolves refs; the shell language itself is parsed by the microshell at run time,
 * so nothing here knows what a pipe or an `&&` is. [segments] preserves which parts of the body are
 * author-written text and which are resolved values — the distinction that keeps a value from ever
 * being read as syntax.
 *
 * [value] is a flat rendering for the playground and logs.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class MicroshellActionValue internal constructor(
    internal val template: Template,
    override val sourceMap: Position?,
) : ActionValueBase<String>() {

    /**
     * Public because the CLI module is a separate compilation unit, but not exported: this is a
     * plain Kotlin hierarchy with no JS representation.
     */
    @JsExport.Ignore
    var segments: List<ActionSegment>? = null
        internal set
}
