package com.kidstv.launcher

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SETTINGS_TILE = "@settings"

/** The home screen: profile picker and the grid of apps the active profile may open. */
class LauncherActivity : ComponentActivity() {
    companion object {
        /** Set by the guard service when a parent asks to wake the box from the phone. */
        const val EXTRA_WAKE = "wake"
    }

    private var showProfiles by mutableStateOf(false)
    private var appsVersion by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_WAKE, false)) setTurnScreenOn(true)
        setContent { Root() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_WAKE, false)) setTurnScreenOn(true)
        showProfiles = false
    }

    override fun onResume() {
        super.onResume()
        Store.currentPkg = packageName
        appsVersion++ // picks up installs and removals made while away
        if (!Store.hasPin()) startActivity(LockActivity.intent(this, LockActivity.KIND_PARENT, ""))
    }

    /** Address of the parents' web page, or null while the box has no LAN address. */
    private fun adminUrl(): String? = Apps.lanAddress()?.let { "http://$it:${WebServer.PORT}" }

    private fun askParent() = startActivity(LockActivity.intent(this, LockActivity.KIND_PARENT, ""))

    @Composable
    private fun Root() {
        val version = Store.version.intValue
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(5_000)
                now = System.currentTimeMillis()
            }
        }
        val parent = remember(version, now) { Store.parentActive() }
        val profile = remember(version) { Store.active() }
        val lock = remember(version, now) { Store.timeLock() }
        val tint = if (parent) Color(0xFF6E6E73) else parseColor(profile?.color ?: "")

        // Home is the root of the device; Back only leaves the profile picker.
        BackHandler { showProfiles = false }

        Backdrop(tint) {
            if (showProfiles) {
                ProfilePicker()
            } else {
                Column(Modifier.fillMaxSize()) {
                    TopBar(now, parent, profile)
                    when {
                        lock != null -> LockedPanel(lock)
                        else -> AppGrid(parent, profile)
                    }
                }
            }
        }
    }

    @Composable
    private fun TopBar(now: Long, parent: Boolean, profile: Profile?) {
        val date = Date(now)
        Row(
            Modifier.fillMaxWidth().padding(start = 58.dp, end = 58.dp, top = 34.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    SimpleDateFormat("HH:mm", Locale.US).format(date),
                    color = TextPrimary, fontSize = 46.sp, fontWeight = FontWeight.Light,
                )
                Text(
                    SimpleDateFormat("EEEE, d MMMM", Locale(Store.lang.value)).format(date).replaceFirstChar { it.uppercase() },
                    color = TextSecondary, fontSize = 16.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (parent) {
                    val url = remember { adminUrl() }
                    if (url != null) QrCode(url, 92.dp)
                    Column {
                        Text(S.SCAN_QR.text(), color = TextPrimary, fontSize = 15.sp)
                        Text(url ?: S.NO_NETWORK.text(), color = TextSecondary, fontSize = 14.sp)
                    }
                    if (!GuardService.running) Text(S.GUARD_OFF.text(), color = Color(0xFFFFD60A), fontSize = 14.sp)
                    Pill(S.LOCK_AGAIN.text(), onClick = { Store.endParent() })
                } else {
                    val left = Store.remainingMin()
                    if (left != null) Text(S.MINUTES_LEFT.text(left), color = TextSecondary, fontSize = 15.sp)
                    Pill(S.PARENTS.text(), onClick = { askParent() })
                }
                FocusCard(onClick = { showProfiles = true }, shape = CircleShape, focusedScale = 1.12f) {
                    Avatar(if (parent) "★" else profile?.name ?: "?", parseColor(profile?.color ?: ""), 46.dp)
                }
            }
        }
    }

    @Composable
    private fun AppGrid(parent: Boolean, profile: Profile?) {
        val ctx = LocalContext.current
        val all by produceState(emptyList<AppInfo>(), appsVersion) {
            value = withContext(Dispatchers.IO) { Apps.launchable(ctx) }
        }
        val shown = remember(all, parent, profile) {
            // Most TV firmwares list Settings as an app; add a tile only when this one does not.
            if (parent) all + listOfNotNull(AppInfo(SETTINGS_TILE, S.SETTINGS.text()).takeIf { all.none { a -> a.pkg == "com.android.tv.settings" } })
            else all.filter { it.pkg in (profile?.allowed ?: emptySet()) }
        }
        if (shown.isEmpty()) {
            if (all.isEmpty()) return // still loading
            val url = remember { adminUrl() }
            Column(
                Modifier.fillMaxSize().padding(bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    S.NO_APPS.text(),
                    color = TextSecondary, fontSize = 20.sp,
                )
                if (url != null) {
                    Spacer(Modifier.height(26.dp))
                    QrCode(url, 190.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(url, color = TextSecondary, fontSize = 16.sp)
                }
            }
            return
        }
        val first = remember { FocusRequester() }
        LaunchedEffect(shown.firstOrNull()?.pkg, parent) { runCatching { first.requestFocus() } }
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 58.dp, end = 58.dp, top = 30.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(shown, key = { _, app -> app.pkg }) { index, app ->
                AppTile(app, if (index == 0) first else null) {
                    if (app.pkg == SETTINGS_TILE) {
                        runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    } else {
                        Apps.launch(ctx, app.pkg)
                    }
                }
            }
        }
    }

    @Composable
    private fun AppTile(app: AppInfo, focusRequester: FocusRequester?, onClick: () -> Unit) {
        val ctx = LocalContext.current
        // Pair of (image, isBanner); a banner fills the tile, an icon sits on a tinted card.
        val art by produceState<Pair<Bitmap, Boolean>?>(null, app.pkg) {
            value = withContext(Dispatchers.IO) {
                if (app.pkg == SETTINGS_TILE) null
                else Apps.banner(ctx, app.pkg)?.let { it to true } ?: Apps.icon(ctx, app.pkg)?.let { it to false }
            }
        }
        var focused by remember { mutableStateOf(false) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FocusCard(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                shape = RoundedCornerShape(16.dp),
                focusedScale = 1.12f,
                focusRequester = focusRequester,
            ) { isFocused ->
                focused = isFocused
                val image = art
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF3A3A4A), Color(0xFF1E1E28)))
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        image != null && image.second -> Image(
                            image.first.asImageBitmap(), app.label,
                            Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                        )
                        image != null -> Image(image.first.asImageBitmap(), app.label, Modifier.size(64.dp))
                        app.pkg == SETTINGS_TILE -> Text("⚙", fontSize = 44.sp, color = TextPrimary)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                app.label,
                color = if (focused) TextPrimary else Color.Transparent,
                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    private fun LockedPanel(lock: Verdict.Block) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(lock.kind) { runCatching { focus.requestFocus() } }
        Column(
            Modifier.fillMaxSize().padding(bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LockArt(lock.kind, 210.dp)
            Spacer(Modifier.height(10.dp))
            Text(lock.reason, color = TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(30.dp))
            Pill(
                S.PARENT_UNLOCK.text(),
                onClick = { startActivity(LockActivity.intent(this@LauncherActivity, lock.kind, lock.reason)) },
                focusRequester = focus,
            )
        }
    }

    @Composable
    private fun ProfilePicker() {
        val profiles = remember(Store.version.intValue) { Store.profiles() }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(S.WHO_IS_WATCHING.text(), color = TextPrimary, fontSize = 38.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(44.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                profiles.forEachIndexed { i, p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
                        FocusCard(
                            onClick = {
                                // Switching between children can be PIN-gated so the younger one stays on their profile.
                                if (Store.switchLocked() && !Store.parentActive() && p.id != Store.active()?.id) {
                                    askParent()
                                } else {
                                    Store.setActive(p.id)
                                    Store.endParent()
                                    showProfiles = false
                                }
                            },
                            shape = CircleShape,
                            focusedScale = 1.15f,
                            focusRequester = if (i == 0) focus else null,
                        ) { Avatar(p.name, parseColor(p.color), 130.dp) }
                        Spacer(Modifier.height(18.dp))
                        Text(p.name, color = TextPrimary, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
