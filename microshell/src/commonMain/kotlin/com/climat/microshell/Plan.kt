package com.climat.microshell

/**
 * A [Node] with every [Word] collapsed to a final argv entry. This is what crosses the process
 * boundary into the native shell.
 */
sealed interface ResolvedNode

data class RSeq(val items: List<ResolvedNode>) : ResolvedNode

data class RAndOr(val left: ResolvedNode, val op: Op, val right: ResolvedNode) : ResolvedNode

data class RPipeline(val stages: List<ResolvedNode>) : ResolvedNode

data class RCommand(val assignments: List<Pair<String, String>>, val argv: List<String>) : ResolvedNode

/**
 * Resolves every [Hole] through [value], which yields the ref's values.
 *
 * A word that is nothing but a single hole expands to **one argv entry per value** — so a vararg or
 * passthrough ref becomes several arguments, and a ref with no value (an unset flag's mapping)
 * drops the argument entirely instead of leaving a stray empty entry. A hole embedded in literal
 * text (`--name=@{x}`) always produces exactly one word.
 */
fun Node.resolve(value: (Hole) -> List<String>): ResolvedNode = when (this) {
    is Seq -> RSeq(items.map { it.resolve(value) })
    is AndOr -> RAndOr(left.resolve(value), op, right.resolve(value))
    is Pipeline -> RPipeline(stages.map { it.resolve(value) })
    // Parentheses only shape parse-time precedence; there is no scope to preserve in v1.
    is Group -> body.resolve(value)
    is Command -> {
        val argv = words.flatMap { it.resolveWord(value) }
        if (argv.isEmpty()) {
            throw MicroshellParseException("Command resolved to nothing — an interpolated value is empty")
        }
        RCommand(assignments.map { it.name to it.value.resolveWord(value).joinToString(" ") }, argv)
    }
}

private fun Word.resolveWord(value: (Hole) -> List<String>): List<String> {
    val single = parts.singleOrNull()
    if (single is Hole) return value(single).filter { it.isNotEmpty() }
    return listOf(
        parts.joinToString("") { part ->
            if (part is Hole) value(part).joinToString(" ") else (part as Literal).text
        },
    )
}

/**
 * Encodes the plan as a token list passed straight through `argv`.
 *
 * argv entries are already delimited by the kernel, so there is nothing to quote or escape — which
 * is the point: no encoding layer means no encoding bug can reintroduce shell injection.
 */
fun ResolvedNode.encode(): List<String> = buildList { encodeInto(this) }

private fun ResolvedNode.encodeInto(out: MutableList<String>) {
    when (this) {
        is RSeq -> {
            out.add("seq")
            out.add(items.size.toString())
            items.forEach { it.encodeInto(out) }
        }
        is RAndOr -> {
            out.add(if (op == Op.And) "and" else "or")
            left.encodeInto(out)
            right.encodeInto(out)
        }
        is RPipeline -> {
            out.add("pipe")
            out.add(stages.size.toString())
            stages.forEach { it.encodeInto(out) }
        }
        is RCommand -> {
            out.add("cmd")
            out.add(assignments.size.toString())
            assignments.forEach { (k, v) -> out.add(k); out.add(v) }
            out.add(argv.size.toString())
            out.addAll(argv)
        }
    }
}

/**
 * A human-readable rendering of the plan, for logs, the playground and test assertions.
 *
 * Display only — nothing parses this back. Quoting is applied purely so the argv boundaries stay
 * visible to a reader.
 */
fun ResolvedNode.render(): String = when (this) {
    is RSeq -> items.joinToString(" ; ") { it.render() }
    is RAndOr -> "${left.render()} ${if (op == Op.And) "&&" else "||"} ${right.render()}"
    is RPipeline -> stages.joinToString(" | ") { it.render() }
    is RCommand ->
        (assignments.map { (k, v) -> "$k=${quoteForDisplay(v)}" } + argv.map(::quoteForDisplay))
            .joinToString(" ")
}

private fun quoteForDisplay(value: String): String =
    if (value.isNotEmpty() && value.none { it in " \t\n'\"|&;()<>$`" }) {
        value
    } else {
        "'" + value.replace("'", "'\\''") + "'"
    }

fun decodePlan(tokens: List<String>): ResolvedNode {
    val reader = TokenReader(tokens)
    val node = reader.node()
    if (!reader.exhausted) throw MicroshellParseException("Trailing tokens in plan")
    return node
}

private class TokenReader(private val tokens: List<String>) {
    private var pos = 0

    val exhausted: Boolean get() = pos == tokens.size

    private fun next(): String =
        tokens.getOrNull(pos++) ?: throw MicroshellParseException("Truncated plan")

    private fun count(): Int =
        next().toIntOrNull() ?: throw MicroshellParseException("Malformed plan: expected a count")

    fun node(): ResolvedNode = when (val tag = next()) {
        "seq" -> RSeq((0 until count()).map { node() })
        "pipe" -> RPipeline((0 until count()).map { node() })
        "and" -> RAndOr(node(), Op.And, node())
        "or" -> RAndOr(node(), Op.Or, node())
        "cmd" -> {
            val assignments = (0 until count()).map { next() to next() }
            RCommand(assignments, (0 until count()).map { next() })
        }
        else -> throw MicroshellParseException("Malformed plan: unknown node `$tag`")
    }
}
