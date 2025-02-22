package com.climat.library.commandParser

import com.climat.library.commandParser.exception.ParameterException
import com.climat.library.commandParser.exception.ToolchainNotDefinedException
import com.climat.library.domain.action.ActionValueBase
import com.climat.library.domain.action.JavaScriptActionValue
import com.climat.library.domain.action.NoopActionValue
import com.climat.library.domain.action.ScopeParamsActionValue
import com.climat.library.domain.action.TemplateActionValue
import com.climat.library.domain.isLeaf
import com.climat.library.domain.ref.RefWithAnyValue
import com.climat.library.domain.toolchain.Toolchain
import com.climat.library.utils.newLine
import org.lighthousegames.logging.logging

private val log = logging("ToolchainProcessor")

internal fun processToolchain(
    toolchain: Toolchain,
    passedParams: MutableList<String>,
    handler: (parsedAction: ActionValueBase<*>, context: Toolchain) -> Unit
) = processToolchains(
    children = listOf(toolchain),
    passedParams = passedParams,
    upperScopeRefs = emptyMap(),
    upperTraceToRoot = emptyList(),
    handler = handler,
    allowUnmatched = toolchain.allowUnmatched
)

private fun processToolchains(
    children: List<Toolchain>,
    passedParams: MutableList<String>,
    upperScopeRefs: Map<String, RefWithAnyValue>,
    upperTraceToRoot: List<Toolchain>,
    handler: (parsedAction: ActionValueBase<*>, context: Toolchain) -> Unit,
    allowUnmatched: Boolean,
) {
    require(passedParams.isNotEmpty()) {
        "`params` must have at least one element"
    }
    if (children.isEmpty() && allowUnmatched) {
        return
    }

    val next = passedParams.removeFirst()
    val toolchain = children.find {
        it.name != "_" && (it.name == next || it.aliases.any { it.name == next })
    } ?: children.find { it.name == "_" }
    ?: throw Exception("Toolchain $next is not defined" + newLine() + getSubcommandUsageHint(upperTraceToRoot))

    processToolchain(
        context = RefProcessingContext(
            toolchain = toolchain,
            passedParams = passedParams
        ),
        upperScopeRefs = upperScopeRefs,
        upperTraceToRoot = upperTraceToRoot,
        handler = handler,
    )
}

private fun processToolchain(
    context: RefProcessingContext,
    upperScopeRefs: Map<String, RefWithAnyValue>,
    upperTraceToRoot: List<Toolchain>,
    handler: (parsedAction: ActionValueBase<*>, context: Toolchain) -> Unit
) {
    val (toolchain, passedParams) = context
    log.d { "Processing toolchain: <${toolchain.name}>" }

    val traceToRoot = upperTraceToRoot + toolchain
    val scopeRefs = upperScopeRefs + processRefs(context, traceToRoot)
    if (passedParams.isEmpty()) {
        handleMatch(toolchain, scopeRefs, handler)
        log.d { "Execution summary\nPath: ${traceToRoot.joinToString(" -> ") { it.name }}" }
    } else if (toolchain.isLeaf) {
        throw Exception("Could not match $passedParams with any definition") // TODO: proper error
    } else {
        processToolchains(
            children = toolchain.children.toList(),
            passedParams = passedParams,
            upperScopeRefs = scopeRefs,
            upperTraceToRoot = traceToRoot,
            handler = handler,
            allowUnmatched = toolchain.allowUnmatched
        )
    }
}

private fun handleMatch(
    toolchain: Toolchain,
    scopeRefs: Map<String, RefWithAnyValue>,
    handler: (parsedAction: ActionValueBase<*>, context: Toolchain) -> Unit
) {
    log.d { "Matched <${toolchain.name}>" }

    val act = toolchain.action

    if (act.type == ActionValueBase.Type.Noop) return

    setActualCommand(act, scopeRefs.values)
    log.d { "Handling action.." }
    handler(act, toolchain)
}

private fun processRefs(
    context: RefProcessingContext,
    traceToRoot: List<Toolchain>
): Map<String, RefWithAnyValue> = try {
    processRefs(context)
} catch (ex: ParameterException) {
    throw Exception(ex.message + newLine() + getParameterUsageHint(traceToRoot), ex)
}

private fun setActualCommand(
    action: ActionValueBase<*>,
    values: Collection<RefWithAnyValue>
) {
    when (action) {
        is TemplateActionValue -> action.value = action.template.str(values)
        is JavaScriptActionValue -> action.value = values.associate { it.ref.name to it.value }
        is ScopeParamsActionValue -> action.value = values.associate { it.ref.name to it.value }
        is NoopActionValue -> { /* By definition, do nothing */ }
        else -> throw Exception("Type `${action::class}` not supported")
    }
}
