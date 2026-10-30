package com.tbm.iptv.data

import java.io.InputStream

object M3uParser {
    private val attrRx = Regex("""([\w-]+)="([^"]*)"""")
    private val fileExt = Regex("""\.(mp4|mkv|avi|mov|wmv|m4v|flv)(\?|$)""")
    private const val NO_GROUP = "ללא קבוצה"

    private class SeriesAcc(val name: String, val logo: String?, val group: String) {
        val eps = ArrayList<Episode>()
    }

    fun parse(input: InputStream, playlistId: String): Content {
        val live = ArrayList<Channel>()
        val movies = ArrayList<Channel>()
        val series = LinkedHashMap<String, SeriesAcc>()
        var inf: String? = null
        var grp: String? = null
        var epgUrl: String? = null
        var headAttrs: Map<String, String> = emptyMap()

        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                val line = raw.trim().removePrefix("﻿")
                when {
                    line.isEmpty() -> {}
                    line.startsWith("#EXTM3U", true) -> {
                        headAttrs = attrRx.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        epgUrl = headAttrs["url-tvg"] ?: headAttrs["x-tvg-url"] ?: headAttrs["tvg-url"]
                    }
                    line.startsWith("#EXTINF", true) -> { inf = line; grp = null }
                    line.startsWith("#EXTGRP:", true) -> grp = line.substringAfter(':').trim()
                    line.startsWith("#") -> {}
                    else -> {
                        handle(inf ?: "#EXTINF:-1,${line.substringAfterLast('/')}", grp, line,
                            live, movies, series, headAttrs)
                        inf = null; grp = null
                    }
                }
            }
        }

        val seriesList = series.entries.map { (k, acc) ->
            SeriesItem(
                key = "s:$playlistId:$k",
                name = acc.name,
                logo = acc.logo,
                group = acc.group,
                episodesM3u = acc.eps.sortedWith(compareBy({ it.season }, { it.episode })),
            )
        }
        return Content(live, movies, seriesList, epgUrl)
    }

    private fun handle(
        inf: String, grp: String?, url: String,
        live: MutableList<Channel>, movies: MutableList<Channel>, series: MutableMap<String, SeriesAcc>,
        head: Map<String, String>,
    ) {
        val attrs = attrRx.findAll(inf).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val lastQuote = inf.lastIndexOf('"')
        val comma = inf.indexOf(',', if (lastQuote > 0) lastQuote else 0)
        val name = (if (comma >= 0) inf.substring(comma + 1) else "").trim()
            .ifEmpty { attrs["tvg-name"] ?: url.substringAfterLast('/') }
        val group = (attrs["group-title"] ?: grp ?: NO_GROUP).trim().ifEmpty { NO_GROUP }
        val logo = attrs["tvg-logo"]?.trim()?.ifEmpty { null }
        val lu = url.lowercase()
        val lg = group.lowercase()
        val parsed = TitleParser.parse(name)

        val kind = when {
            "/series/" in lu -> Kind.SERIES
            "/movie/" in lu -> Kind.MOVIE
            parsed.season != null && parsed.episode != null &&
                (fileExt.containsMatchIn(lu) || listOf("series", "סדר", "show").any { lg.contains(it) }) -> Kind.SERIES
            fileExt.containsMatchIn(lu) -> if (parsed.season != null) Kind.SERIES else Kind.MOVIE
            listOf("vod", "movie", "film", "סרט", "cinema").any { lg.contains(it) } && !lu.endsWith(".ts") -> Kind.MOVIE
            else -> Kind.LIVE
        }

        when (kind) {
            Kind.LIVE -> {
                val cuType = (attrs["catchup"] ?: attrs["catchup-type"] ?: head["catchup"])?.ifBlank { null }
                val cuSrc = (attrs["catchup-source"] ?: head["catchup-source"])?.ifBlank { null }
                val cuDays = (attrs["catchup-days"] ?: attrs["tvg-rec"] ?: attrs["timeshift"] ?: head["catchup-days"])
                    ?.toIntOrNull() ?: 0
                live += Channel(
                    url, name, logo, group, url, Kind.LIVE,
                    epgId = attrs["tvg-id"]?.ifBlank { null } ?: attrs["tvg-name"]?.ifBlank { null },
                    archiveDays = if (cuDays > 0) cuDays else if (cuType != null) 7 else 0,
                    catchupType = cuType ?: if (cuDays > 0) "shift" else null,
                    catchupSource = cuSrc,
                )
            }
            Kind.MOVIE -> movies += Channel(url, name, logo, group, url, Kind.MOVIE)
            Kind.SERIES -> {
                val sName = parsed.title
                val k = "$group|${sName.lowercase()}"
                val acc = series.getOrPut(k) { SeriesAcc(sName, logo, group) }
                acc.eps += Episode(
                    key = url,
                    title = name,
                    season = parsed.season ?: 1,
                    episode = parsed.episode ?: (acc.eps.size + 1),
                    url = url,
                    image = logo,
                )
            }
        }
    }
}
