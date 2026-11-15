package com.kidstv.launcher

import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}

data class Profile(
    val id: String,
    var name: String,
    var color: String,
    var allowed: MutableSet<String>,
    var dailyLimitMin: Int,
    var bedStart: String,
    var bedEnd: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("color", color)
        .put("allowed", JSONArray(allowed.sorted()))
        .put("dailyLimitMin", dailyLimitMin)
        .put("bedStart", bedStart).put("bedEnd", bedEnd)

    companion object {
        fun fromJson(o: JSONObject): Profile {
            val arr = o.optJSONArray("allowed") ?: JSONArray()
            val allowed = (0 until arr.length()).map { arr.getString(it) }.toMutableSet()
            return Profile(
                id = o.getString("id"),
                name = o.optString("name").trim().take(24).ifEmpty { S.KID.text() },
                color = o.optString("color", "#5B8DEF"),
                allowed = allowed,
                dailyLimitMin = o.optInt("dailyLimitMin", 0).coerceIn(0, 24 * 60),
                bedStart = cleanTime(o.optString("bedStart", "")),
                bedEnd = cleanTime(o.optString("bedEnd", "")),
            )
        }

        private fun cleanTime(s: String): String =
            if (Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(s)) s else ""
    }
}

sealed class Verdict {
    data object Allow : Verdict()
    data class Block(val kind: String, val reason: String) : Verdict()
}

/** Single source of truth for parental policy, PIN, and usage. Thread-safe. */
object Store {
    const val PARENT_SESSION_MS = 15 * 60 * 1000L
    private const val MAX_PIN_FAILS = 5
    private const val PIN_LOCKOUT_MS = 60_000L
    private const val USAGE_DAYS_KEPT = 14

    /** Bumped on every change so Compose screens recompose. */
    val version = mutableIntStateOf(0)

    /** Language of the TV screens and the web page: "vi" or "en". Compose state, so screens follow a change at once. */
    val lang = mutableStateOf("vi")
    private val LANGS = setOf("vi", "en")

    private lateinit var app: Context
    private lateinit var policyFile: File
    private lateinit var usageFile: File

    private var pinSalt = ""
    private var pinHash = ""
    private val tokens = mutableSetOf<String>()
    private var profiles = mutableListOf<Profile>()
    private var activeId = ""
    private var switchLocked = false
    private var paused = false
    private var overrideUntil = 0L
    private var sleepAt = 0L
    private var usage = JSONObject()

    @Volatile private var parentUntil = 0L
    private var pinFails = 0
    private var pinLockedUntil = 0L
    private var usageDirty = 0

    @Volatile var currentPkg: String = ""

    @Synchronized
    fun init(context: Context) {
        app = context.applicationContext
        policyFile = File(app.filesDir, "policy.json")
        usageFile = File(app.filesDir, "usage.json")
        val o = runCatching { JSONObject(policyFile.readText()) }.getOrNull()
        // Until the parents choose, follow the language the box itself is set to.
        lang.value = o?.optString("lang")?.takeIf { it in LANGS }
            ?: if (Locale.getDefault().language == "vi") "vi" else "en"
        if (o == null) {
            profiles = mutableListOf(
                Profile("small", S.LITTLE_ONE.text(), "#FF9F0A", mutableSetOf(), 60, "19:30", "07:00"),
                Profile("big", S.BIG_KID.text(), "#30B0C7", mutableSetOf(), 90, "20:30", "06:30"),
            )
            activeId = "small"
            savePolicy()
        } else {
            pinSalt = o.optString("pinSalt")
            pinHash = o.optString("pinHash")
            o.optJSONArray("tokens")?.let { a -> for (i in 0 until a.length()) tokens += a.getString(i) }
            val arr = o.optJSONArray("profiles") ?: JSONArray()
            profiles = (0 until arr.length()).map { Profile.fromJson(arr.getJSONObject(it)) }.toMutableList()
            activeId = o.optString("active")
            switchLocked = o.optBoolean("switchLocked")
            paused = o.optBoolean("paused")
            overrideUntil = o.optLong("overrideUntil")
            sleepAt = o.optLong("sleepAt")
            if (profiles.none { it.id == activeId }) activeId = profiles.firstOrNull()?.id ?: ""
        }
        usage = runCatching { JSONObject(usageFile.readText()) }.getOrDefault(JSONObject())
    }

    @Synchronized
    fun setLang(code: String): Boolean {
        if (code !in LANGS) return false
        lang.value = code
        savePolicy()
        return true
    }

    private fun savePolicy() {
        val o = JSONObject()
            .put("pinSalt", pinSalt).put("pinHash", pinHash)
            .put("tokens", JSONArray(tokens.toList()))
            .put("profiles", JSONArray(profiles.map { it.toJson() }))
            .put("active", activeId)
            .put("switchLocked", switchLocked)
            .put("paused", paused)
            .put("overrideUntil", overrideUntil)
            .put("sleepAt", sleepAt)
            .put("lang", lang.value)
        writeAtomic(policyFile, o.toString())
        version.intValue++
    }

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }

    // ---- PIN and sessions -------------------------------------------------

    @Synchronized fun hasPin(): Boolean = pinHash.isNotEmpty()

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun randomHex(bytes: Int): String =
        ByteArray(bytes).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun validPinFormat(pin: String): Boolean = Regex("^\\d{4,8}$").matches(pin)

    @Synchronized
    fun setPin(pin: String): Boolean {
        if (!validPinFormat(pin)) return false
        pinSalt = randomHex(16)
        pinHash = hash(pinSalt, pin)
        tokens.clear()
        savePolicy()
        return true
    }

    /** Seconds left in the wrong-PIN lockout, or 0. */
    @Synchronized
    fun pinLockoutSec(): Int =
        ((pinLockedUntil - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)

    @Synchronized
    fun checkPin(pin: String): Boolean {
        if (!hasPin() || System.currentTimeMillis() < pinLockedUntil) return false
        val ok = MessageDigest.isEqual(hash(pinSalt, pin).toByteArray(), pinHash.toByteArray())
        if (ok) {
            pinFails = 0
        } else if (++pinFails >= MAX_PIN_FAILS) {
            pinFails = 0
            pinLockedUntil = System.currentTimeMillis() + PIN_LOCKOUT_MS
        }
        return ok
    }

    @Synchronized
    fun newToken(): String = randomHex(24).also {
        tokens += it
        savePolicy()
    }

    @Synchronized fun validToken(token: String?): Boolean = token != null && token in tokens

    fun parentActive(): Boolean = System.currentTimeMillis() < parentUntil

    fun startParent() {
        parentUntil = System.currentTimeMillis() + PARENT_SESSION_MS
        version.intValue++
    }

    fun endParent() {
        if (parentUntil != 0L) {
            parentUntil = 0L
            version.intValue++
        }
    }

    // ---- Policy -----------------------------------------------------------

    @Synchronized fun profiles(): List<Profile> = profiles.map { it.copy(allowed = it.allowed.toMutableSet()) }

    @Synchronized fun active(): Profile? = profiles.firstOrNull { it.id == activeId }

    @Synchronized fun switchLocked(): Boolean = switchLocked

    @Synchronized
    fun setActive(id: String): Boolean {
        if (profiles.none { it.id == id }) return false
        activeId = id
        savePolicy()
        return true
    }

    @Synchronized
    fun setPaused(value: Boolean) {
        paused = value
        savePolicy()
    }

    /** Suspends quota and bedtime (not the app whitelist) for the given minutes; 0 clears. */
    @Synchronized
    fun grantExtra(minutes: Int) {
        overrideUntil = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
        savePolicy()
    }

    /** Sleep timer: the box turns off after the given minutes; 0 cancels. */
    @Synchronized
    fun setSleep(minutes: Int) {
        sleepAt = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
        savePolicy()
    }

    /** True exactly once when the sleep timer has run out. */
    @Synchronized
    fun takeDueSleep(): Boolean {
        if (sleepAt == 0L || System.currentTimeMillis() < sleepAt) return false
        sleepAt = 0L
        savePolicy()
        return true
    }

    @Synchronized
    fun updateFromJson(o: JSONObject) {
        o.optJSONArray("profiles")?.let { arr ->
            val list = (0 until arr.length()).map { Profile.fromJson(arr.getJSONObject(it)) }
            if (list.isNotEmpty() && list.map { it.id }.toSet().size == list.size) {
                profiles = list.toMutableList()
                if (profiles.none { it.id == activeId }) activeId = profiles.first().id
            }
        }
        if (o.has("switchLocked")) switchLocked = o.getBoolean("switchLocked")
        savePolicy()
    }

    // ---- Usage ------------------------------------------------------------

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    @Synchronized
    fun addUsage(pkg: String, seconds: Int) {
        val profile = activeId.ifEmpty { return }
        val day = usage.optJSONObject(today()) ?: JSONObject().also { usage.put(today(), it) }
        val per = day.optJSONObject(profile) ?: JSONObject().also { day.put(profile, it) }
        per.put(pkg, per.optInt(pkg) + seconds)
        if (++usageDirty >= 4) flushUsage()
        version.intValue++
    }

    @Synchronized
    fun flushUsage() {
        usageDirty = 0
        val keys = usage.keys().asSequence().toList().sorted()
        keys.dropLast(USAGE_DAYS_KEPT).forEach { usage.remove(it) }
        writeAtomic(usageFile, usage.toString())
    }

    @Synchronized
    fun usedSec(profileId: String, day: String = today()): Int {
        val per = usage.optJSONObject(day)?.optJSONObject(profileId) ?: return 0
        return per.keys().asSequence().sumOf { per.optInt(it) }
    }

    /** Minutes left today for the active profile, or null when unlimited. */
    @Synchronized
    fun remainingMin(): Int? {
        val p = active() ?: return null
        if (p.dailyLimitMin <= 0) return null
        return (p.dailyLimitMin - usedSec(p.id) / 60).coerceAtLeast(0)
    }

    // ---- Decisions --------------------------------------------------------

    private fun inBedtime(p: Profile): Boolean {
        if (p.bedStart.isEmpty() || p.bedEnd.isEmpty() || p.bedStart == p.bedEnd) return false
        fun minutes(s: String) = s.substring(0, 2).toInt() * 60 + s.substring(3, 5).toInt()
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val start = minutes(p.bedStart)
        val end = minutes(p.bedEnd)
        return if (start < end) now in start until end else now >= start || now < end
    }

    /** Time-based lock for the active profile, independent of which app is open. */
    @Synchronized
    fun timeLock(): Verdict.Block? {
        if (parentActive()) return null
        if (paused) return Verdict.Block("paused", S.PAUSED.text())
        val p = active() ?: return null
        if (System.currentTimeMillis() < overrideUntil) return null
        if (inBedtime(p)) return Verdict.Block("bedtime", S.BEDTIME.text())
        if (p.dailyLimitMin > 0 && usedSec(p.id) >= p.dailyLimitMin * 60) {
            return Verdict.Block("quota", S.QUOTA.text())
        }
        return null
    }

    private fun inputMethodPackages(): Set<String> {
        val raw = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS) ?: ""
        return raw.split(':').mapNotNull { it.substringBefore('/').ifEmpty { null } }.toSet()
    }

    /** Packages that are part of the system surface and never count as "an app the child opened". */
    fun isSystemSurface(pkg: String): Boolean =
        pkg == "android" || pkg == "com.android.systemui" ||
            pkg == "com.google.android.backdrop" || pkg == "com.android.dreams.basic" ||
            pkg == "com.google.android.permissioncontroller" || pkg in inputMethodPackages()

    @Synchronized
    fun decide(pkg: String): Verdict {
        if (pkg == app.packageName || isSystemSurface(pkg) || parentActive()) return Verdict.Allow
        timeLock()?.let { return it }
        val p = active() ?: return Verdict.Block("app", S.NO_PROFILE.text())
        if (pkg !in p.allowed) return Verdict.Block("app", S.APP_LOCKED.text())
        return Verdict.Allow
    }

    // ---- Web admin state --------------------------------------------------

    @Synchronized
    fun stateJson(): JSONObject {
        val days = JSONObject()
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        repeat(7) {
            val day = fmt.format(cal.time)
            val per = JSONObject()
            profiles.forEach { per.put(it.id, usedSec(it.id, day)) }
            days.put(day, per)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return JSONObject()
            .put("profiles", JSONArray(profiles.map { it.toJson() }))
            .put("active", activeId)
            .put("switchLocked", switchLocked)
            .put("paused", paused)
            .put("overrideUntil", overrideUntil)
            .put("sleepAt", sleepAt)
            .put("now", System.currentTimeMillis())
            .put("today", today())
            .put("todayApps", usage.optJSONObject(today()) ?: JSONObject())
            .put("days", days)
            .put("current", currentPkg)
            .put("lock", timeLock()?.kind ?: "")
            .put("guardRunning", GuardService.running)
            .put("parent", parentActive())
            .put("lang", lang.value)
            .put("awake", (app.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive)
    }
}
