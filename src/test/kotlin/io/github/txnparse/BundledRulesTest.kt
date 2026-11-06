package io.github.txnparse

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Every bundled rule file is valid, listed in the index, and passes its own samples. */
class BundledRulesTest {

    @TestFactory
    fun `every rule passes its samples`(): List<DynamicTest> =
        RuleLoader.bundledRules().map { rule ->
            DynamicTest.dynamicTest(rule.id) {
                val problems = RuleValidator.validate(rule)
                if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
            }
        }

    @Test
    fun `every rule file is listed in the index`() {
        val dir = File("src/main/resources/txnparse/rules")
        val files = dir.listFiles { f -> f.extension == "json" }!!.map { it.name }.sorted()
        val indexed = File(dir, "index.txt").readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        assertEquals(files, indexed.sorted(), "index.txt must list exactly the .json files in $dir")
    }

    @Test
    fun `rule ids match file names and are unique`() {
        val rules = RuleLoader.bundledRules()
        assertEquals(rules.size, rules.map { it.id }.toSet().size, "duplicate rule ids")
        val dir = File("src/main/resources/txnparse/rules")
        rules.forEach { rule -> assertTrue(File(dir, "${rule.id}.json").exists(), "${rule.id}.json missing") }
    }

    @Test
    fun `samples don't match another bank's rules`() {
        // Parse each sample with every bank loaded and no sender hint: it must still land on its own bank.
        val parser = TransactionParser.withBundledRules(useGenericFallback = false)
        RuleLoader.bundledRules().forEach { rule ->
            rule.samples.forEachIndexed { i, sample ->
                val txn = parser.parseOrNull(sample.sms) ?: fail("[${rule.id}] sample $i matched nothing")
                assertEquals(rule.id, txn.bankId, "[${rule.id}] sample $i was claimed by ${txn.bankId}")
            }
        }
    }
}
