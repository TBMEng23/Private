package com.tbm.iptv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tbm.iptv.data.Prefs

/** תרגום ממשק: הטקסטים בקוד כתובים בעברית, L() מחזיר אנגלית כשנבחרה אנגלית */
object I18n {
    /** "he" | "en" – מצב Compose כדי שכל המסכים יתעדכנו מיד */
    var lang by mutableStateOf("he")

    val isEn get() = lang == "en"

    fun init() { lang = Prefs.str("lang", "he") }

    fun set(l: String) { lang = l; Prefs.setStr("lang", l) }

    /** מחרוזות שלא נאספו אוטומטית (תוויות של enum) */
    val extra = mapOf(
        "טלוויזיה" to "Live TV", "סרטים" to "Movies", "סדרות" to "Series", "בית" to "Home",
        "לוח שידורים" to "TV Guide", "מועדפים" to "Favorites", "הקלטות" to "Recordings",
        "מולטי-ויו" to "Multi-view", "חיפוש" to "Search",
        "הרשימה גדולה מדי לזיכרון המכשיר" to "The playlist is too large for this device's memory",
        "המדריך גדול מדי לזיכרון המכשיר" to "The TV guide is too large for this device's memory",
        "האפליקציה נסגרה בפעם הקודמת" to "The app closed unexpectedly last time",
        "לחץ \"העתק\" ושלח את הדוח כדי שאפשר יהיה לתקן." to "Tap \"Copy\" and send the report so it can be fixed.",
        "העתק" to "Copy", "הועתק" to "Copied", "סגור" to "Close",
        "שם לחיפוש (אפשר שם מקורי או tt1234567)" to "Search name (original title or tt1234567)",
        "חפש" to "Search", "מקורות פעילים: {0}" to "Active sources: {0}",
        "לא פעילים (חסר מפתח בהגדרות ← שמע וכתוביות): {0}" to "Inactive (add a key in Settings → Audio & Subtitles): {0}",
        "מחפש כתוביות…" to "Searching subtitles…", "האתר לא זמין ברשת שלך" to "Site unreachable on your network",
        "OpenSubtitles.org – ללא מפתח" to "OpenSubtitles.org – no key needed",
        "בגיבוי אין רשימת ערוצים" to "The backup has no playlist", "שחזור מגיבוי…" to "Restore from backup…",
        "טלוויזיה, סרטים וסדרות – עם כתוביות בעברית" to "Live TV, movies and series – with Hebrew subtitles",
        "כניסה עם Xtream Codes" to "Sign in with Xtream Codes", "שרת, שם משתמש וסיסמה" to "Server, username and password",
        "רשימת M3U" to "M3U playlist", "קישור או קובץ מהמכשיר" to "Link or file from this device",
        "שחזור מגיבוי" to "Restore from backup", "מחזיר חשבון, מועדפים, הגדרות ומפתחות מקובץ גיבוי" to "Brings back account, favorites, settings and keys from a backup file",
        "קריינות AI כבויה" to "AI narration off", "קריינות AI פעילה" to "AI narration on", "קריינות AI" to "AI narration",
        "קריינות תתחיל כשתיטען כתובית" to "Narration will start once a subtitle loads",
        "אין קול מותקן לשפה הזו – התקן קול עברי בהגדרות אנדרואיד ← המרת טקסט לדיבור (Google)" to "No voice installed for this language – install a Hebrew voice in Android Settings → Text-to-speech (Google)",
        "קריינות AI מהכתוביות" to "AI narration from subtitles", "להפעיל אוטומטית בסרטים וסדרות" to "Turn on automatically for movies and series",
        "מהירות דיבור: {0}×" to "Speech rate: {0}×", "עוצמת הקול המקורי בזמן הקריינות: {0}%" to "Original audio volume while narrating: {0}%",
        "הכפתור 🗣 בנגן מפעיל ומכבה. דורש קול עברי מותקן: הגדרות אנדרואיד ← שפה וקלט ← המרת טקסט לדיבור ← Google ← עברית." to "The 🗣 button in the player toggles it. Requires a Hebrew voice: Android Settings → Language & input → Text-to-speech → Google → Hebrew.",
        "סינון לפי ארצות" to "Filter by country", "מוצגות כל הארצות ({0})" to "Showing all countries ({0})", "נבחרו: {0}" to "Selected: {0}",
        "הקלד שם מדינה (ישראל, UK, France…)" to "Type a country name (Israel, UK, France…)", "לא נמצאה מדינה" to "No country found",
        "רצועה {0}" to "Track {0}", "נבחרה כתובית מובנת: {0}" to "Built-in subtitle selected: {0}",
        "כתוביות מובנות בקובץ:" to "Built-in subtitles:", "רקע שחור (מכסה כתוביות צרובות בתמונה)" to "Black box (covers burned-in subtitles)",
        "כתובית חיצונית מחליפה כתוביות מובנות בקובץ" to "External subtitle replaces built-in subtitles",
        "גובה הכתוביות" to "Subtitle height",
        "קטגוריה" to "Category", "קטגוריות – {0}" to "Categories – {0}", "חיפוש קטגוריה…" to "Search category…",
        "לא נמצאה קטגוריה" to "No category found", "הכל" to "All",
        "לא פעיל – חסר מפתח/התחברות או כבוי" to "Inactive – missing key/login or turned off",
        "מחובר, אבל לא הוחזרו תוצאות ({0} מ\"ש)" to "Connected, but no results ({0} ms)",
        "מחובר ✓ – עברית: {0}, אנגלית: {1} ({2} מ\"ש)" to "Connected ✓ – Hebrew: {0}, English: {1} ({2} ms)",
        "אין תגובה מהאתר (חריגת זמן)" to "No response from the site (timeout)",
        "האתר לא זמין ברשת שלך (DNS)" to "Site unreachable on your network (DNS)",
        "נדחה – בדוק מפתח/סיסמה ({0})" to "Rejected – check key/password ({0})",
        "חרגת ממכסת הבקשות – נסה מאוחר יותר" to "Request quota exceeded – try later",
        "🔌 בדוק חיבור לכל המקורות" to "🔌 Test all sources", "בודק…" to "Testing…", "לא פעיל" to "Inactive", "בדוק" to "Test",
        "ללא סימון = כל הארצות מוצגות." to "Nothing selected = all countries shown.", "OpenSubtitles v3 – ללא מפתח" to "OpenSubtitles v3 – no key needed",
    )
}

/** מתרגם מחרוזת. {0}, {1}… מוחלפים בארגומנטים */
fun L(s: String, vararg args: Any?): String {
    var r = if (I18n.isEn) (I18nEn.map[s] ?: I18n.extra[s] ?: s) else s
    args.forEachIndexed { i, a -> r = r.replace("{$i}", a?.toString() ?: "") }
    return r
}
