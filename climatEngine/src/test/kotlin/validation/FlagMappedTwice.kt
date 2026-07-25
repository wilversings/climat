package validation

import com.climat.library.dslParser.dsl.decodeCliDsl
import com.climat.library.validation.validations.ValidationCode
import utils.assertContainsInMessages
import utils.getValidationMessages
import kotlin.test.Test

class FlagMappedTwice {
    private val toolchain = """
        root(root_param1: arg, root_param2: arg) {
            act sh { dummy command @{root_param} @{root_param1 ? "dummyCommandParam"} @{root_param2 ? "dummyCommandParam"} }

            sub child {
                act sh { dummy2 command @{r ? "cparam"} @{c ? "cparam"} }
            }
        }
    """

    @Test
    fun test() {
        val validationResults = decodeCliDsl(toolchain).getValidationMessages(ValidationCode.FlagMappedTwice)
        assertContainsInMessages(
            validationResults,
            "dummyCommandParam",
            "cparam"
        )
    }
}
