package com.smashsms

import com.smashsms.filter.SpamDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamDetectorTest {

    @Test
    fun detectsPoliticalFundraisingAndOptOutPatterns() {
        val spamSamples = listOf(
            "Donate today at https://secure.actblue.com/donate/example",
            "Support the campaign on WinRed right now!",
            "Link from ngpvan.com/xyz",
            "Urgent alert! Reply STOP to end messages.",
            "Text STOP2END to opt out.",
            "Can you chip in \$5 before midnight?",
            "UNLOCK YOUR 400% MATCH BEFORE MIDNIGHT!",
            "FEC deadline is tonight at 11:59 PM.",
            "Reply STOP to quit receiving alerts.",
            "Urgent alert! Take a look >> example-campaign.org/l/abc123 End2End",
            "Just voted! Share this: examplepac.com/l/def456 End2End",
            "Will you donate now? https://charity-example.org/xyz789 Example Org Stop To End",
            "Take our survey here: https://survey-example.co/sample123 Thanks! STOP to quit",
            "A new role matching your preferences is available. Reply STOP to opt out.",
            "Example Org has grassroots donors like you & a 2x match! > example-dems.org/l/ghi789 End2End",
            "I'm working with realtors in Springfield and there are buyers actively looking... do you have any plans of moving in the next 12 months?",
            "Link without footer: https://sample-pac.com/l/jkl012",
        )

        for (sample in spamSamples) {
            assertTrue("Expected spam match for: $sample", SpamDetector.isPoliticalSpam(sample))
        }
    }

    @Test
    fun allowsNormalMessages() {
        val hamSamples = listOf(
            "Hey, are we still on for dinner at 6?",
            "Your verification code is 482910.",
            "Please stop by the grocery store on your way home.",
            "Running 10 minutes late!",
        )

        for (sample in hamSamples) {
            assertFalse("Expected non-spam for: $sample", SpamDetector.isPoliticalSpam(sample))
        }
        assertFalse(SpamDetector.isPoliticalSpam(null))
        assertFalse(SpamDetector.isPoliticalSpam("   "))
    }

    @Test
    fun detectReturnsMatchedRuleName() {
        val match = SpamDetector.detect("Donate via ActBlue before the FEC deadline")
        assertTrue(match.isSpam)
        org.junit.Assert.assertEquals("ActBlue", match.matchedRule)
    }
}
