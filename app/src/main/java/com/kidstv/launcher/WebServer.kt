package com.kidstv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/** Parent web admin, served on the home network. Every API call except login needs a token. */
class WebServer(private val ctx: Context) : NanoHTTPD(PORT) {
    companion object {
        const val PORT = 8765
        private const val MAX_BODY_BYTES = 256 * 1024
    }

    private val iconCache = ConcurrentHashMap<String, ByteArray>()

    override fun serve(session: IHTTPSession): Response = try {
        route(session)
    } catch (e: Exception) {
        json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", e.message ?: "error"))
    }

    private fun route(s: IHTTPSession): Response {
        val uri = s.uri
        if (s.method == Method.GET && (uri == "/" || uri == "/index.html")) return asset("web/index.html", "text/html; charset=utf-8")
        if (s.method == Method.GET && uri == "/manifest.json") return asset("web/manifest.json", "application/manifest+json")
        if (s.method == Method.GET && uri == "/app-icon.png") return png(appIcon())
        if (!uri.startsWith("/api/")) return json(Response.Status.NOT_FOUND, JSONObject().put("error", "not found"))

        if (uri == "/api/status") {
            return ok(JSONObject().put("hasPin", Store.hasPin()).put("lockoutSec", Store.pinLockoutSec()).put("lang", Store.lang.value))
        }
        if (uri == "/api/login" && s.method == Method.POST) {
            val pin = body(s).optString("pin")
            return if (Store.checkPin(pin)) ok(JSONObject().put("token", Store.newToken()))
            else json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "wrong pin").put("lockoutSec", Store.pinLockoutSec()))
        }

        // Icons are loaded by <img> tags, which cannot send headers, so the token may come as a query parameter.
        val token = s.headers["x-token"] ?: s.parameters["token"]?.firstOrNull()
        if (!Store.validToken(token)) return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "unauthorized"))

        return when {
            uri == "/api/state" && s.method == Method.GET -> ok(state())
            uri == "/api/icon" && s.method == Method.GET -> icon(s.parameters["pkg"]?.firstOrNull() ?: "")
            uri == "/api/policy" && s.method == Method.POST -> {
                Store.updateFromJson(body(s))
                ok(state())
            }
            uri == "/api/action" && s.method == Method.POST -> action(body(s))
            else -> json(Response.Status.NOT_FOUND, JSONObject().put("error", "not found"))
        }
    }

    private fun action(b: JSONObject): Response {
        Log.i("KidsTV", "web action ${b.optString("type")}")
        when (b.optString("type")) {
            "pause" -> Store.setPaused(true)
            "resume" -> Store.setPaused(false)
            "lock" -> Store.endParent()
            "wake" -> GuardService.instance?.wake() ?: return bad(S.WEB_GUARD_OFF.text())
            "close" -> GuardService.instance?.closeFront() ?: return bad(S.WEB_GUARD_OFF.text())
            "home" -> if (GuardService.instance?.goHome() != true) return bad(S.WEB_GUARD_OFF.text())
            "sleep" -> Store.setSleep(b.optInt("minutes").coerceIn(0, 480))
            "youtube" -> {
                val id = youtubeId(b.optString("url")) ?: return bad(S.WEB_BAD_YOUTUBE.text())
                val verdict = Store.decide(GuardService.YOUTUBE_PKG)
                if (verdict is Verdict.Block) return bad(S.WEB_YOUTUBE_LOCKED.text(verdict.reason))
                if (GuardService.instance?.openYoutubeVideo(id) != true) return bad(S.WEB_YOUTUBE_FAILED.text())
            }
            "launch" -> {
                val pkg = b.optString("pkg")
                val verdict = Store.decide(pkg)
                if (verdict is Verdict.Block) return bad(S.WEB_APP_LOCKED.text(verdict.reason))
                if (GuardService.instance?.open(pkg) != true) return bad(S.WEB_APP_FAILED.text())
            }
            "extra" -> Store.grantExtra(b.optInt("minutes").coerceIn(0, 240))
            "lang" -> if (!Store.setLang(b.optString("lang"))) return bad("unknown language")
            "profile" -> if (!Store.setActive(b.optString("id"))) return bad("unknown profile")
            "pin" -> {
                // A phone left logged in must not be enough to take over the PIN.
                if (!Store.checkPin(b.optString("current"))) {
                    val wait = Store.pinLockoutSec()
                    val msg = if (wait > 0) S.PIN_LOCKOUT.text(wait) else S.WEB_PIN_CURRENT_WRONG.text()
                    return json(Response.Status.FORBIDDEN, JSONObject().put("error", msg))
                }
                if (!Store.setPin(b.optString("pin"))) return bad(S.WEB_PIN_FORMAT.text())
            }
            else -> return bad("unknown action")
        }
        // Pause, profile and time changes must hit the app on screen now, not at the next tick.
        GuardService.instance?.recheck()
        // A pin change revokes every token, including the caller's; hand back a fresh one.
        val result = state()
        if (b.optString("type") == "pin") result.put("token", Store.newToken())
        return ok(result)
    }

    /** Extracts the 11-character video id from a pasted link, so only a rebuilt YouTube URL ever reaches the TV. */
    private fun youtubeId(input: String): String? {
        val text = input.trim()
        if (Regex("^[A-Za-z0-9_-]{11}$").matches(text)) return text
        if (!Regex("^(https?://)?([a-z]+\\.)?(youtube\\.com|youtu\\.be)/", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        return Regex("(?:[?&]v=|youtu\\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])").find(text)?.groupValues?.get(1)
    }

    private fun state(): JSONObject {
        val apps = JSONArray()
        Apps.launchable(ctx).forEach { apps.put(JSONObject().put("pkg", it.pkg).put("label", it.label)) }
        return Store.stateJson().put("apps", apps)
    }

    /**
     * Reads the JSON request body as UTF-8. NanoHTTPD's own parseBody decodes it as
     * US-ASCII unless the client names a charset, which corrupts Vietnamese text.
     */
    private fun body(s: IHTTPSession): JSONObject {
        val length = (s.headers["content-length"]?.toIntOrNull() ?: 0).coerceIn(0, MAX_BODY_BYTES)
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = s.inputStream.read(bytes, read, length - read)
            if (n < 0) break
            read += n
        }
        return runCatching { JSONObject(String(bytes, 0, read, Charsets.UTF_8)) }.getOrDefault(JSONObject())
    }

    private fun icon(pkg: String): Response {
        val bytes = iconCache.getOrPut(pkg) {
            val bmp = Apps.icon(ctx, pkg, 96) ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", "no icon"))
            toPng(bmp)
        }
        return png(bytes)
    }

    private fun appIcon(): ByteArray = iconCache.getOrPut("@self") {
        toPng(Apps.icon(ctx, ctx.packageName, 192) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
    }

    private fun toPng(bmp: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    private fun png(bytes: ByteArray): Response =
        newFixedLengthResponse(Response.Status.OK, "image/png", ByteArrayInputStream(bytes), bytes.size.toLong())
            .apply { addHeader("Cache-Control", "max-age=86400") }

    private fun asset(path: String, mime: String): Response {
        val bytes = ctx.assets.open(path).use { it.readBytes() }
        return newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong())
    }

    private fun ok(o: JSONObject) = json(Response.Status.OK, o)
    private fun bad(msg: String) = json(Response.Status.BAD_REQUEST, JSONObject().put("error", msg))

    private fun json(status: Response.Status, o: JSONObject): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", o.toString())
            .apply { addHeader("Cache-Control", "no-store") }
}
