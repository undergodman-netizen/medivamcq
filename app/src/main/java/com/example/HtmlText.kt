package com.example

import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import io.noties.markwon.Markwon
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.ext.tables.TablePlugin

private fun Int.toHexColor() = String.format("#%06X", 0xFFFFFF and this)

sealed class HtmlPart {
    data class Text(val content: String) : HtmlPart()
    data class Table(val rows: List<List<String>>) : HtmlPart()
}

private fun extractHtmlParts(html: String): List<HtmlPart> {
    val tableRegex = Regex("(?si)<table[^>]*>(.*?)</table>")
    val parts = mutableListOf<HtmlPart>()
    var lastIndex = 0
    tableRegex.findAll(html).forEach { matchResult ->
        val textBefore = html.substring(lastIndex, matchResult.range.first)
        if (textBefore.trim().isNotBlank()) {
            parts.add(HtmlPart.Text(textBefore))
        }
        val tableContent = matchResult.groupValues[1]
        
        // Match TR, then match th/td within
        val rowRegex = Regex("(?si)<tr[^>]*>(.*?)</tr>")
        val parsedRows = mutableListOf<List<String>>()
        
        rowRegex.findAll(tableContent).forEach { rowMatch ->
            val rowContent = rowMatch.groupValues[1]
            val cellRegex = Regex("(?si)<t[dh][^>]*>(.*?)</t[dh]>")
            val cells = cellRegex.findAll(rowContent).map { it.groupValues[1].trim() }.toList()
            if (cells.isNotEmpty()) {
                parsedRows.add(cells)
            }
        }
        if (parsedRows.isNotEmpty()) {
            parts.add(HtmlPart.Table(parsedRows))
        }
        lastIndex = matchResult.range.last + 1
    }
    val textAfter = html.substring(lastIndex)
    if (textAfter.trim().isNotBlank()) {
        parts.add(HtmlPart.Text(textAfter))
    }
    return if (parts.isEmpty()) listOf(HtmlPart.Text(html)) else parts
}

private fun formatExplanationHtml(
    html: String,
    primaryHex: String,
    secondaryHex: String,
    tertiaryHex: String,
    errorHex: String,
    isTableCell: Boolean = false
): String {
    var cleanedHtml = html

    cleanedHtml = cleanedHtml.replace("\r\n", "\n").replace("\r", "\n")

    // Clean artifacts
    cleanedHtml = cleanedHtml
        .replace(Regex("<img[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("@med(?:_)?a[cd](?:\\s*a[cd])?", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\bmed(?:_)?a[cd](?:\\s*a[cd])?\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("@medAc", RegexOption.IGNORE_CASE), "")
        .replace(Regex("••\\s*Explanation\\s*\\n*\\s*••\\s*:\\s*\\n*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("••\\s*Option\\s*\\n*\\s*••\\s*([A-E]\\.)", RegexOption.IGNORE_CASE), "•• Option $1")
        .replace(Regex("•\\s*Explanation\\s*\\n*\\s*•\\s*:\\s*\\n*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("•\\s*Option\\s*\\n*\\s*•\\s*([A-E]\\.)", RegexOption.IGNORE_CASE), "• Option $1")

    // Remove excessive newlines that might cause gaps
    cleanedHtml = cleanedHtml.replace(Regex("\\n{3,}"), "\n\n")

    if (!isTableCell) {
        // Strip out newlines adjacent to block-level HTML tags so they don't produce extra empty lines in HtmlCompat
        val blockTagsPattern = "</?(?:p|div|ul|ol|li|table|tr|td|th|blockquote|br)\\b[^>]*>"
        var processedHtml = cleanedHtml
        processedHtml = processedHtml.replace(Regex("\\n\\s*($blockTagsPattern)", RegexOption.IGNORE_CASE), "$1")
        processedHtml = processedHtml.replace(Regex("($blockTagsPattern)\\s*\\n", RegexOption.IGNORE_CASE), "$1")

        // Now replace any remaining single raw newlines within text paragraphs with <br>
        cleanedHtml = processedHtml.replace("\n", "<br>")
        
        val replacements = mapOf(
            "Solution:" to primaryHex,
            "Highyeild:" to secondaryHex,
            "Highyield:" to secondaryHex,
            "Random:" to tertiaryHex,
            "Extraedge:" to errorHex,
            "Explanation for Incorrect Options:-" to errorHex,
            "Explanation for Incorrect Options:" to errorHex,
            "Explanation:" to primaryHex
        )

        var result = cleanedHtml

        for ((keyword, color) in replacements) {
            val regex = Regex("(?i)(?:<br\\s*/?>|\\s)*($keyword)\\s*(?:<br\\s*/?>|\\s)*")
            val displayWord = if (keyword.equals("Highyeild:", ignoreCase = true) || keyword.equals("Highyield:", ignoreCase = true)) "High Yield:" else keyword
            // Single gap before, standard jump after
            result = result.replace(regex, "<br><br><b><font color='$color'>$displayWord</font></b><br>")
        }

        val optionRegex = Regex("(?i)(?:<br\\s*/?>|\\s)*(Option:\\s*[A-E]\\.?|Option\\s*[A-E]\\b)\\s*(?:<br\\s*/?>|\\s)*")
        result = result.replace(optionRegex, "<br><br><b>$1</b> ")

        result = result.replace(Regex("(?i)(include:)(?:<br\\s*/?>|\\s)*"), "$1<br>")
        result = result.replace(Regex("(?i)(?:<br\\s*/?>)\\s*[•*-]\\s+"), "<br>&#8226; ")

        // Cap breaks to 2
        result = result.replace(Regex("(<br\\s*/?>\\s*){3,}"), "<br><br>")
        
        // Remove redundant <br> elements adjacent to other block-level tags to avoid double spacing
        val nonBrBlockTagsPattern = "</?(?:p|div|ul|ol|li|table|tr|td|th|blockquote)\\b[^>]*>"
        result = result.replace(Regex("(?:<br\\s*/?>\\s*)+($nonBrBlockTagsPattern)", RegexOption.IGNORE_CASE), "$1")
        result = result.replace(Regex("($nonBrBlockTagsPattern)\\s*(?:<br\\s*/?>\\s*)+", RegexOption.IGNORE_CASE), "$1")

        result = result.replace(Regex("^(?:<br\\s*/?>|\\s)*"), "")
        result = result.replace(Regex("(?:<br\\s*/?>|\\s)*\\b(a[cd])\\b(?:<br\\s*/?>|\\s)*$"), "")
        
        return result
    } else {
        // Strip out newlines adjacent to block-level HTML tags for tables as well
        val blockTagsPattern = "</?(?:p|div|ul|ol|li|table|tr|td|th|blockquote|br)\\b[^>]*>"
        var processedHtml = cleanedHtml
        processedHtml = processedHtml.replace(Regex("\\n\\s*($blockTagsPattern)", RegexOption.IGNORE_CASE), "$1")
        processedHtml = processedHtml.replace(Regex("($blockTagsPattern)\\s*\\n", RegexOption.IGNORE_CASE), "$1")
        
        return processedHtml.trim().replace("\n", "<br>")
    }
}

private fun isMarkdownContent(text: String): Boolean {
    val containsPipe = text.contains("|")
    val containsAsterisk = text.contains("**") || text.contains("__")
    val hasLines = text.contains("\n")
    if (!hasLines) {
        return containsAsterisk || text.startsWith("#") || text.startsWith("- ") || text.startsWith("* ")
    }
    val lines = text.lines()
    val hasTables = containsPipe && lines.any { line ->
        val trimmed = line.trim()
        trimmed.startsWith("|") && (trimmed.contains("---") || trimmed.contains("-:-") || trimmed.contains(":---"))
    }
    val hasHeaders = lines.any { it.trim().startsWith("#") }
    val hasList = lines.any { 
        val trimmed = it.trim()
        trimmed.startsWith("- ") || trimmed.startsWith("* ") || (trimmed.isNotEmpty() && trimmed.first().isDigit() && trimmed.contains(". "))
    }
    return hasTables || hasHeaders || containsAsterisk || hasList
}

private fun formatExplanationMarkdown(
    text: String,
    primaryHex: String,
    secondaryHex: String,
    tertiaryHex: String,
    errorHex: String
): String {
    var cleaned = text.replace("\r\n", "\n").replace("\r", "\n")

    // Fix legacy HTML mixed into markdown strings
    cleaned = cleaned.replace(Regex("(?i)<br\\s*/?>"), "\n")
    cleaned = cleaned.replace(Regex("(?i)</ul>"), "\n")
    cleaned = cleaned.replace(Regex("(?i)<ul>"), "\n")
    cleaned = cleaned.replace(Regex("(?i)<li>"), "- ")
    cleaned = cleaned.replace(Regex("(?i)</li>"), "\n")

    // Clean artifacts
    cleaned = cleaned
        .replace(Regex("<img[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("@med(?:_)?a[cd](?:\\s*a[cd])?", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\bmed(?:_)?a[cd](?:\\s*a[cd])?\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("@medAc", RegexOption.IGNORE_CASE), "")
        .replace(Regex("••\\s*Explanation\\s*\\n*\\s*••\\s*:\\s*\\n*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("••\\s*Option\\s*\\n*\\s*••\\s*([A-E]\\.)", RegexOption.IGNORE_CASE), "•• Option $1")
        .replace(Regex("•\\s*Explanation\\s*\\n*\\s*•\\s*:\\s*\\n*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("•\\s*Option\\s*\\n*\\s*•\\s*([A-E]\\.)", RegexOption.IGNORE_CASE), "• Option $1")

    // Cap excessive newlines to exactly 2 newlines (1 empty line)
    cleaned = cleaned.replace(Regex("\\n{3,}"), "\n\n")

    val replacements = mapOf(
        "Solution:" to primaryHex,
        "Highyeild:" to secondaryHex,
        "Highyield:" to secondaryHex,
        "Random:" to tertiaryHex,
        "Extraedge:" to errorHex,
        "Explanation for Incorrect Options:-" to errorHex,
        "Explanation for Incorrect Options:" to errorHex,
        "Explanation:" to primaryHex
    )

    for ((keyword, color) in replacements) {
        val regex = Regex("(?i)(?:\\n\\s*)*($keyword)\\s*(?:\\n\\s*)*")
        val displayWord = if (keyword.equals("Highyeild:", ignoreCase = true) || keyword.equals("Highyield:", ignoreCase = true)) "High Yield:" else keyword
        cleaned = cleaned.replace(regex, "\n\n**<span style='color:$color'><font color='$color'>$displayWord</font></span>**\n")
    }

    val optionRegex = Regex("(?i)(?:\\n\\s*)*(Option:\\s*[A-E]\\.?|Option\\s*[A-E]\\b)\\s*(?:\\n\\s*)*")
    cleaned = cleaned.replace(optionRegex, "\n\n**$1** ")

    // Cap excessive newlines again
    cleaned = cleaned.replace(Regex("\\n{3,}"), "\n\n")
    
    return cleaned.trim()
}

@Composable
fun HtmlText(
    html: String?, 
    modifier: Modifier = Modifier, 
    activeWord: String? = null,
    activeRange: IntRange? = null,
    onTapText: (() -> Unit)? = null
) {
    if (html.isNullOrBlank()) return

    val actualHtml = if (html.startsWith("file://")) {
        try {
            val path = html.substring("file://".length)
            val file = java.io.File(path)
            if (file.exists()) {
                file.readText(Charsets.UTF_8)
            } else {
                html
            }
        } catch (e: Exception) {
            html
        }
    } else {
        html
    }

    if (actualHtml.isBlank()) return

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val highlightBg = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val highlightFg = MaterialTheme.colorScheme.onPrimaryContainer.toArgb()
    
    val primaryHex = MaterialTheme.colorScheme.primary.toArgb().toHexColor()
    val secondaryHex = MaterialTheme.colorScheme.secondary.toArgb().toHexColor()
    val tertiaryHex = MaterialTheme.colorScheme.tertiary.toArgb().toHexColor()
    val errorHex = MaterialTheme.colorScheme.error.toArgb().toHexColor()

    val parts = extractHtmlParts(actualHtml)
    
    Column(modifier = modifier.fillMaxWidth()) {
        parts.forEach { part ->
            when (part) {
                is HtmlPart.Table -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            part.rows.forEachIndexed { rowIndex, row ->
                                val bgColor = if (rowIndex % 2 == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.Transparent
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .background(bgColor)
                                ) {
                                    row.forEachIndexed { colIndex, cellContent ->
                                        val formattedCell = formatExplanationHtml(
                                            html = cellContent,
                                            primaryHex = primaryHex,
                                            secondaryHex = secondaryHex,
                                            tertiaryHex = tertiaryHex,
                                            errorHex = errorHex,
                                            isTableCell = true
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(8.dp)
                                        ) {
                                            HtmlTextViewBasic(
                                                htmlContent = formattedCell,
                                                textColor = textColor,
                                                linkColor = linkColor
                                            )
                                        }
                                        if (colIndex < row.lastIndex) {
                                            Box(
                                                modifier = Modifier
                                                    .width(1.dp)
                                                    .fillMaxHeight()
                                                    .background(MaterialTheme.colorScheme.outlineVariant)
                                            )
                                        }
                                    }
                                }
                                if (rowIndex < part.rows.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )
                                }
                            }
                        }
                    }
                }
                is HtmlPart.Text -> {
                    val isMarkdown = isMarkdownContent(part.content)
                    val formattedHtml = if (isMarkdown) {
                        formatExplanationMarkdown(
                            text = part.content,
                            primaryHex = primaryHex,
                            secondaryHex = secondaryHex,
                            tertiaryHex = tertiaryHex,
                            errorHex = errorHex
                        )
                    } else {
                        formatExplanationHtml(
                            html = part.content,
                            primaryHex = primaryHex,
                            secondaryHex = secondaryHex,
                            tertiaryHex = tertiaryHex,
                            errorHex = errorHex,
                            isTableCell = false
                        )
                    }
                    HtmlTextViewHighlightable(
                        formattedHtml = formattedHtml,
                        textColor = textColor,
                        linkColor = linkColor,
                        highlightBg = highlightBg,
                        highlightFg = highlightFg,
                        activeWord = activeWord,
                        activeRange = activeRange,
                        onTapText = onTapText
                    )
                }
            }
        }
    }
}

@Composable
private fun HtmlTextViewBasic(
    htmlContent: String,
    textColor: Int,
    linkColor: Int
) {
    val context = LocalContext.current
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .build()
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                textSize = 15f
                setLineSpacing(0f, 1.25f)
                textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, htmlContent)
        }
    )
}

@Composable
private fun HtmlTextViewHighlightable(
    formattedHtml: String,
    textColor: Int,
    linkColor: Int,
    highlightBg: Int,
    highlightFg: Int,
    activeWord: String?,
    activeRange: IntRange?,
    onTapText: (() -> Unit)?
) {
    val context = LocalContext.current
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .build()
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(context).apply {
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                textSize = 15f 
                setLineSpacing(0f, 1.25f)
                textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            if (onTapText != null) {
                textView.setOnClickListener { onTapText() }
            } else {
                textView.setOnClickListener(null)
                textView.isClickable = false
            }
            
            val spanned = markwon.toMarkdown(formattedHtml)
            markwon.setParsedMarkdown(textView, spanned)
        }
    )
}


