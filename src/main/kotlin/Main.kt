package com.climat

import com.climat.library.commandParser.execute
import com.climat.library.commandParser.getValidations
import com.climat.library.commandParser.parse
import com.climat.library.domain.action.JavaScriptActionValue
import com.climat.library.validation.ValidationResult.ValidationEntryType.Error
import com.climat.library.validation.ValidationResult.ValidationEntryType.Warning
import com.climat.output.ColoredLogger
import com.climat.output.prettify
import com.climat.output.warn
import org.lighthousegames.logging.KmLogging
import org.lighthousegames.logging.VariableLogLevel
import os.EOL
import process

// Used dynamically by `eval`
val climat = AsyncClimatCli()

fun main() =
    prettify {
        KmLogging.clear()
        KmLogging.addLogger(ColoredLogger(VariableLogLevel(getLogLevel())))

        val toolchain = parse(MANIFEST_TEXT)
        val validations = getValidations(toolchain)
        val warnings =
            validations
                .filter { it.type == Warning }
                .joinToString(EOL) { "${it.code}: ${it.message}" }

        if (warnings.isNotEmpty()) {
            console.warn(warn("Warnings:"))
            console.warn(warn(warnings))
        }

        val errors =
            validations
                .filter { it.type == Error }
                .joinToString(EOL) { "${it.code}: ${it.message}" }

        if (errors.isNotEmpty()) {
            throw Exception(errors)
        }

        execute(
            process.argv.drop(2).toTypedArray(),
            toolchain,
            prettify { command, _ ->
                if (command is JavaScriptActionValue) {
                    // Params used inside `eval`
                    @Suppress("UNUSED_VARIABLE")
                    val params = command.valueForJs
                    eval(command.customScript)
                } else {
                    throw Exception("`${command.type}` command type not supported")
                }
            },
            true,
        )
    }.invoke()
