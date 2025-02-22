package com.climat.library.validation

import com.climat.library.domain.toolchain.RootToolchain
import com.climat.library.domain.toolchain.Toolchain
import com.climat.library.utils.newLine
import com.climat.library.validation.validations.AllowUnmatchedOnNonLeaf
import com.climat.library.validation.validations.AncestorSubcommandWithSameName
import com.climat.library.validation.validations.BooleanFlippedMappings
import com.climat.library.validation.validations.DefaultForRequiredParam
import com.climat.library.validation.validations.DuplicateRefNames
import com.climat.library.validation.validations.DuplicateToolchainNamesOrAliases
import com.climat.library.validation.validations.FlagMappedTwice
import com.climat.library.validation.validations.ShadowedParams
import com.climat.library.validation.validations.UndefinedParams
import com.climat.library.validation.validations.UselessToolchain
import org.lighthousegames.logging.logging

val log = logging("Validation")

private val validators = listOf(
    AllowUnmatchedOnNonLeaf(),
    AncestorSubcommandWithSameName(),
    BooleanFlippedMappings(),
    DefaultForRequiredParam(),
    DuplicateToolchainNamesOrAliases(),
    DuplicateRefNames(),
    FlagMappedTwice(),
    ShadowedParams(),
    UndefinedParams(),
    UselessToolchain()
)

internal fun computeValidations(
    current: RootToolchain
) = computeValidations(current, current.sourceCode)

private fun computeValidations(
    current: Toolchain,
    sourceCode: String,
    traceToRoot: List<Toolchain> = emptyList(),
): Sequence<ValidationResult> =
    (
        validators.flatMap { validator ->
            validator.validate(
                ValidationContext(
                    traceToRoot = traceToRoot,
                    toolchain = current
                )
            ).map {
                ValidationResult(it.message, sourceCode, it.sourceMap, validator.code, validator.type)
            }
        } +
            current.children.flatMap {
                computeValidations(it, sourceCode, traceToRoot + listOf(current))
            }
        ).asSequence()

// TODO implement warning for unused variables
internal fun validate(toolchain: RootToolchain) {
    log.d { "Starting validation of ${validators.map { it.code.name }}" }

    val validations = computeValidations(toolchain)
    val errors = validations.filter { it.type == ValidationResult.ValidationEntryType.Error }.toList()
    if (errors.any()) {
        throw Exception(errors.joinToString(newLine()))
    }
}
