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
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.url.URLSearchParams

/**
 * A CodeMirror 6 editor + a fake shell around the Climat engine.
 *
 * The engine (`:climatEngine`) is pure Kotlin/JS with no Node dependency, so we call `parse`,
 * `getValidations` and `execute` directly. Instead of running the resolved shell command (which is
 * impossible in a browser) we simply print it. Custom `act js` actions are actually run, but in
 * a sandbox where `require(...)` is unavailable — so `child_process`/`fs` calls fail gracefully.
 */

private val DEFAULT_DSL = """
sgit {
    sub acp(amend a: flag) {
        act sh {
            git add . &&
            git commit @{amend ? "--amend"} &&
            git push @{amend ? "--force"}
        }
    }
    sub cf(branch: arg, force f: flag) {
        act sh {
            git checkout feature/@{branch} @{force ? "--force"}
        }
    }
}
""".trimIndent()

private const val STORAGE_KEY = "climat-playground-source"
private const val THEME_STORAGE_KEY = "climat-playground-theme"

// --- CodeMirror + custom-script runner, wired through require() so webpack bundles them ---

/**
 * A hand-rolled CodeMirror 6 "legacy stream" tokenizer for the climat DSL (mirrors the modes of
 * the engine's `DslLexer.g4`: top-level keywords/punctuation, line and block comments, `"""`
 * docstrings with `@param` tags, `"..."` strings and `{ ... }` action templates (both support
 * `@{name}` / `@{!name}` / `@{name ? "mapping with a {} placeholder"}` interpolation).
 *
 * This is a simplified/flattened approximation of the ANTLR grammar's lexer modes, good enough
 * for editor highlighting without pulling in a full Lezer grammar.
 */
private val climatLanguage: dynamic = js(
    """
    (function () {
        var lang = require('@codemirror/language');

        var KEYWORD = /^(const|true|false|act|sh|js|scope-params|flag|arg|override|default|sub)(?![a-zA-Z0-9_-])/;
        var MODIFIER = /^@(seal|shift|alias|aliases|allow-unmatched)(?![a-zA-Z0-9_-])/;
        var IDENT = /^[a-zA-Z0-9_-]+/;

        function tokenBlockComment(stream, state) {
            var maybeEnd = false, ch;
            while ((ch = stream.next()) != null) {
                if (maybeEnd && ch === '/') { state.mode = 'top'; break; }
                maybeEnd = ch === '*';
            }
            return 'comment';
        }

        function tokenDocstringRef(stream, state) {
            stream.eatSpace();
            if (stream.match(IDENT)) { state.mode = 'docstring'; return 'variableName'; }
            state.mode = 'docstring';
            return null;
        }

        function tokenDocstring(stream, state) {
            if (stream.match('\"\"\"')) { state.mode = 'top'; return 'docComment'; }
            if (stream.match(/^@param(?![a-zA-Z0-9_-])/)) { state.mode = 'docstringRef'; return 'annotation'; }
            if (stream.match(/^[^@"]+/)) return 'docComment';
            stream.next();
            return 'docComment';
        }

        function tokenInterpolation(stream, state) {
            stream.eatSpace();
            if (stream.match('}')) { state.mode = 'template'; state.close = state.returnClose; state.returnClose = null; return 'punctuation'; }
            if (stream.match('!')) return 'operator';
            if (stream.match('?')) return 'punctuation';
            if (stream.match('\"')) { state.mode = 'mapping'; return 'string'; }
            if (stream.match(IDENT)) return 'variableName';
            stream.next();
            return null;
        }

        function tokenMapping(stream, state) {
            if (stream.match('\"')) { state.mode = 'interpolation'; return 'string'; }
            if (stream.match('{}')) return 'operator';
            if (stream.match(/^\\./)) return 'escape';
            if (stream.match(/^[^\\"{]+/)) return 'string';
            stream.next();
            return 'string';
        }

        // `state.close` is '"' for a string template, or the number of `{` that opened an action body
        function tokenTemplate(stream, state) {
            if (state.close === '\"') {
                if (stream.match('\"')) { state.mode = 'top'; state.close = null; return 'string'; }
            } else if (stream.match(new RegExp('^\\}{' + state.close + '}'))) {
                state.mode = 'top'; state.close = null; return 'meta';
            }
            if (stream.match(/^\\./)) return 'escape';
            if (stream.match('@{')) { state.mode = 'interpolation'; state.returnClose = state.close; return 'punctuation'; }
            var rest = state.close === '\"' ? /^[^\\"@]+/ : /^[^\\}@]+/;
            if (stream.match(rest)) return 'string';
            stream.next();
            return 'string';
        }

        function tokenTop(stream, state) {
            var afterAction = state.afterAction;
            state.afterAction = false;
            if (stream.match('//')) { stream.skipToEnd(); return 'comment'; }
            if (stream.match('/*')) return tokenBlockComment(stream, state);
            if (stream.match('\"\"\"')) { state.mode = 'docstring'; return 'docComment'; }
            if (stream.match('\"')) { state.mode = 'template'; state.close = '\"'; return 'string'; }
            // An action body opens with a run of `{` and closes with an equally long run of `}`
            if (afterAction && stream.match(/^\{+/)) {
                state.mode = 'template'; state.close = stream.current().length; return 'meta';
            }
            if (stream.match(MODIFIER)) return 'modifier';
            if (stream.match(KEYWORD)) { var kw = stream.current(); state.afterAction = kw === 'sh' || kw === 'js'; return 'keyword'; }
            if (stream.match(/^[(){}\[\]]/)) return 'bracket';
            if (stream.match(/^[=,:?]/)) return 'punctuation';
            if (stream.match(IDENT)) return 'variableName';
            stream.next();
            return null;
        }

        function token(stream, state) {
            if (stream.eatSpace()) return null;
            switch (state.mode) {
                case 'blockComment': return tokenBlockComment(stream, state);
                case 'docstring': return tokenDocstring(stream, state);
                case 'docstringRef': return tokenDocstringRef(stream, state);
                case 'template': return tokenTemplate(stream, state);
                case 'interpolation': return tokenInterpolation(stream, state);
                case 'mapping': return tokenMapping(stream, state);
                default: return tokenTop(stream, state);
            }
        }

        return lang.StreamLanguage.define({
            name: 'climat',
            startState: function () { return { mode: 'top', close: null, returnClose: null, afterAction: false }; },
            copyState: function (s) { return { mode: s.mode, close: s.close, returnClose: s.returnClose, afterAction: s.afterAction }; },
            token: token
        });
    })()
    """
)

private val climatHighlighting: dynamic = js(
    """
    (function () {
        var lang = require('@codemirror/language');
        var t = require('@lezer/highlight').tags;
        return lang.syntaxHighlighting(lang.HighlightStyle.define([
            { tag: t.keyword, color: 'var(--cmd)', fontWeight: '600' },
            { tag: t.modifier, color: 'var(--warn)', fontWeight: '600' },
            { tag: [t.comment, t.docComment], color: 'var(--muted)', fontStyle: 'italic' },
            { tag: t.annotation, color: 'var(--warn)' },
            { tag: t.string, color: 'var(--accent)' },
            { tag: t.escape, color: 'var(--warn)' },
            { tag: t.meta, color: 'var(--warn)', fontWeight: '600' },
            { tag: t.variableName, color: 'var(--fg)' },
            { tag: t.operator, color: 'var(--error)' },
            { tag: [t.punctuation, t.bracket], color: 'var(--muted)' }
        ]));
    })()
    """
)

/**
 * Overrides CodeMirror's built-in light/dark base theme (which hardcodes a light gutter
 * background regardless of the surrounding page) so gutters, selection and cursor follow our
 * CSS custom properties — and therefore repaint automatically when [data-theme] flips, with no
 * need to recreate the editor.
 *
 * `EditorView.theme()` (unlike `EditorView.baseTheme()`) doesn't support `&light`/`&dark`
 * selectors, and its rules land at the same specificity as CodeMirror's own base theme — so the
 * contested properties are marked `!important` to win regardless of base theme's light/dark
 * variant or rule order.
 */
private val climatEditorTheme: dynamic = js(
    """
    (function () {
        var view = require('@codemirror/view');
        return view.EditorView.theme({
            '&': { color: 'var(--fg)', backgroundColor: 'transparent' },
            '.cm-content': { caretColor: 'var(--accent)' },
            '.cm-cursor, .cm-dropCursor': { borderLeftColor: 'var(--accent)' },
            '.cm-selectionBackground': { backgroundColor: 'var(--border) !important' },
            '&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground': { backgroundColor: 'var(--border) !important' },
            '.cm-gutters': {
                backgroundColor: 'var(--panel-2) !important',
                color: 'var(--muted) !important',
                border: 'none !important',
                borderRight: '1px solid var(--border) !important'
            },
            '.cm-activeLineGutter': { backgroundColor: 'var(--border) !important', color: 'var(--fg) !important' },
            '.cm-activeLine': { backgroundColor: 'transparent !important' }
        });
    })()
    """
)

private val makeEditor: dynamic = js(
    """
    (function (parent, doc, onChange, langExt, highlightExt, themeExt) {
        var view = require('@codemirror/view');
        var cm = require('codemirror');
        var EditorView = view.EditorView;
        var listener = EditorView.updateListener.of(function (u) {
            if (u.docChanged) onChange(u.state.doc.toString());
        });
        return new EditorView({
            doc: doc,
            extensions: [cm.basicSetup, langExt, highlightExt, listener, themeExt, EditorView.lineWrapping],
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
    applyColorOverridesFromQuery()
    val themeForcedByHost = initTheme()
    if (themeForcedByHost) {
        (document.getElementById("theme-toggle") as? HTMLElement)?.style?.display = "none"
    } else {
        setupThemeToggle()
    }

    val initialSource = localStorage.getItem(STORAGE_KEY) ?: DEFAULT_DSL
    setupEditor(initialSource)
    setupResetButton()
    setupShell()
    analyze(initialSource)
    printLine("Welcome to the climat playground.", "note")
    printLine("Edit the definition on the left, then type a command below.", "note")
    printLine("Try:  acp --amend   |   cf myFeature --force", "note")
}

// --- Theme & embedding ---

/**
 * Lets the embedding page (this playground is meant to be rendered inside an iframe) brand the
 * UI via `?primaryLight=&secondaryLight=&primaryDark=&secondaryDark=` query params — any CSS
 * color syntax works. Primary drives the accent color (logo, prompt, cursor); secondary drives
 * the DSL keyword color in the editor. See also [initTheme] for the `?theme=` param.
 */
private fun applyColorOverridesFromQuery() {
    val params = URLSearchParams(window.location.search)
    val root = document.documentElement as HTMLElement
    params.get("primaryLight")?.let { root.style.setProperty("--accent-light", it) }
    params.get("secondaryLight")?.let { root.style.setProperty("--cmd-light", it) }
    params.get("primaryDark")?.let { root.style.setProperty("--accent-dark", it) }
    params.get("secondaryDark")?.let { root.style.setProperty("--cmd-dark", it) }
}

/**
 * Theme precedence: an explicit `?theme=light|dark` from the embedding page wins outright (and
 * isn't persisted, so the host stays in control on every load); otherwise fall back to the
 * user's last in-app toggle choice, then the OS preference.
 *
 * A synchronous inline script in index.html's `<head>` already resolves and applies this same
 * precedence before first paint (to avoid a flash of the wrong theme while this bundle loads) —
 * keep the two in sync. Re-applying it here is still needed to update the toggle button, which
 * only exists once this script runs.
 *
 * @return true if the theme came from the query param, in which case the host is dictating the
 *   theme and the in-app toggle is hidden rather than offering a control that wouldn't stick.
 */
private fun initTheme(): Boolean {
    val fromQuery = URLSearchParams(window.location.search).get("theme")?.takeIf { it == "light" || it == "dark" }
    val stored = localStorage.getItem(THEME_STORAGE_KEY)
    val theme = fromQuery ?: stored ?: if (window.matchMedia("(prefers-color-scheme: light)").matches) "light" else "dark"
    applyTheme(theme)
    return fromQuery != null
}

private fun applyTheme(theme: String) {
    val root = document.documentElement as HTMLElement
    root.setAttribute("data-theme", theme)
    val btn = document.getElementById("theme-toggle") as? HTMLElement ?: return
    btn.textContent = if (theme == "light") "🌙" else "☀️"
    btn.setAttribute("aria-label", if (theme == "light") "Switch to dark theme" else "Switch to light theme")
}

private fun setupThemeToggle() {
    val btn = document.getElementById("theme-toggle") as HTMLElement
    btn.addEventListener("click", {
        val current = document.documentElement?.getAttribute("data-theme") ?: "dark"
        val next = if (current == "light") "dark" else "light"
        localStorage.setItem(THEME_STORAGE_KEY, next)
        applyTheme(next)
    })
}

// --- Editor & diagnostics ---

private fun setupEditor(initialSource: String) {
    val parent = document.getElementById("editor")
    editorView = makeEditor(
        parent,
        initialSource,
        { src: String -> onDocChanged(src) },
        climatLanguage,
        climatHighlighting,
        climatEditorTheme,
    )
    // Small embedding hook: replace the editor content programmatically (also used by tests).
    window.asDynamic().__climatSetSource = { text: String -> setDoc(editorView, text) }
}

private fun setupResetButton() {
    val btn = document.getElementById("reset-source") as HTMLElement
    btn.addEventListener("click", {
        setDoc(editorView, DEFAULT_DSL)
    })
}

private fun currentSource(): String =
    editorView?.state?.doc?.toString() as? String ?: DEFAULT_DSL

private fun onDocChanged(source: String) {
    localStorage.setItem(STORAGE_KEY, source)
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
