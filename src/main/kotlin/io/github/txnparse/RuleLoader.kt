package io.github.txnparse

import kotlinx.serialization.json.Json

/** Loads bank rules from JSON. */
public object RuleLoader {
    private const val RULES_DIR = "txnparse/rules"

    private val json = Json { isLenient = false }

    /** Parses one rule file. Throws [IllegalArgumentException] for invalid rules. */
    public fun fromJson(text: String): BankRule {
        val rule = json.decodeFromString(BankRule.serializer(), text)
        validateShape(rule)
        return rule
    }

    /** The rules bundled with the library, listed in `txnparse/rules/index.txt`. */
    public fun bundledRules(): List<BankRule> {
        val index = readResource("$RULES_DIR/index.txt")
        return index.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { fileName -> fromJson(readResource("$RULES_DIR/$fileName")) }
            .toList()
    }

    private fun readResource(path: String): String {
        val stream = RuleLoader::class.java.classLoader.getResourceAsStream(path)
            ?: error("Missing bundled resource: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun validateShape(rule: BankRule) {
        require(rule.id.matches(Regex("[a-z0-9_]+"))) { "Rule id '${rule.id}' must be lowercase letters, digits or _" }
        require(rule.senders.isNotEmpty()) { "Rule '${rule.id}' needs at least one sender" }
        require(rule.patterns.isNotEmpty()) { "Rule '${rule.id}' needs at least one pattern" }
        rule.patterns.forEachIndexed { i, pattern ->
            val regex = try {
                Regex(pattern.regex)
            } catch (e: Exception) {
                throw IllegalArgumentException("Rule '${rule.id}' pattern $i is not a valid regex: ${e.message}", e)
            }
            require(pattern.regex.contains("(?<amount>")) { "Rule '${rule.id}' pattern $i has no (?<amount>...) group" }
            if (pattern.regex.contains("(?<date>")) {
                require(pattern.dateFormats.isNotEmpty() || rule.dateFormats.isNotEmpty()) {
                    "Rule '${rule.id}' pattern $i captures a date but no dateFormats are set"
                }
            }
            regex.toString() // compiled successfully
        }
    }
}
