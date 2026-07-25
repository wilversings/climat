package com.climat.library.dslParser.template

import climat.lang.DslParser.ActionTemplateEntryContext
import climat.lang.DslParser.StringTemplateEntryContext
import com.climat.library.domain.action.template.Interpolation
import com.climat.library.domain.action.template.SimpleString
import com.climat.library.domain.action.template.Template
import com.climat.library.dslParser.exception.assertRequire
import com.climat.library.dslParser.exception.throwUnexpected
import com.climat.library.utils.unescape

internal fun decodeTemplate(cliDsl: String, templateEntries: List<ActionTemplateEntryContext>): Template =
    templateEntries.map { entry ->
        val actionTemplateContent = entry.actionTemplateContent()
        val actionTemplateInterpolation = entry.actionTemplateInterpolation()

        when {
            actionTemplateContent != null -> SimpleString(actionTemplateContent.text.unescape('@').unescape("%>"))

            actionTemplateInterpolation != null -> Interpolation(
                name = actionTemplateInterpolation.assertRequire(cliDsl) { Interpolation_IDENTIFIER() }.text,
                mapping = actionTemplateInterpolation.mapping()?.Interpolation_IDENTIFIER()?.text,
                isFlipped = actionTemplateInterpolation.Interpolation_NEGATE() != null
            )

            else -> entry.throwUnexpected("No content or interpolation found", cliDsl)
        }
    }.let(::Template)

internal fun decodeTemplate(cliDsl: String, templateEntries: List<StringTemplateEntryContext>): Template =
    templateEntries.map { entry ->
        val stringTemplateContent = entry.stringTemplateContent()
        val stringTemplateInterpolation = entry.stringTemplateInterpolation()

        when {
            stringTemplateContent != null -> SimpleString(stringTemplateContent.text.unescape('@').unescape('"'))

            stringTemplateInterpolation != null -> Interpolation(
                name = stringTemplateInterpolation.assertRequire(cliDsl) { Interpolation_IDENTIFIER() }.text,
                mapping = stringTemplateInterpolation.mapping()?.Interpolation_IDENTIFIER()?.text,
                isFlipped = stringTemplateInterpolation.Interpolation_NEGATE() != null
            )

            else -> entry.throwUnexpected("No content or interpolation found", cliDsl)
        }
    }.let(::Template)
