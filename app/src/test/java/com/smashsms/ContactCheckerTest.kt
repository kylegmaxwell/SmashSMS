package com.smashsms

import com.smashsms.filter.ContactChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactCheckerTest {

    @Test
    fun looksLikePhoneNumberDistinguishesNumbersAndNames() {
        assertTrue(ContactChecker.looksLikePhoneNumber("+1 (555) 123-4567"))
        assertTrue(ContactChecker.looksLikePhoneNumber("329717"))
        assertTrue(ContactChecker.looksLikePhoneNumber("555-0199"))

        assertFalse(ContactChecker.looksLikePhoneNumber("Alice Smith"))
        assertFalse(ContactChecker.looksLikePhoneNumber("ActBlue"))
        assertFalse(ContactChecker.looksLikePhoneNumber(""))
        assertFalse(ContactChecker.looksLikePhoneNumber("---"))
    }
}
