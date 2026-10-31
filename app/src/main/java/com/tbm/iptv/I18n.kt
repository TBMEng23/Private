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
    )
}

/** מתרגם מחרוזת. {0}, {1}… מוחלפים בארגומנטים */
fun L(s: String, vararg args: Any?): String {
    var r = if (I18n.isEn) (I18nEn.map[s] ?: I18n.extra[s] ?: s) else s
    args.forEachIndexed { i, a -> r = r.replace("{$i}", a?.toString() ?: "") }
    return r
}
