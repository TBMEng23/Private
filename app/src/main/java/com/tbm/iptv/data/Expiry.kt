package com.tbm.iptv.data

/** תאריך תפוגת המנוי אצל הספק (Xtream: user_info.exp_date) */
object Expiry {
    /** ימים שנותרו (שלילי = פג), null = לא ידוע / ללא הגבלה */
    fun daysLeft(p: Playlist): Int? {
        val exp = Prefs.str("exp_${p.id}").toLongOrNull()?.takeIf { it > 0 } ?: return null
        val ms = exp * 1000 - System.currentTimeMillis()
        return Math.floorDiv(ms, 86_400_000L).toInt()
    }
}
