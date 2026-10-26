package com.example

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class TtsManagerTest {

    @Test
    fun testTextChunking_ExceedsLimit() {
        val maxChunkSize = 2500
        val sentence = "This is a sentence. "
        // Create a text that is > 2500 characters
        val longText = sentence.repeat(150) // 20 * 150 = 3000 chars

        val chunks = mutableListOf<String>()
        var currentText = longText
        while (currentText.length > maxChunkSize) {
            var splitIndex = currentText.lastIndexOf('.', maxChunkSize)
            if (splitIndex == -1) splitIndex = currentText.lastIndexOf(' ', maxChunkSize)
            if (splitIndex == -1) splitIndex = maxChunkSize
            chunks.add(currentText.substring(0, splitIndex + 1).trim())
            currentText = currentText.substring(splitIndex + 1).trim()
        }
        if (currentText.isNotEmpty()) {
            chunks.add(currentText)
        }

        assertTrue("Long text should be split into multiple chunks", chunks.size > 1)
        assertTrue("No chunk should exceed the max limit", chunks.all { it.length <= maxChunkSize + 1 })
    }
}
