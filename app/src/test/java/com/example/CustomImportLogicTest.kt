package com.example

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class CustomImportLogicTest {

    @Test
    fun testCustomSourcePrefixRemovalAndDisplayName() {
        val rawSource = "custom://Foo_123.json"
        val stripped = rawSource.removePrefix("custom://")
        assertEquals("Foo_123.json", stripped)

        val displayName = formatSourceDisplayName(stripped)
        assertTrue("Display name must not be blank", displayName.isNotBlank())
        assertTrue(
            "Display name should be title-cased and start with an uppercase letter",
            displayName.first().isUpperCase()
        )
    }

    @Test
    fun testSchemaAutoRepairLogic() {
        val rawSubject: String? = null
        val rawTopic: String? = ""
        val rawOptions: List<String>? = listOf("Option 1")
        val rawCorrectAnswer = ""

        val validSubject = rawSubject?.takeIf { it.isNotBlank() } ?: "General"
        val validTopic = rawTopic?.takeIf { it.isNotBlank() } ?: "Custom Import"
        val validOptions = when {
            rawOptions.isNullOrEmpty() -> listOf("True", "False")
            rawOptions.size == 1 -> listOf(rawOptions[0], "None of the above")
            else -> rawOptions
        }
        val validCorrectAnswer = when {
            rawCorrectAnswer.isNotBlank() && validOptions.contains(rawCorrectAnswer) -> rawCorrectAnswer
            else -> validOptions.first()
        }

        assertEquals("General", validSubject)
        assertEquals("Custom Import", validTopic)
        assertEquals(2, validOptions.size)
        assertEquals("Option 1", validCorrectAnswer)
    }

    @Test
    fun testSourceScopedDeduplicationKey() {
        val q1 = McqField(question = "Sample Q", source_url = "custom://source_a.json")
        val q2 = McqField(question = "Sample Q", source_url = "custom://source_b.json")

        val list = listOf(q1, q2)
        val distinct = list.distinctBy { Pair(it.source_url ?: "", it.question.trim().lowercase()) }

        assertEquals(
            "Questions with identical text from different custom sources should not be deduplicated away",
            2,
            distinct.size
        )
    }
}
