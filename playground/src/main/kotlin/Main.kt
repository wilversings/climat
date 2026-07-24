package com.climat.playground

import com.climat.library.commandParser.execute
import com.climat.library.commandParser.getValidations
import com.climat.library.commandParser.parse
import com.climat.library.domain.action.ActionValueBase
import com.climat.library.domain.action.JavaScriptActionValue
import com.climat.library.domain.action.NoopActionValue
import com.climat.library.domain.action.ScopeParamsActionValue
import com.climat.library.domain.action.TemplateActionValue
import com.climat.library.validation.ValidationResult
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent

/**
 * A CodeMirror 6 editor + a fake shell around the Climat engine.
 *
 * The engine (`:climatEngine`) is pure Kotlin/JS with no Node dependency, so we call `parse`,
 * `getValidations` and `execute` directly. Instead of running the resolved shell command (which is
 * impossible in a browser) we simply print it. Custom `javascript action`s are actually run, but in
 * a sandbox where `require(...)` is unavailable — so `child_process`/`fs` calls fail gracefully.
 */

private val DEFAULT_DSL = """
sgit {
    sub acp(amend a: flag) {
        action <%
            git add . &&
            git commit ${'$'}(amend:--amend) &&
            git push ${'$'}(amend:--force)
        %>
    }
    sub cf(branch: arg, force f: flag) {
        action <%
            git checkout feature/${'$'}(branch) ${'$'}(force:--force)
        %>
    }
}
""".trimIndent()

// --- CodeMirror + custom-script runner, wired through require() so webpack bundles them ---

private val makeEditor: dynamic = js(
    """
    (function (parent, doc, onChange) {
        var view = require('@codemirror/view');
        var cm = require('codemirror');
        var EditorView = view.EditorView;
        var listener = EditorView.updateListener.of(function (u) {
            if (u.docChanged) onChange(u.state.doc.toString());
        });
        return new EditorView({
            doc: doc,
            extensions: [cm.basicSetup, listener],
            parent: parent
        });
    })
    """
)

private val setDoc: dynamic = js(
    """
    (function (view, text) {
        view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: text } });
    })
    """
)

private val runScript: dynamic = js(
    """
    (function (script, params, emit) {
        var fakeConsole = {
            log:   function () { emit(Array.prototype.slice.call(arguments).join(' ')); },
            info:  function () { emit(Array.prototype.slice.call(arguments).join(' ')); },
            warn:  function () { emit(Array.prototype.slice.call(arguments).join(' ')); },
            error: function () { emit(Array.prototype.slice.call(arguments).join(' ')); }
        };
        var require = function (name) {
            throw new Error("require('" + name + "') is not available in the browser playground");
        };
        var fn = new Function('params', 'console', 'require', script);
        return fn(params, fakeConsole, require);
    })
    """
)

private var editorView: dynamic = null
private var rootName: String? = null
private var rootAliases: List<String> = emptyList()
private var debounceHandle: Int = 0

private val history = mutableListOf<String>()
private var historyIdx = 0

fun main() {
    setupEditor()
    setupShell()
    analyze(DEFAULT_DSL)
    printLine("Welcome to the climat playground.", "note")
    printLine("Edit the definition on the left, then type a command below.", "note")
    printLine("Try:  acp --amend   |   cf myFeature --force", "note")
}

// --- Editor & diagnostics ---

private fun setupEditor() {
    val parent = document.getElementById("editor")
    editorView = makeEditor(parent, DEFAULT_DSL, { src: String -> onDocChanged(src) })
    // Small embedding hook: replace the editor content programmatically (also used by tests).
    window.asDynamic().__climatSetSource = { text: String -> setDoc(editorView, text) }
}

private fun currentSource(): String =
    editorView?.state?.doc?.toString() as? String ?: DEFAULT_DSL

private fun onDocChanged(source: String) {
    window.clearTimeout(debounceHandle)
    debounceHandle = window.setTimeout({ analyze(source) }, 300)
}

private fun analyze(source: String) {
    val diag = document.getElementById("diagnostics") as HTMLElement
    diag.innerHTML = ""

    val root =
        try {
            parse(source)
        } catch (e: Throwable) {
            rootName = null
            rootAliases = emptyList()
            updatePrompt()
            appendDiag(diag, "error", "parse-error", e.message ?: e.toString())
            return
        }

    rootName = root.name
    rootAliases = root.aliases.map { it.name }
    updatePrompt()

    val validations = getValidations(root)
    if (validations.isEmpty()) {
        val empty = document.createElement("div") as HTMLElement
        empty.className = "diag-empty"
        empty.textContent = "No problems."
        diag.appendChild(empty)
        return
    }
    validations.forEach { v ->
        val kind = if (v.type == ValidationResult.ValidationEntryType.Error) "error" else "warning"
        appendDiag(diag, kind, v.code.toString(), v.message)
    }
}

private fun appendDiag(container: HTMLElement, kind: String, code: String, message: String) {
    val el = document.createElement("div") as HTMLElement
    el.className = "diag $kind"

    val codeEl = document.createElement("div") as HTMLElement
    codeEl.className = "code"
    codeEl.textContent = code
    el.appendChild(codeEl)

    val msgEl = document.createElement("div") as HTMLElement
    msgEl.textContent = message
    el.appendChild(msgEl)

    container.appendChild(el)
}

// --- Shell ---

private fun setupShell() {
    val input = document.getElementById("shell-input") as HTMLInputElement
    input.addEventListener("keydown", { event ->
        val e = event as KeyboardEvent
        when (e.key) {
            "Enter" -> {
                val line = input.value
                input.value = ""
                if (line.isNotBlank()) {
                    history.add(line)
                    historyIdx = history.size
                }
                runCommand(line)
            }
            "ArrowUp" -> {
                if (historyIdx > 0) {
                    historyIdx--
                    input.value = history[historyIdx]
                    e.preventDefault()
                }
            }
            "ArrowDown" -> {
                if (historyIdx < history.size - 1) {
                    historyIdx++
                    input.value = history[historyIdx]
                } else {
                    historyIdx = history.size
                    input.value = ""
                }
                e.preventDefault()
            }
        }
    })
    input.focus()
}

private fun updatePrompt() {
    val prompt = document.getElementById("shell-prompt") as HTMLElement
    prompt.textContent = (rootName ?: "climat")
}

private fun runCommand(line: String) {
    echoPrompt(line)
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return

    var tokens = tokenize(trimmed)
    // Be forgiving: allow the user to type the toolchain name (or an alias) as the first token,
    // even though `execute` prepends it itself.
    if (tokens.isNotEmpty() && (tokens[0] == rootName || tokens[0] in rootAliases)) {
        tokens = tokens.drop(1)
    }

    try {
        execute(
            tokens.toTypedArray(),
            currentSource(),
            { action, _ -> handleAction(action) },
            true, // skipValidation — diagnostics are already shown live in the editor
        )
    } catch (e: Throwable) {
        printLine(e.message ?: e.toString(), "err")
    }
}

private fun handleAction(action: ActionValueBase<*>) {
    when (action) {
        is TemplateActionValue -> printLine((action.value ?: "").trim(), "cmd")
        is JavaScriptActionValue -> runCustomScript(action)
        is ScopeParamsActionValue -> printLine("[scope-params action — nothing to print]", "note")
        is NoopActionValue -> { /* nothing to do */ }
        else -> printLine("[${action.type} action is not supported in the playground]", "note")
    }
}

private fun runCustomScript(action: JavaScriptActionValue) {
    try {
        runScript(action.customScript, action.valueForJs, { s: String -> printLine(s, "out") })
    } catch (e: Throwable) {
        printLine(e.message ?: e.toString(), "err")
    }
}

/** Minimal quote-aware tokenizer (handles spaces and single/double quotes). */
private fun tokenize(line: String): List<String> {
    val tokens = mutableListOf<String>()
    val sb = StringBuilder()
    var inSingle = false
    var inDouble = false
    var started = false
    for (c in line) {
        when {
            inSingle -> if (c == '\'') inSingle = false else sb.append(c)
            inDouble -> if (c == '"') inDouble = false else sb.append(c)
            c == '\'' -> { inSingle = true; started = true }
            c == '"' -> { inDouble = true; started = true }
            c.isWhitespace() -> if (started) {
                tokens.add(sb.toString())
                sb.clear()
                started = false
            }
            else -> { sb.append(c); started = true }
        }
    }
    if (started) tokens.add(sb.toString())
    return tokens
}

// --- Shell output helpers ---

private fun echoPrompt(line: String) {
    val out = document.getElementById("shell-output") as HTMLElement
    val el = document.createElement("div") as HTMLElement
    el.className = "line prompt-echo"

    val p = document.createElement("span") as HTMLElement
    p.className = "p"
    p.textContent = (rootName ?: "$") + " "
    el.appendChild(p)
    el.appendChild(document.createTextNode(line))

    out.appendChild(el)
    scrollShell()
}

private fun printLine(text: String, cls: String) {
    val out = document.getElementById("shell-output") as HTMLElement
    text.split("\n").forEach { ln ->
        val el = document.createElement("div") as HTMLElement
        el.className = "line $cls"
        el.textContent = if (ln.isEmpty()) " " else ln
        out.appendChild(el)
    }
    scrollShell()
}

private fun scrollShell() {
    val out = document.getElementById("shell-output") as HTMLElement
    out.scrollTop = out.scrollHeight.toDouble()
}
