package com.tbm.iptv.data

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class ParsedTitle(val title: String, val year: Int?, val season: Int?, val episode: Int?)

/** מנקה שמות של זרמי IPTV ("|EN| The Matrix (1999) 4K", "Friends S02 E05") לשם נקי + שנה + עונה/פרק */
object TitleParser {
    private val se = Regex("""[Ss](\d{1,2})\s*[ ._-]?\s*[Ee][Pp]?(\d{1,3})""")
    private val xForm = Regex("""\b(\d{1,2})x(\d{2,3})\b""")
    private val yearRx = Regex("""[(\[]?\b((?:19|20)\d{2})\b[)\]]?""")
    // קידומות ספק: "EN - ", "|EN| ", "[4K]", "NF - ", "4K-AMZ - "
    private val prefix = Regex("""^\s*(?:[\[|(]\s*[\p{L}0-9+ ]{2,8}\s*[\]|)]|[A-Za-z0-9]{1,4}(?:[-+][A-Za-z0-9]{1,5})?\s*[|:–-]\s)\s*""")
    private val junk = Regex(
        """(?i)\b(4k|uhd|fhd|hd|sd|hevc|h\.?26[45]|x26[45]|1080p|720p|2160p|480p|multi|multisub|vostfr|dubbed|dub|subbed|web-?dl|webrip|bluray|brrip|hdr|hdr10|dv|aac|ac3|dd5\.?1|atmos|imax)\b"""
    )

    fun parse(raw: String): ParsedTitle {
        var s = raw
        repeat(2) { s = prefix.replace(s, "") }
        var season: Int? = null
        var ep: Int? = null
        se.find(s)?.let {
            season = it.groupValues[1].toInt(); ep = it.groupValues[2].toInt()
            s = s.substring(0, it.range.first)
        }
        if (season == null) xForm.find(s)?.let {
            season = it.groupValues[1].toInt(); ep = it.groupValues[2].toInt()
            s = s.substring(0, it.range.first)
        }
        var year: Int? = null
        yearRx.findAll(s).lastOrNull()?.let { m ->
            if (m.range.first > 0) {
                year = m.groupValues[1].toInt()
                s = s.removeRange(m.range)
            }
        }
        s = junk.replace(s, " ")
        s = s.replace(Regex("""[._]"""), " ")
            .replace(Regex("""[\[\](){}|]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd('-', '–', ':', ' ')
            .trim()
        if (s.isEmpty()) s = raw.trim()
        return ParsedTitle(s, year, season, ep)
    }
}

/** זיהוי ארץ מתוך שם קבוצה: "IL | Israel", "|UK| Sports", "FRANCE: Cinema", "ישראל" */
object Countries {
    const val OTHER = "??"

    private val codes = setOf(
        "IL", "US", "GB", "FR", "DE", "ES", "IT", "TR", "RU", "ARB", "IN", "PT", "BR", "NL", "PL", "GR",
        "RO", "CA", "AU", "BE", "CH", "AT", "SE", "NO", "DK", "FI", "IE", "MX", "CO", "CL", "PE", "VE",
        "EG", "SA", "AE", "QA", "MA", "DZ", "TN", "LB", "JO", "IQ", "IR", "PK", "BD", "CN", "JP", "KR",
        "TH", "VN", "PH", "ID", "MY", "SG", "HK", "TW", "UA", "BY", "KZ", "AZ", "AM", "GE", "HU", "CZ",
        "SK", "BG", "RS", "HR", "BA", "SI", "AL", "MK", "ME", "XK", "CY", "MT", "LU", "IS", "EE", "LV",
        "LT", "NZ", "ZA", "NG", "KE", "GH", "ET", "SO", "SN", "CM", "AF", "SY", "YE", "KW", "BH", "OM",
        "LY", "SD", "EN", "LAT", "KU", "AFR", "ASI", "EX", "EXYU",
    )

    private val names = linkedMapOf(
        "ISRAEL" to "IL", "HEBREW" to "IL", "ישראל" to "IL", "עברית" to "IL",
        "UNITED STATES" to "US", "USA" to "US", "AMERICA" to "US",
        "UNITED KINGDOM" to "GB", "ENGLAND" to "GB", "BRITAIN" to "GB",
        "FRANCE" to "FR", "FRENCH" to "FR", "GERMANY" to "DE", "DEUTSCH" to "DE", "GERMAN" to "DE",
        "SPAIN" to "ES", "SPANISH" to "ES", "ESPAÑA" to "ES", "ITALY" to "IT", "ITALIA" to "IT",
        "TURKEY" to "TR", "TURKIYE" to "TR", "TÜRKIYE" to "TR", "TURK" to "TR", "RUSSIA" to "RU",
        "ARABIC" to "ARB", "ARAB" to "ARB", "INDIA" to "IN", "HINDI" to "IN", "PORTUGAL" to "PT",
        "BRAZIL" to "BR", "BRASIL" to "BR", "NETHERLANDS" to "NL", "HOLLAND" to "NL", "DUTCH" to "NL",
        "POLAND" to "PL", "POLSKA" to "PL", "GREECE" to "GR", "ROMANIA" to "RO", "CANADA" to "CA",
        "AUSTRALIA" to "AU", "BELGIUM" to "BE", "SWITZERLAND" to "CH", "AUSTRIA" to "AT",
        "SWEDEN" to "SE", "NORWAY" to "NO", "DENMARK" to "DK", "FINLAND" to "FI", "IRELAND" to "IE",
        "MEXICO" to "MX", "LATINO" to "LAT", "LATIN" to "LAT", "UKRAINE" to "UA", "PERSIAN" to "IR",
        "IRAN" to "IR", "KURD" to "KU", "AFRICA" to "AFR", "ALBANIA" to "AL", "PAKISTAN" to "PK",
        "CHINA" to "CN", "JAPAN" to "JP", "KOREA" to "KR", "PHILIPPINES" to "PH", "THAILAND" to "TH",
        "VIETNAM" to "VN", "HUNGARY" to "HU", "CZECH" to "CZ", "BULGARIA" to "BG", "SERBIA" to "RS",
        "CROATIA" to "HR", "BOSNIA" to "BA", "EX-YU" to "EXYU", "EXYU" to "EXYU", "ENGLISH" to "EN",
    )

    private val codeRx = Regex("""^[\[|(]?\s*([A-Z]{2,4})\s*(?:[\]|):–-]|\s+[-|:–])""")
    private val cache = ConcurrentHashMap<String, String>()

    private fun norm(c: String) = when (c) {
        "UK" -> "GB"; "HE", "ISR" -> "IL"; "AR", "ARA" -> "ARB"
        "USA" -> "US"; "GER" -> "DE"; "FRA" -> "FR"; "ITA" -> "IT"; "ESP", "SPA" -> "ES"
        "TUR" -> "TR"; "POR" -> "PT"; "NED" -> "NL"; "POL" -> "PL"; "IND" -> "IN"
        else -> c
    }

    fun of(group: String): String = cache.getOrPut(group) {
        val g = group.trim().uppercase(Locale.ROOT)
        codeRx.find(g)?.let { m ->
            val c = norm(m.groupValues[1])
            if (c in codes) return@getOrPut c
        }
        for ((n, c) in names) if (g.contains(n)) return@getOrPut c
        OTHER
    }

    fun label(code: String): String = if (com.tbm.iptv.I18n.isEn) when (code) {
        OTHER -> "Other"; "ARB" -> "Arabic"; "EN" -> "English"; "LAT" -> "Latino"; "KU" -> "Kurdish"
        "AFR" -> "Africa"; "EXYU" -> "Ex-Yugoslavia"; "ASI" -> "Asia"; "EX" -> "EX"
        else -> Locale("", code).getDisplayCountry(Locale.ENGLISH).ifBlank { code }
    } else when (code) {
        OTHER -> "אחר"
        "ARB" -> "ערבית"
        "EN" -> "אנגלית"
        "LAT" -> "לטינית"
        "KU" -> "כורדית"
        "AFR" -> "אפריקה"
        "EXYU" -> "יוגוסלביה לשעבר"
        "ASI" -> "אסיה"
        "EX" -> "EX"
        else -> {
            val n = Locale("", code).getDisplayCountry(Locale("iw"))
            if (n.isBlank() || n.equals(code, true)) code else n
        }
    }
}
