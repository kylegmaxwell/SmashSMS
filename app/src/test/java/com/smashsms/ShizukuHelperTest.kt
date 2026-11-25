package com.smashsms

import com.smashsms.shizuku.ShizukuHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ShizukuHelperTest {

    @Test
    fun buildDeleteCommandUsesExpectedContentDeleteArguments() {
        val cmd = ShizukuHelper.buildDeleteCommand("+15551234567")
        assertArrayEquals(
            arrayOf(
                "content",
                "delete",
                "--uri",
                "content://sms",
                "--where",
                "address='+15551234567'",
            ),
            cmd,
        )
    }

    @Test
    fun buildDeleteCommandEscapesSingleQuotesInSenderAddress() {
        val cmd = ShizukuHelper.buildDeleteCommand("' OR '1'='1")
        assertArrayEquals(
            arrayOf(
                "content",
                "delete",
                "--uri",
                "content://sms",
                "--where",
                "address=''' OR ''1''=''1'",
            ),
            cmd,
        )
    }
}
