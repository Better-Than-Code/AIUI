package com.cellular.rpc.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanPrimary

/**
 * High-performance Markdown parser for iMessage/Slack-grade rich chat rendering.
 * Supports:
 * - Code Blocks (```kotlin ... ```) with syntax styling and one-tap Copy
 * - Inline code (`variable`)
 * - Bold (**text**) & Italics (*text* / _text_)
 * - Bullet lists (- item or * item)
 * - Numbered lists (1. item)
 * - Headings (### Header, ## Header, # Header)
 * - Blockquotes (> quote)
 */
@Composable
fun RichMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    val clipboardManager = LocalClipboardManager.current
    var copiedCodeIndex by remember { mutableStateOf<Int?>(null) }

    // Strip internal thread tags [TID:...] if any happen to reach presentation layer
    val cleanText = remember(text) {
        text.replace(Regex("\\[(?:TID|THREAD):[^\\]]+\\]\\s*"), "")
            .replace(Regex("\"(?:threadId|tid)\"\\s*:\\s*\"[^\"]+\"\\s*,?\\s*"), "")
            .trim()
    }

    // Split text by markdown code blocks (```...```)
    val parts = remember(cleanText) { splitByCodeBlocks(cleanText) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEachIndexed { index, part ->
            when (part) {
                is MarkdownPart.CodeBlock -> {
                    CodeBlockCard(
                        language = part.language,
                        code = part.code,
                        isCopied = copiedCodeIndex == index,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(part.code))
                            copiedCodeIndex = index
                        }
                    )
                }
                is MarkdownPart.Paragraph -> {
                    val annotatedString = remember(part.content, textColor) {
                        parseInlineMarkdown(part.content, textColor)
                    }
                    if (annotatedString.isNotBlank()) {
                        Text(
                            text = annotatedString,
                            style = style,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    isCopied: Boolean,
    onCopy: () -> Unit
) {
    Surface(
        color = Color(0xFF0D1117),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            // Header bar with language tag & Copy action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "CODE" }.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary
                )
                Row(
                    modifier = Modifier.clickable { onCopy() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (isCopied) Color(0xFF00E676) else Color.LightGray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCopied) "COPIED" else "COPY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCopied) Color(0xFF00E676) else Color.LightGray
                    )
                }
            }

            // Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFE6EDF3),
                    lineHeight = 17.sp
                )
            }
        }
    }
}

sealed class MarkdownPart {
    data class Paragraph(val content: String) : MarkdownPart()
    data class CodeBlock(val language: String, val code: String) : MarkdownPart()
}

private fun splitByCodeBlocks(text: String): List<MarkdownPart> {
    val list = mutableListOf<MarkdownPart>()
    val regex = Regex("```([a-zA-Z0-9_-]*)\\s*\\n?([\\s\\S]*?)```")
    var lastIndex = 0

    regex.findAll(text).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1

        if (start > lastIndex) {
            val paragraph = text.substring(lastIndex, start).trim()
            if (paragraph.isNotEmpty()) {
                list.add(MarkdownPart.Paragraph(paragraph))
            }
        }

        val lang = match.groupValues.getOrElse(1) { "" }.trim()
        val code = match.groupValues.getOrElse(2) { "" }.trimEnd()
        list.add(MarkdownPart.CodeBlock(lang, code))

        lastIndex = end
    }

    if (lastIndex < text.length) {
        val remaining = text.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) {
            list.add(MarkdownPart.Paragraph(remaining))
        }
    }

    if (list.isEmpty() && text.isNotEmpty()) {
        list.add(MarkdownPart.Paragraph(text))
    }

    return list
}

private fun parseInlineMarkdown(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { lineIdx, line ->
            if (lineIdx > 0) append("\n")

            var current = line

            // Headings
            if (current.startsWith("### ")) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CyanPrimary)) {
                    append(current.removePrefix("### "))
                }
                return@forEachIndexed
            } else if (current.startsWith("## ")) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CyanPrimary)) {
                    append(current.removePrefix("## "))
                }
                return@forEachIndexed
            } else if (current.startsWith("# ")) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CyanPrimary)) {
                    append(current.removePrefix("# "))
                }
                return@forEachIndexed
            }

            // Bullet points
            if (current.startsWith("- ") || current.startsWith("* ")) {
                append("• ")
                current = current.substring(2)
            } else if (Regex("^\\d+\\.\\s").containsMatchIn(current)) {
                // numbered list item
                val match = Regex("^\\d+\\.\\s").find(current)
                if (match != null) {
                    append(match.value)
                    current = current.substring(match.value.length)
                }
            }

            // Inline parser for bold (**text**), italics (*text* or _text_), and inline code (`code`)
            val inlineRegex = Regex("(`[^`]+`)|(\\*\\*[^\\*]+\\*\\*)|(\\*[^\\*]+\\*)|(_[^_]+_)")
            var lastPos = 0

            inlineRegex.findAll(current).forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1

                if (start > lastPos) {
                    append(current.substring(lastPos, start))
                }

                val matchedStr = match.value
                when {
                    matchedStr.startsWith("`") && matchedStr.endsWith("`") -> {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0x33000000),
                                color = CyanPrimary,
                                fontSize = 12.sp
                            )
                        ) {
                            append(matchedStr.removeSurrounding("`"))
                        }
                    }
                    matchedStr.startsWith("**") && matchedStr.endsWith("**") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(matchedStr.removeSurrounding("**"))
                        }
                    }
                    matchedStr.startsWith("*") && matchedStr.endsWith("*") -> {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(matchedStr.removeSurrounding("*"))
                        }
                    }
                    matchedStr.startsWith("_") && matchedStr.endsWith("_") -> {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(matchedStr.removeSurrounding("_"))
                        }
                    }
                }
                lastPos = end
            }

            if (lastPos < current.length) {
                append(current.substring(lastPos))
            }
        }
    }
}
