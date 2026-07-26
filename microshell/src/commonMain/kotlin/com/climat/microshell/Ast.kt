package com.climat.microshell

/**
 * A piece of an action body as handed to the microshell. Literal text comes from the DSL and is
 * lexed for shell metacharacters; a [Hole] is an opaque `@{ref}` that the caller resolves later.
 *
 * Because a hole is atomic, a resolved ref value is never re-lexed — it always lands as exactly one
 * argv entry, whatever it contains.
 */
sealed interface Segment

data class Literal(val text: String) : Segment

data class Hole(val id: Int) : Segment

/** A single argv entry, assembled from literal text and holes (`--name=@{x}` is one word). */
data class Word(val parts: List<Segment>)

data class Assignment(val name: String, val value: Word)

enum class Op { And, Or }

/**
 * Parsed microshell body. Precedence, loosest to tightest: `;`, then `&&`/`||` (equal and
 * left-associative, as in POSIX), then `|`.
 */
sealed interface Node

data class Seq(val items: List<Node>) : Node

data class AndOr(val left: Node, val op: Op, val right: Node) : Node

data class Pipeline(val stages: List<Node>) : Node

data class Group(val body: Node) : Node

data class Command(val assignments: List<Assignment>, val words: List<Word>) : Node

class MicroshellParseException(message: String) : Exception(message)
