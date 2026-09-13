package com.example

import com.example.util.ContactParser
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testPhoneNumberSanitization() {
    val raw = "+1 (800) 555-0199"
    val sanitized = ContactParser.sanitizePhoneNumber(raw)
    assertEquals("+18005550199", sanitized)
    assertTrue(ContactParser.isValidPhoneNumber(sanitized))
  }

  @Test
  fun testFilterOnlyNumbersFromMessyText() {
    val messy = """
      Alice: +1-555-123-4567, extra notes
      Invalid: 12345
      Bob: 9876543210 (direct line)
      Duplicate: +1-555-123-4567
    """.trimIndent()

    val parsed = ContactParser.parseRawText(
      text = messy,
      onlyNumbers = true,
      deduplicate = true
    )

    assertEquals(2, parsed.size)
    assertEquals("+15551234567", parsed[0].phoneNumber)
    assertEquals("9876543210", parsed[1].phoneNumber)
  }
}

