package com.inspiremusic.security

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NoHardcodedSecretsTest {
    @Test
    fun sourceTreeDoesNotContainHardcodedApiCredentials() {
        val sourceRoot = File("src")
        if (!sourceRoot.exists()) return

        val knownCredentialPatterns = listOf(
            Regex("AIza[0-9A-Za-z_-]{30,}"),
            Regex("A" + "Q\\.[0-9A-Za-z_-]{40,}"),
            Regex("s" + "k-[0-9A-Za-z._-]{16,}"),
            Regex("github" + "_pat_[0-9A-Za-z_]{20,}"),
            Regex("g" + "h[pousr]_[0-9A-Za-z]{20,}"),
            Regex("AKIA[0-9A-Z]{16}"),
        )
        val literalAssignment = Regex(
            """(?i)(?:api[_-]?key|client[_-]?secret|access[_-]?token)\s*(?::[^=\r\n]+)?=\s*[\"]([^\"]{12,})[\"]""",
        )
        val allowedLiteralValues = setOf("your-api-key", "replace-me", "example", "test", "dummy")
        val findings = mutableListOf<String>()

        sourceRoot.walkTopDown()
            .filter(File::isFile)
            .filter { it.extension.lowercase() in TEXT_EXTENSIONS }
            .forEach { file ->
                val content = runCatching(file::readText).getOrNull() ?: return@forEach
                knownCredentialPatterns.forEach { pattern ->
                    if (pattern.containsMatchIn(content)) findings += file.relativeTo(sourceRoot).path
                }
                literalAssignment.findAll(content).forEach { match ->
                    val value = match.groupValues[1].trim()
                    val isRuntimeReference = value.startsWith("$") ||
                        value.contains("config.") ||
                        value.contains("BuildConfig.") ||
                        value.contains("getString(")
                    if (!isRuntimeReference && value.lowercase() !in allowedLiteralValues) {
                        findings += file.relativeTo(sourceRoot).path
                    }
                }
            }

        assertTrue(
            "Hardcoded credential-like value found in: ${findings.distinct().sorted().joinToString()}",
            findings.isEmpty(),
        )
    }

    private companion object {
        val TEXT_EXTENSIONS = setOf(
            "kt", "kts", "java", "xml", "json", "properties", "gradle", "toml", "yml", "yaml",
        )
    }
}
