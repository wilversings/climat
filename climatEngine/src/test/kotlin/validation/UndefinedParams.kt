package validation

import com.climat.library.dslParser.dsl.decodeCliDsl
import com.climat.library.validation.validations.ValidationCode
import utils.assertContainsInMessages
import utils.getValidationMessages
import kotlin.test.Test

class UndefinedParams {
    private val toolchain = """
        root(param1: flag, param2: arg?) {
            act sh { dummy @{undef} }
            sub root_child(param1: flag, param_2: arg?, param3: arg?) {
                act sh { dummy @{param1} @{param2} @{param_2} @{undef} @{undef2} }
            }
        }
    """

    @Test
    fun test() {
        val validationResults = decodeCliDsl(toolchain).getValidationMessages(ValidationCode.UndefinedParams)
        assertContainsInMessages(
            validationResults,
            "undef",
            "undef",
            "undef2"
        )
    }
}
