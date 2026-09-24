package dev.kosherswitch

/**
 * A simple safety net for AI food answers: the small offline model can forget kashrut,
 * so answers that mix meat and dairy, or mention non-kosher foods, get a clear warning.
 */
object Kashrut {
    private val FOOD = Regex("""recipe|cook|bake|dish|meal|food|dinner|lunch|breakfast|מתכון|לבשל|לאפות|מנה|ארוחה|אוכל""", RegexOption.IGNORE_CASE)
    private val MEAT = Regex("""\b(?:beef|chicken|turkey|lamb|veal|meat|steak|burger|sausage|schnitzel)\b|בשר|עוף|בקר|הודו|כבש|שניצל|נקניק""", RegexOption.IGNORE_CASE)
    private val DAIRY = Regex("""(?<!dairy-free |dairy free |non-dairy |non dairy |soy |almond |oat |coconut |rice |peanut |parve |pareve )\b(?:cheese|butter|milk|cream|yogurt|parmesan|mozzarella|cheddar)\b|גבינה|גבינת|חמאה|(?<!תחליף )חלב|שמנת|יוגורט""", RegexOption.IGNORE_CASE)
    private val TREIF = Regex("""\b(?:pork|bacon|ham|shrimp|prawn|lobster|crab|clam|oyster|mussel|squid|shellfish)\b|חזיר|שרימפס|סרטן|לובסטר|פירות ים""", RegexOption.IGNORE_CASE)

    fun isFood(question: String) = FOOD.containsMatchIn(question)

    /** Added to food questions, right where a small model pays attention. */
    fun reminder(hebrew: Boolean) = if (hebrew)
        "\n(חשוב: המתכון חייב להיות כשר. אין לערב בשר וחלב. במנה בשרית השתמשו בשמן או מרגרינה ובלי גבינה, חמאה, חלב או שמנת. ציינו אם זה בשרי, חלבי או פרווה.)"
    else "\n(Important: the recipe must be kosher. Never mix meat and dairy. In a meat dish use oil or margarine and no cheese, butter, milk or cream. Say whether it is meat, dairy or pareve.)"

    /** A warning to add under the answer, or null if nothing looks wrong. */
    fun warning(answer: String, hebrew: Boolean): String? {
        val treif = TREIF.find(answer)?.value
        val mixed = MEAT.containsMatchIn(answer) && DAIRY.containsMatchIn(answer)
        if (treif == null && !mixed) return null
        val dairy = DAIRY.find(answer)?.value
        return if (hebrew) buildString {
            append("⚠️ בדיקת כשרות: ")
            if (mixed) append("המתכון מערב בשר וחלב (${dairy}). במנה בשרית השתמשו במרגרינה או שמן במקום חמאה, בתחליף חלב, והשמיטו את הגבינה. ")
            if (treif != null) append("\"$treif\" אינו כשר. ")
        } else buildString {
            append("⚠️ Kashrut check: ")
            if (mixed) append("this mixes meat and dairy ($dairy). In a meat dish use margarine or oil instead of butter, non-dairy milk instead of milk, and leave out the cheese. ")
            if (treif != null) append("\"$treif\" is not kosher. ")
        }.trim()
    }

    fun isHebrew(text: String) = text.any { it in '֐'..'׿' }
}
