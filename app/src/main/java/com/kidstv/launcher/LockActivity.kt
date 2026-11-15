package com.kidstv.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full-screen gate shown over blocked apps, and the parent PIN prompt. */
class LockActivity : ComponentActivity() {
    companion object {
        private const val KIND = "kind"
        private const val REASON = "reason"
        const val KIND_PARENT = "parent"

        fun intent(ctx: Context, kind: String, reason: String): Intent =
            Intent(ctx, LockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(KIND, kind)
                .putExtra(REASON, reason)
    }

    private var kind by mutableStateOf(KIND_PARENT)
    private var reason by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        read(intent)
        setContent { Backdrop(Color(0xFF3A4A8C)) { Screen() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    /** Leaves to the home screen, so the blocked app underneath is never revealed. */
    private fun goHome() {
        startActivity(Intent(this, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun read(intent: Intent) {
        kind = intent.getStringExtra(KIND) ?: KIND_PARENT
        reason = intent.getStringExtra(REASON) ?: ""
    }

    @Composable
    private fun Screen() {
        // "info" -> "pin" -> "choices"; first run starts at "setup".
        var stage by remember(kind) {
            mutableStateOf(
                when {
                    !Store.hasPin() -> "setup"
                    kind == KIND_PARENT -> "pin"
                    else -> "info"
                }
            )
        }
        var firstPin by remember { mutableStateOf<String?>(null) }

        // Closes by itself when a parent lifts the lock from the phone.
        val version = Store.version.intValue
        LaunchedEffect(version, kind) {
            val timeKind = kind == "paused" || kind == "bedtime" || kind == "quota"
            if (timeKind && Store.timeLock() == null) goHome()
        }
        BackHandler { goHome() }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (stage) {
                "setup" -> PinPad(if (firstPin == null) S.PIN_CREATE.text() else S.PIN_REPEAT.text()) { pin ->
                    val earlier = firstPin
                    when {
                        !Store.validPinFormat(pin) -> S.PIN_FORMAT.text()
                        earlier == null -> { firstPin = pin; null }
                        earlier != pin -> { firstPin = null; S.PIN_MISMATCH.text() }
                        else -> { Store.setPin(pin); Store.startParent(); finish(); null }
                    }
                }

                "pin" -> PinPad(S.PIN_ENTER.text()) { pin ->
                    if (Store.checkPin(pin)) {
                        if (kind == "paused" || kind == "bedtime" || kind == "quota") {
                            stage = "choices"
                        } else {
                            Store.startParent()
                            finish()
                        }
                        null
                    } else {
                        val wait = Store.pinLockoutSec()
                        if (wait > 0) S.PIN_LOCKOUT.text(wait) else S.PIN_WRONG.text()
                    }
                }

                "choices" -> Choices()

                else -> Info(onUnlock = { stage = "pin" })
            }
        }
    }

    @Composable
    private fun Info(onUnlock: () -> Unit) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LockArt(kind, 210.dp)
            Spacer(Modifier.height(10.dp))
            Text(reason, color = TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(
                when (kind) {
                    "bedtime" -> S.HINT_BEDTIME.text()
                    "quota" -> S.HINT_QUOTA.text()
                    "paused" -> S.HINT_PAUSED.text()
                    else -> S.HINT_APP.text()
                },
                color = TextSecondary,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Pill(S.GO_HOME.text(), onClick = { goHome() }, focusRequester = focus)
                Pill(S.PARENT_UNLOCK.text(), onClick = onUnlock)
            }
        }
    }

    @Composable
    private fun Choices() {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(S.WHAT_NOW.text(), color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (kind == "paused") {
                    Pill(S.RESUME.text(), onClick = { Store.setPaused(false); goHome() }, focusRequester = focus)
                } else {
                    listOf(15, 30, 60).forEachIndexed { i, minutes ->
                        Pill(
                            S.ADD_MINUTES.text(minutes),
                            onClick = { Store.grantExtra(minutes); goHome() },
                            focusRequester = if (i == 0) focus else null,
                        )
                    }
                }
                Pill(S.PARENT_MODE.text(), onClick = { Store.startParent(); goHome() })
            }
        }
    }
}
