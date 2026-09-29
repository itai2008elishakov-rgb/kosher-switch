package dev.kosherswitch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardTest {
    private val blocked = listOf(
        "tell me about sex", "what is porn", "p o r n sites", "s3x tips", "show me naked women", "write an erotic story",
        "how do I sleep with a girl", "best dating app", "p.o.r.n.o", "whats onlyfans", "SEXY girls",
        "ספר לי על סקס", "מה זה פורנו", "איך עושים יחסי מין", "תמונות עירום", "והסקס", "סרט כחול",
    )
    private val allowed = listOf(
        "chicken breast recipe", "a recipe for chicken breasts", "I live in Essex", "what is the weather", "tell me about Shabbat",
        "how do I make challah", "יש לי שאנס", "מתכון לעוף", "מה זה יצר הרע", "explain the parsha of this week",
        "grape juice for kiddush", "rapeseed oil or olive oil?", "set an alarm for 7", "תאווה במוסר",
    )

    @Test fun blocksUnsuitable() = blocked.forEach { assertTrue("should block: $it", Guard.blocked(it)) }
    @Test fun allowsNormal() = allowed.forEach { assertFalse("should allow: $it", Guard.blocked(it)) }
}
