package com.example.utils

import android.text.Html

fun stripHtmlForTts(html: String?): String {
    if (html.isNullOrBlank()) return ""
    
    // Process line by line to convert Markdown tables into natural conversational text
    val lines = html.lines()
    val processedLines = lines.map { line ->
        var l = line.trim()
        if (l.startsWith("|") && (l.contains("---") || l.contains("-:-") || l.contains(":---"))) {
            ""
        } else if (l.startsWith("|")) {
            // Strip outer pipes
            if (l.startsWith("|")) l = l.substring(1)
            if (l.endsWith("|")) l = l.substring(0, l.length - 1)
            
            val cells = l.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            if (cells.size >= 2) {
                cells.joinToString(", ") + "."
            } else if (cells.isNotEmpty()) {
                cells[0]
            } else {
                ""
            }
        } else {
            line
        }
    }
    
    val textWithNormalizedTables = processedLines.filter { it.isNotBlank() }.joinToString("\n")
    
    // Convert common HTML line-break and element endings to clean period-pauses
    val textWithPunctuation = textWithNormalizedTables
        .replace(Regex("(?i)<br\\s*/?>"), ", ")
        .replace(Regex("(?i)</p>"), ", ")
        .replace(Regex("(?i)</li>"), ", ")
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1") // Strip Markdown bold
        .replace(Regex("__(.*?)__"), "$1") // Strip Markdown bold
        .replace(Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)"), "$1") // Strip Markdown italic
        .replace(Regex("(?<!_)_(?!_)(.*?)(?<!_)_(?!_)"), "$1") // Strip Markdown italic
        .replace(Regex("#{1,6}\\s+"), "") // Strip Markdown headers
    
    // Strip other HTML elements safely using system Html.fromHtml
    val spannedText = Html.fromHtml(textWithPunctuation, Html.FROM_HTML_MODE_COMPACT).toString()
    
    // Polish the string to introduce clean strategic breathing/pausing points
    val polished = spannedText
        .replace(Regex("\n+"), " ")
        .replace(Regex(":\\s*"), ": ")
        .replace(Regex("^[-*•]\\s+"), "")
        .replace(Regex("\\s+[-*•]\\s+"), " ")
        .replace(Regex("[^a-zA-Z0-9\\s,.!?'’]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        
    return polished
}
