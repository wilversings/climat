package com.climat.microshell

/**
 * A piece of an action body as handed to the microshell.
 *
 * [Literal] is text written in the DSL and is lexed for shell metacharacters. [Value] is an
 * already-resolved `@{ref}` value and is **never** lexed — it is atomic, so whatever it contains
 * (`;`, `|`, quotes) lands as ordinary characters in exactly one argv entry rather than as syntax.
 *
 * That split is the whole security model, and it is why the caller resolves refs before handing the
 * body over: by the time the microshell sees a value, there is no way for it to become a command.
 */
sealed interface Segment

data class Literal(val text: String) : Segment

data class Value(val text: String) : Segment

/** A single argv entry, assembled from literal text and values (`--name=@{x}` is one word). */
internal data class Word(val parts: List<Segment>) {

    /**
     * The argv entry this word contributes: one, or none at all.
     *
     * A word that is nothing but an empty value disappears, so an unset flag's mapping drops the
     * whole argument rather than leaving a stray empty entry. A value mixed with literal text
     * (`--name=@{x}`) always yields a word, even when the value is empty.
     *
     * A ref with several values arrives as separate values already split by literal whitespace, so
     * the tokenizer has made them separate words by the time this runs — nothing to expand here.
     */
    fun toArgv(): List<String> {
        val single = parts.singleOrNull()
        if (single is Value) return if (single.text.isEmpty()) emptyList() else listOf(single.text)
        return listOf(
            parts.joinToString("") { part ->
                when (part) {
                    is Literal -> part.text
                    is Value -> part.text
                }
            },
        )
    }
}

internal data class Assignment(val name: String, val value: Word)

internal enum class Op { And, Or }

/**
 * Parsed microshell body. Precedence, loosest to tightest: `;`, then `&&`/`||` (equal and
 * left-associative, as in POSIX), then `|`.
 */
internal sealed interface Node

internal data class Seq(val items: List<Node>) : Node

internal data class AndOr(val left: Node, val op: Op, val right: Node) : Node

internal data class Pipeline(val stages: List<Node>) : Node

internal data class Group(val body: Node) : Node

internal data class Command(val assignments: List<Assignment>, val words: List<Word>) : Node

internal class MicroshellParseException(message: String) : Exception(message)
