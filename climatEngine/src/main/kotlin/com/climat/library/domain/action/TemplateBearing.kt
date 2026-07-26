package com.climat.library.domain.action

import com.climat.library.domain.action.template.Template

/**
 * The template behind an action, when it has one.
 *
 * `act sh` and `act microsh` share the same `@{ref}` interpolation, so every ref-oriented validation
 * applies to both. Going through this instead of matching on [TemplateActionValue] keeps those
 * validations working for microshell actions without widening the `@JsExport` surface.
 */
internal val Action.templateOrNull: Template?
    get() = when (this) {
        is TemplateActionValue -> template
        is MicroshellActionValue -> template
        else -> null
    }
