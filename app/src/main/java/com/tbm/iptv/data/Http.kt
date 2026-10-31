package com.tbm.iptv.data

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class HttpResp(val code: Int, val body: String, val headers: Headers)

object Http {
    const val UA = "TBMPlayer/1.0"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val noRedirect: OkHttpClient by lazy { client.newBuilder().followRedirects(false).build() }

    private fun fix(url: String): String {
        val u = url.trim()
        if (u.isEmpty()) throw IOException("קישור ריק")
        return when {
            u.startsWith("http://", true) || u.startsWith("https://", true) -> u
            u.startsWith("//") -> "https:$u"
            u.startsWith("/") || u.startsWith("javascript", true) -> throw IOException("קישור לא תקין: ${u.take(60)}")
            else -> "https://$u"
        }
    }

    private fun req(url: String, headers: Map<String, String>): Request.Builder {
        val b = Request.Builder().url(fix(url))
        if (headers.keys.none { it.equals("User-Agent", true) }) b.header("User-Agent", UA)
        headers.forEach { (k, v) -> b.header(k, v) }
        return b
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        client.newCall(req(url, headers).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string() ?: ""
        }

    fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray =
        client.newCall(req(url, headers).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.bytes() ?: ByteArray(0)
        }

    fun download(url: String, target: File) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        client.newCall(req(url, emptyMap()).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("תגובה ריקה")
            body.byteStream().use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
        }
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }

    fun post(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
        followRedirects: Boolean = true,
    ): HttpResp {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val c = if (followRedirects) client else noRedirect
        return c.newCall(req(url, headers).post(body).build()).execute().use { r ->
            HttpResp(r.code, r.body?.string() ?: "", r.headers)
        }
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
