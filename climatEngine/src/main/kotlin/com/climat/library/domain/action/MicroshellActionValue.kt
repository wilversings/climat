package com.climat.library.domain.action

import com.climat.library.domain.action.template.Template
import com.climat.microshell.Node
import com.climat.microshell.ResolvedNode
import org.antlr.v4.kotlinruntime.ast.Position
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * An `act microsh { ... }` body: parsed into a microshell AST at DSL-decode time, so `@{ref}` values
 * become argv entries rather than text a shell would re-lex.
 *
 * [value] is a human-readable rendering for the playground and logs; [plan] is what actually gets
 * executed.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class MicroshellActionValue internal constructor(
    internal val template: Template,
    internal val ast: Node,
    override val sourceMap: Position?,
) : ActionValueBase<String>() {

    /**
     * The resolved plan handed to the `climat-msh` binary.
     *
     * Public because the CLI module is a separate compilation unit, but not exported: [ResolvedNode]
     * is a plain Kotlin hierarchy with no JS representation.
     */
    @JsExport.Ignore
    var plan: ResolvedNode? = null
        internal set
}
