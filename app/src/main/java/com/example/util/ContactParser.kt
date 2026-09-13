package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

data class ParsedContact(
    val phoneNumber: String,
    val displayName: String = ""
)

object ContactParser {

    // Matches international (+...) or local phone numbers with optional separators
    // Examples: +1-800-555-0199, (555) 234-5678, +44 7911 123456, 9876543210, +919876543210
    private val PHONE_PATTERN = Pattern.compile(
        """(?:\+?[0-9]{1,4}[-.\s]?)?(?:\([0-9]{1,4}\)[-.\s]?)?[0-9]{3,4}[-.\s]?[0-9]{3,4}[-.\s]?[0-9]{0,6}"""
    )

    /**
     * Sanitizes a potential phone string into digits and optional leading +
     */
    fun sanitizePhoneNumber(raw: String): String {
        val trimmed = raw.trim()
        val hasLeadingPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (hasLeadingPlus) "+$digitsOnly" else digitsOnly
    }

    /**
     * Validates if the normalized number is within standard telecom lengths (7 to 16 digits)
     */
    fun isValidPhoneNumber(normalized: String): Boolean {
        val digits = normalized.filter { it.isDigit() }
        // E.164 recommends max 15 digits; minimum local subscriber numbers are ~7 digits
        return digits.length in 7..16
    }

    /**
     * Extracts all valid phone numbers and optional names from arbitrary raw text or pasted content.
     */
    fun parseRawText(
        text: String,
        onlyNumbers: Boolean = true,
        deduplicate: Boolean = true
    ): List<ParsedContact> {
        val results = mutableListOf<ParsedContact>()
        val seen = mutableSetOf<String>()

        val lines = text.split("\n", "\r")
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            // If line contains delimiters like comma/tab/semicolon, check parts
            val tokens = trimmedLine.split(",", ";", "\t")
            for (token in tokens) {
                val candidate = token.trim()
                if (candidate.isEmpty()) continue

                val matcher = PHONE_PATTERN.matcher(candidate)
                while (matcher.find()) {
                    val match = matcher.group().trim()
                    val sanitized = sanitizePhoneNumber(match)
                    if (isValidPhoneNumber(sanitized)) {
                        if (!deduplicate || seen.add(sanitized)) {
                            // Extract possible name if not strictly only numbers mode
                            val name = if (!onlyNumbers) {
                                val remaining = candidate.replace(match, "").trim(' ', '-', ':', ',')
                                if (remaining.length in 1..40) remaining else ""
                            } else ""

                            results.add(ParsedContact(phoneNumber = sanitized, displayName = name))
                        }
                    }
                }
            }
        }

        return results
    }

    /**
     * Parses a CSV or delimited text file.
     * If [onlyNumbers] is true, extracts every phone number anywhere in the CSV rows.
     * If false, attempts to map columns (e.g. Name, Phone).
     */
    fun parseCsv(
        inputStream: InputStream,
        onlyNumbers: Boolean = true,
        deduplicate: Boolean = true
    ): List<ParsedContact> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val results = mutableListOf<ParsedContact>()
        val seen = mutableSetOf<String>()

        var lineIndex = 0
        var phoneColIndex = -1
        var nameColIndex = -1

        reader.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                // Delimiter detection (comma, semicolon, tab)
                val delimiter = when {
                    trimmed.contains("\t") -> "\t"
                    trimmed.contains(";") -> ";"
                    else -> ","
                }

                val cols = parseCsvLine(trimmed, delimiter)

                // Header inspection on first line if structured parsing
                if (lineIndex == 0 && !onlyNumbers) {
                    cols.forEachIndexed { index, col ->
                        val lower = col.lowercase().trim()
                        if (lower.contains("phone") || lower.contains("mobile") || lower.contains("tel") || lower.contains("contact") || lower.contains("number")) {
                            phoneColIndex = index
                        }
                        if (lower.contains("name") || lower.contains("first") || lower.contains("client") || lower.contains("customer")) {
                            nameColIndex = index
                        }
                    }
                    if (phoneColIndex != -1) {
                        lineIndex++
                        continue
                    }
                }

                lineIndex++

                if (!onlyNumbers && phoneColIndex != -1 && phoneColIndex < cols.size) {
                    val rawPhone = cols[phoneColIndex]
                    val sanitized = sanitizePhoneNumber(rawPhone)
                    if (isValidPhoneNumber(sanitized)) {
                        if (!deduplicate || seen.add(sanitized)) {
                            val name = if (nameColIndex != -1 && nameColIndex < cols.size) cols[nameColIndex].trim() else ""
                            results.add(ParsedContact(phoneNumber = sanitized, displayName = name))
                        }
                    }
                } else {
                    // Extract all valid phone numbers across any column/cell
                    for (col in cols) {
                        val matcher = PHONE_PATTERN.matcher(col)
                        while (matcher.find()) {
                            val match = matcher.group()
                            val sanitized = sanitizePhoneNumber(match)
                            if (isValidPhoneNumber(sanitized)) {
                                if (!deduplicate || seen.add(sanitized)) {
                                    results.add(ParsedContact(phoneNumber = sanitized, displayName = ""))
                                }
                            }
                        }
                    }
                }
            }
        }

        return results
    }

    /**
     * Lightweight native Excel (.xlsx) parser using ZipInputStream and XmlPullParser.
     * Extracts all cells, shared strings, and pulls out numbers.
     */
    fun parseXlsx(
        inputStream: InputStream,
        onlyNumbers: Boolean = true,
        deduplicate: Boolean = true
    ): List<ParsedContact> {
        val sharedStrings = mutableListOf<String>()
        val cellValues = mutableListOf<String>()

        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        val rawEntries = mutableMapOf<String, ByteArray>()

        while (entry != null) {
            val name = entry.name
            if (name == "xl/sharedStrings.xml" || name.startsWith("xl/worksheets/sheet")) {
                val bytes = zip.readBytes()
                rawEntries[name] = bytes
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }

        // 1. Parse shared strings if present
        rawEntries["xl/sharedStrings.xml"]?.let { bytes ->
            val parser = Xml.newPullParser()
            parser.setInput(bytes.inputStream(), "UTF-8")
            var eventType = parser.eventType
            var inTTag = false
            val currentText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name.equals("t", ignoreCase = true)) {
                            inTTag = true
                            currentText.clear()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inTTag) {
                            currentText.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("t", ignoreCase = true)) {
                            inTTag = false
                            sharedStrings.add(currentText.toString())
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        // 2. Parse sheet cells
        for ((name, bytes) in rawEntries) {
            if (name.startsWith("xl/worksheets/sheet")) {
                val parser = Xml.newPullParser()
                parser.setInput(bytes.inputStream(), "UTF-8")
                var eventType = parser.eventType
                var isSharedString = false
                var inValueTag = false
                val valBuilder = StringBuilder()

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            val tag = parser.name
                            if (tag.equals("c", ignoreCase = true)) {
                                val typeAttr = parser.getAttributeValue(null, "t")
                                isSharedString = typeAttr == "s"
                            } else if (tag.equals("v", ignoreCase = true)) {
                                inValueTag = true
                                valBuilder.clear()
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inValueTag) {
                                valBuilder.append(parser.text)
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            val tag = parser.name
                            if (tag.equals("v", ignoreCase = true)) {
                                inValueTag = false
                                val rawVal = valBuilder.toString().trim()
                                if (isSharedString) {
                                    val idx = rawVal.toIntOrNull()
                                    if (idx != null && idx in sharedStrings.indices) {
                                        cellValues.add(sharedStrings[idx])
                                    }
                                } else {
                                    cellValues.add(rawVal)
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        }

        // 3. Extract phone numbers from all collected cells
        val results = mutableListOf<ParsedContact>()
        val seen = mutableSetOf<String>()

        for (cell in cellValues) {
            val matcher = PHONE_PATTERN.matcher(cell)
            while (matcher.find()) {
                val match = matcher.group()
                val sanitized = sanitizePhoneNumber(match)
                if (isValidPhoneNumber(sanitized)) {
                    if (!deduplicate || seen.add(sanitized)) {
                        results.add(ParsedContact(phoneNumber = sanitized, displayName = ""))
                    }
                }
            }
        }

        return results
    }

    /**
     * Ingests a file by URI, automatically detecting whether it is CSV, TXT, or XLSX.
     */
    fun parseUri(
        context: Context,
        uri: Uri,
        fileName: String,
        onlyNumbers: Boolean = true,
        deduplicate: Boolean = true
    ): List<ParsedContact> {
        val lower = fileName.lowercase()
        val stream = context.contentResolver.openInputStream(uri) ?: return emptyList()

        return stream.use { inStream ->
            when {
                lower.endsWith(".xlsx") -> parseXlsx(inStream, onlyNumbers, deduplicate)
                lower.endsWith(".csv") || lower.endsWith(".txt") -> parseCsv(inStream, onlyNumbers, deduplicate)
                else -> {
                    // Try parsing as CSV first
                    parseCsv(inStream, onlyNumbers, deduplicate)
                }
            }
        }
    }

    private fun parseCsvLine(line: String, delimiter: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when (ch) {
                '"' -> inQuotes = !inQuotes
                delimiter[0] -> {
                    if (inQuotes) {
                        sb.append(ch)
                    } else {
                        tokens.add(sb.toString().trim(' ', '"'))
                        sb.clear()
                    }
                }
                else -> sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim(' ', '"'))
        return tokens
    }
}
