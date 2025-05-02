package com.github.somtooo.gitnotify.lib
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class SkipCiRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                    val skipCi = description.getAnnotation(SkipCi::class.java) != null
                    val isCi = System.getenv("CI")?.equals("true", ignoreCase = true) == true

                    Assume.assumeFalse(
                        "$TEST_SKIPPED_MSG: Skipping test due to CI environment",
                        skipCi && isCi
                    )
                base.evaluate()
            }
        }
    }

    companion object {
        private const val TEST_SKIPPED_MSG = "Assumption Violation: "
    }
}