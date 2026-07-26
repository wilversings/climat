
package com.climat.library.domain.action

import com.climat.library.domain.SourceTraceable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

typealias Action = ActionValueBase<*>

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class ActionValueBase<VType> internal constructor() : SourceTraceable() {

    var value: VType? = null

    val type: Type = when (this) {
        is TemplateActionValue -> Type.Template
        is MicroshellActionValue -> Type.Microshell
        is JavaScriptActionValue -> Type.CustomScript
        is ScopeParamsActionValue -> Type.ScopeParams
        is NoopActionValue -> Type.Noop
        else -> throw Exception("${this::class} is not supported")
    }

    enum class Type {
        Template,
        Microshell,
        CustomScript,
        ScopeParams,
        Noop
    }
}
