package com.climat.library.domain.action.template

import com.climat.library.domain.ref.ArgDefinition
import com.climat.library.domain.ref.Constant
import com.climat.library.domain.ref.FlagDefinition
import com.climat.library.domain.ref.RefWithAnyValue
import com.climat.library.domain.ref.RefWithValue
import com.climat.library.utils.emptyString
import com.climat.library.utils.shellSingleQuote
import org.lighthousegames.logging.logging

var log = logging("Interpolation")

internal class Interpolation(
    val name: String,
    val mapping: Mapping?,
    val isFlipped: Boolean
) : IPiece {
    override fun str(values: Collection<RefWithAnyValue>): String {
        val refWithValue = values.find { it.ref.name == name }
            ?: throw IllegalArgumentException("Could not find ref named `$name` inside the collection")

        return if (mapping != null) {
            log.d { "Mapping <${mapping.text}> to value <${refWithValue.value}>" }
            if (isDefined(refWithValue) xor isFlipped) {
                mapping.render(value(refWithValue))
            } else {
                emptyString()
            }
        } else {
            log.d { "No mapping found for $name, therefore interpolating bare value <${refWithValue.value}>" }
            value(refWithValue)
        }
    }

    /**
     * The ref's values as argv entries, for the microshell.
     *
     * Deliberately no [shellSingleQuote]: these never reach a shell lexer, so quoting here would end
     * up *inside* the argument. An undefined ref yields no entries at all, which drops the word it
     * sits in. An array yields one entry per element rather than a single space-joined blob.
     */
    fun rawValues(values: Collection<RefWithAnyValue>): List<String> {
        val refWithValue = refFor(values)

        return if (mapping != null) {
            if (isDefined(refWithValue) xor isFlipped) {
                listOf(mapping.render(rawList(refWithValue).joinToString(" ")))
            } else {
                emptyList()
            }
        } else {
            rawList(refWithValue)
        }
    }

    private fun refFor(values: Collection<RefWithAnyValue>): RefWithAnyValue =
        values.find { it.ref.name == name }
            ?: throw IllegalArgumentException("Could not find ref named `$name` inside the collection")

    private fun rawList(refWithValue: RefWithValue<*>): List<String> =
        when (val value = refWithValue.value) {
            is Array<*> -> value.map { it.toString() }
            else -> listOf(value.toString())
        }

    // A mapping is rendered only when its ref carries something: a flag (or boolean constant) that is
    // set, or an arg/constant/passthrough with a non-empty value. Anything else — an optional arg the
    // user left out, an unset flag — drops the whole mapping, joiner and all.
    private fun isDefined(refWithValue: RefWithValue<*>): Boolean {
        val ref = refWithValue.ref
        val isBoolean = ref is FlagDefinition || (ref is Constant && ref.isBoolean)
        return when {
            isBoolean -> refWithValue.value.toString().toBooleanStrict()
            else -> when (val value = refWithValue.value) {
                is Array<*> -> value.isNotEmpty()
                else -> value.toString().isNotEmpty()
            }
        }
    }

    // User-supplied values (string args and leftover varargs) are wrapped in single quotes so they
    // reach the shell as a single, literal argument (no word-splitting, globbing or injection).
    // Trusted DSL text (constants, flag values, mapping text) is emitted verbatim, so e.g. a constant
    // holding a shell fragment still works, and a mapping is not accidentally quoted.
    private fun value(refWithValue: RefWithValue<*>): String =
        when (val value = refWithValue.value) {
            is Array<*> -> value.joinToString(" ") { shellSingleQuote(it.toString()) }
            else -> value.toString().let { str ->
                if (refWithValue.ref is ArgDefinition && str.isNotEmpty()) shellSingleQuote(str) else str
            }
        }
}
