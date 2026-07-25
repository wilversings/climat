package com.climat.library.domain.action.template

import com.climat.library.domain.ref.ArgDefinition
import com.climat.library.domain.ref.Constant
import com.climat.library.domain.ref.FlagDefinition
import com.climat.library.domain.ref.PredefinedParamDefinition
import com.climat.library.domain.ref.RefWithAnyValue
import com.climat.library.domain.ref.RefWithValue
import com.climat.library.utils.emptyString
import com.climat.library.utils.shellSingleQuote
import org.lighthousegames.logging.logging

var log = logging("Interpolation")

internal class Interpolation(
    val name: String,
    val mapping: String?,
    val isFlipped: Boolean
) : IPiece {
    override fun str(values: Collection<RefWithAnyValue>): String {
        val refWithValue = values.find { it.ref.name == name }
            ?: throw IllegalArgumentException("Could not find ref named `$name` inside the collection")

        return if (mapping != null) {
            log.d { "Mapping <$mapping> to value <${refWithValue.value}>" }
            mapValue(refWithValue, mapping)
        } else {
            log.d { "No mapping found for $name, therefore interpolating bare value <${refWithValue.value}>" }
            bareValue(refWithValue)
        }
    }

    // User-supplied values (string args and leftover varargs) are wrapped in single quotes so they
    // reach the shell as a single, literal argument (no word-splitting, globbing or injection).
    // Trusted DSL text (constants, flag values, flag tokens) is emitted verbatim, so e.g. a constant
    // holding a shell fragment still works, and a flag mapping is not accidentally quoted.
    private fun bareValue(refWithValue: RefWithValue<*>): String =
        when (val value = refWithValue.value) {
            is Array<*> -> value.joinToString(" ") { shellSingleQuote(it.toString()) }
            else -> value.toString().let { str ->
                if (refWithValue.ref is ArgDefinition && str.isNotEmpty()) shellSingleQuote(str) else str
            }
        }

    private fun mapValue(
        refWithValue: RefWithValue<*>,
        mapping: String
    ): String {
        val value = refWithValue.value.toString()
        return when (val ref = refWithValue.ref) {
            is FlagDefinition -> mapBoolean(value, mapping)
            is ArgDefinition -> mapString(value, mapping, quote = true)
            is Constant -> if (ref.isBoolean) {
                mapBoolean(value, mapping)
            } else {
                mapString(value, mapping, quote = false)
            }

            is PredefinedParamDefinition -> throw Exception("Cannot map vararg argument type") // TODO proper error
            else -> throw Exception("Type type not supported") // TODO proper error
        }
    }

    // The `mapping` key is author-controlled DSL text and is left as-is; only a user-supplied value
    // is single-quoted, e.g. `--today-is='New York'`.
    private fun mapString(value: String, mapping: String, quote: Boolean): String = if (value.isNotEmpty()) {
        "$mapping=${if (quote) shellSingleQuote(value) else value}"
    } else {
        emptyString()
    }

    private fun mapBoolean(value: String, mapping: String): String = if (value.toBooleanStrict() xor isFlipped) {
        mapping
    } else {
        emptyString()
    }
}
