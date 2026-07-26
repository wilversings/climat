package com.climat.microshell

private sealed interface Token

private data class WordTok(val word: Word, val startedQuoted: Boolean) : Token

private enum class Punct : Token { Pipe, AndAnd, OrOr, Semi, LParen, RParen }

/**
 * Length of a leading `NAME=` prefix (i.e. the index of the `=`), or `-1` if there isn't one.
 *
 * Hand-rolled rather than a `Regex` on purpose: this is the only pattern in the module, and linking
 * Kotlin's regex engine into the shipped native binary costs far more than the scan it replaces.
 */
private fun assignmentNameLength(text: String): Int {
    if (text.isEmpty()) return -1
    val first = text[0]
    if (!(first in 'a'..'z' || first in 'A'..'Z' || first == '_')) return -1
    var i = 1
    while (i < text.length) {
        when (val c = text[i]) {
            '=' -> return i
            else ->
                if (!(c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_')) return -1
        }
        i++
    }
    return -1
}

/**
 * Parses a microshell body.
 *
 * v1 supports `|`, `&&`, `||`, `;`, `( )` grouping, `VAR=value cmd` prefixes and literal `'...'`
 * quoting. There is no `$VAR` expansion, no double quotes, no escapes, no redirection and no
 * globbing — climat's own `@{ref}` interpolation is the way to get a value (including one with
 * spaces) into a single argv entry.
 */
internal fun parse(segments: List<Segment>): Node = Parser(tokenize(segments)).parseSeq(topLevel = true)

private fun tokenize(segments: List<Segment>): List<Token> {
    val tokens = mutableListOf<Token>()
    var parts = mutableListOf<Segment>()
    val text = StringBuilder()
    var hasWord = false
    var startedQuoted = false
    var inQuote = false

    fun flushText() {
        if (text.isNotEmpty()) {
            parts.add(Literal(text.toString()))
            text.clear()
        }
    }

    fun endWord() {
        flushText()
        if (hasWord) {
            tokens.add(WordTok(Word(parts), startedQuoted))
            parts = mutableListOf()
            hasWord = false
            startedQuoted = false
        }
    }

    fun beginWord(quoted: Boolean) {
        if (!hasWord) startedQuoted = quoted
        hasWord = true
    }

    for (segment in segments) {
        when (segment) {
            // Atomic: never lexed, so a resolved value cannot introduce syntax.
            is Value -> {
                flushText()
                beginWord(false)
                parts.add(segment)
            }

            is Literal -> {
                var i = 0
                val s = segment.text
                while (i < s.length) {
                    val c = s[i]
                    if (inQuote) {
                        if (c == '\'') inQuote = false else text.append(c)
                        i++
                        continue
                    }
                    when {
                        c == '\'' -> { beginWord(quoted = text.isEmpty() && parts.isEmpty()); inQuote = true; i++ }
                        c == ' ' || c == '\t' || c == '\n' || c == '\r' -> { endWord(); i++ }
                        c == '|' && i + 1 < s.length && s[i + 1] == '|' -> { endWord(); tokens.add(Punct.OrOr); i += 2 }
                        c == '|' -> { endWord(); tokens.add(Punct.Pipe); i++ }
                        c == '&' && i + 1 < s.length && s[i + 1] == '&' -> { endWord(); tokens.add(Punct.AndAnd); i += 2 }
                        c == '&' -> throw MicroshellParseException("Background `&` is not supported")
                        c == ';' -> { endWord(); tokens.add(Punct.Semi); i++ }
                        c == '(' -> { endWord(); tokens.add(Punct.LParen); i++ }
                        c == ')' -> { endWord(); tokens.add(Punct.RParen); i++ }
                        c == '>' || c == '<' -> throw MicroshellParseException("Redirection `$c` is not supported")
                        c == '$' -> throw MicroshellParseException("`\$` expansion is not supported — use `@{ref}`")
                        c == '"' -> throw MicroshellParseException("Double quotes are not supported — use '...'")
                        c == '`' -> throw MicroshellParseException("Command substitution is not supported")
                        else -> { beginWord(false); text.append(c); i++ }
                    }
                }
            }
        }
    }
    if (inQuote) throw MicroshellParseException("Unterminated `'`")
    endWord()
    return tokens
}

private class Parser(private val tokens: List<Token>) {
    private var pos = 0

    private fun peek(): Token? = tokens.getOrNull(pos)

    private fun accept(p: Punct): Boolean = (peek() == p).also { if (it) pos++ }

    fun parseSeq(topLevel: Boolean): Node {
        val items = mutableListOf(parseAndOr())
        while (accept(Punct.Semi)) {
            // a trailing `;` is fine, as in POSIX
            if (peek() == null || peek() == Punct.RParen) break
            items.add(parseAndOr())
        }
        if (topLevel && pos != tokens.size) {
            throw MicroshellParseException("Unexpected `)`")
        }
        return if (items.size == 1) items.single() else Seq(items)
    }

    private fun parseAndOr(): Node {
        var left = parsePipeline()
        while (true) {
            val op = when {
                accept(Punct.AndAnd) -> Op.And
                accept(Punct.OrOr) -> Op.Or
                else -> return left
            }
            left = AndOr(left, op, parsePipeline())
        }
    }

    private fun parsePipeline(): Node {
        val stages = mutableListOf(parseStage())
        while (accept(Punct.Pipe)) stages.add(parseStage())
        return if (stages.size == 1) stages.single() else Pipeline(stages)
    }

    private fun parseStage(): Node {
        if (accept(Punct.LParen)) {
            val body = parseSeq(topLevel = false)
            if (!accept(Punct.RParen)) throw MicroshellParseException("Unbalanced `(`")
            return Group(body)
        }
        return parseCommand()
    }

    private fun parseCommand(): Node {
        val assignments = mutableListOf<Assignment>()
        val words = mutableListOf<Word>()

        while (true) {
            val tok = peek()
            if (tok !is WordTok) break
            pos++
            val split = if (words.isEmpty()) splitAssignment(tok) else null
            if (split != null) assignments.add(split) else words.add(tok.word)
        }

        if (words.isEmpty()) {
            if (assignments.isNotEmpty()) {
                // bash would set a shell variable; the microshell has no shell state to set.
                throw MicroshellParseException(
                    "`${assignments.first().name}=...` must be followed by a command",
                )
            }
            throw MicroshellParseException(
                when (val t = peek()) {
                    null -> "Unexpected end of action — expected a command"
                    Punct.RParen -> "Unexpected `)`"
                    else -> "Unexpected `${render(t)}` — expected a command"
                },
            )
        }
        return Command(assignments, words)
    }

    /** `VAR=value` only counts as an assignment unquoted and before any word (`'VAR=x' cmd` is a word). */
    private fun splitAssignment(tok: WordTok): Assignment? {
        if (tok.startedQuoted) return null
        val first = tok.word.parts.firstOrNull() as? Literal ?: return null
        val nameLength = assignmentNameLength(first.text)
        if (nameLength < 0) return null
        val name = first.text.substring(0, nameLength)
        val rest = first.text.substring(nameLength + 1)
        val tail = tok.word.parts.drop(1)
        val parts = if (rest.isEmpty()) tail else listOf(Literal(rest)) + tail
        return Assignment(name, Word(parts))
    }

    private fun render(t: Token): String = when (t) {
        Punct.Pipe -> "|"
        Punct.AndAnd -> "&&"
        Punct.OrOr -> "||"
        Punct.Semi -> ";"
        Punct.LParen -> "("
        Punct.RParen -> ")"
        is WordTok -> "word"
    }
}
