package com.climat.library.commandParser

import com.climat.library.domain.ref.ArgDefinition
import com.climat.library.domain.ref.FlagDefinition
import com.climat.library.domain.ref.ParamDefinition
import com.climat.library.domain.toolchain.Toolchain
import com.climat.library.utils.joinToStringIfNotEmpty
import com.climat.library.utils.newLine
import com.climat.library.utils.newLines
import com.climat.library.utils.tpl

internal fun getParameterUsageHint(traceToRoot: List<Toolchain>): String =
    getRequiredPlaceholders(traceToRoot) +
    2.newLines() +
    getParameterDescriptions(traceToRoot.last().parameters) +
    newLine()

internal fun getSubcommandUsageHint(traceToRoot: List<Toolchain>): String =
    traceToRoot.last().children.let { children ->
        getRequiredPlaceholders(traceToRoot) +
            children.joinToStringIfNotEmpty(separator = "|", prefix = " (", postfix = ")") { it.name } +
            children.joinToStringIfNotEmpty(newLine(), prefix = "${2.newLines()}Subcommands:${newLine()}") {
                "${it.name} - ${it.description}"
            } +
            newLine()
    }

private fun getRequiredPlaceholders(traceToRoot: List<Toolchain>) =
    "usage: ${traceToRoot.joinToString(" ") { getRequiredPlaceholder(it) }}"

private fun getRequiredPlaceholder(toolchain: Toolchain) =
    toolchain.parameters.partition { it.optional }.let { (optionals, required) ->
        toolchain.name +
            required.joinToStringIfNotEmpty(" ", prefix = " ") { "<${it.name}>" } +
            optionals.joinToStringIfNotEmpty(" ", prefix = " ") {
                when (it) {
                    is FlagDefinition -> "[--${it.name}]"
                    is ArgDefinition -> "[--${it.name}=<value>]"
                    else -> throw Exception("Type not supported")
                }
            }
    }

fun getParameterDescriptions(params: Array<ParamDefinition>) =
    params.partition { it.optional }.let { (optionals, required) ->
        optionals.joinToStringIfNotEmpty(
            separator = newLine(),
            prefix = "Optional: ${newLine()}",
            postfix = 2.newLines()
        ) {
            paramDefWithDescription(it)
        } +
            required.joinToStringIfNotEmpty(
                separator = newLine(),
                prefix = "Required: ${newLine()}"
            ) {
                paramDefWithDescription(it)
            }
    }

private fun paramDefWithDescription(it: ParamDefinition): String =
    ARG_PREFIX +
        it.name +
        it.shorthand.tpl { " or ${SHORTHAND_ARG_PREFIX}$it" } +
        " \t\t ${it.description} "
