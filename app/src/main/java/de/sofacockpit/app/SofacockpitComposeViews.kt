package de.sofacockpit.app

import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ViewCompositionStrategy
import de.tonmaschine.android.MainViewModel
import de.tonmaschine.android.Tonmaschine
import de.sofacockpit.app.theme.*

/** Hosts the existing Tonmaschine Compose screen and its real device controls. */
@OptIn(ExperimentalMaterial3Api::class)
fun createTonmaschineComposePage(context: Context, vm: MainViewModel, openConnections: Runnable, section: Int, pack: ThemePack,
    refresh: ConnectionRefresh): ComposeView =
    ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val activity = context as ComponentActivity
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> if (granted) vm.connect() }
            val connect: () -> Unit = {
                if (Build.VERSION.SDK_INT >= 37 &&
                    activity.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch("android.permission.ACCESS_LOCAL_NETWORK")
                } else {
                    vm.connect()
                }
            }
            val refreshing by refresh.refreshing.collectAsState()
            CockpitThemeScope(pack) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { refresh.request(CockpitNavigation.SOUND) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Tonmaschine(vm, connect, section = section, themePack = pack, connectionSettings = { openConnections.run() })
                }
            }
        }
    }

/** Observe the existing ViewModel on the activity's main-thread lifecycle. */
fun observeSoundbarConnection(activity: ComponentActivity, vm: MainViewModel, update: Runnable) {
    activity.lifecycleScope.launch {
        vm.device.combine(vm.message) { _, _ -> Unit }.collect { update.run() }
    }
}

/** Compact start-screen status. Values and switch states come from soundbar reports. */
fun createSoundbarHomeComposeView(context: Context, vm: MainViewModel, pack: ThemePack): ComposeView =
    ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { SoundbarHomeCard(vm, pack) }
    }

@Composable
private fun SoundbarHomeCard(vm: MainViewModel, pack: ThemePack) {
    val t = pack.theme
    val cockpitText = Color(t.text)
    val cockpitMuted = Color(t.muted)
    val cockpitGreen = Color(t.accent)
    val state by vm.device.collectAsState()
    val active = state.connection == de.tonmaschine.android.model.Connection.CONNECTED
    CockpitThemeScope(pack) {
        Surface(
            modifier = Modifier.fillMaxWidth().cockpitPanel(pack),
            color = Color.Transparent,
            contentColor = cockpitText,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(Modifier.padding(horizontal = t.paddingDp.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SOUNDBAR",
                        modifier = Modifier.weight(1f),
                        color = cockpitMuted,
                        fontSize = (12 * t.fontScale).sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!active) {
                        val status = when (state.connection) {
                            de.tonmaschine.android.model.Connection.CONNECTING -> "Verbinde …"
                            else -> "Nicht verbunden"
                        }
                        Text(status, color = cockpitMuted, fontSize = (13 * t.fontScale).sp)
                    } else {
                        val powerColor = when (state.powerOn) {
                            true -> cockpitGreen
                            false -> cockpitText
                            null -> cockpitMuted
                        }
                        IconButton(
                            onClick = { state.powerOn?.let { vm.setPower(!it) } },
                            enabled = state.powerOn != null,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Outlined.PowerSettingsNew,
                                contentDescription = when (state.powerOn) {
                                    true -> "Soundbar ausschalten"
                                    false -> "Soundbar einschalten"
                                    null -> "Powerstatus nicht gemeldet"
                                },
                                tint = powerColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
                if (active) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeToggle("Night", state.nightTime, active) {
                            vm.setOption("b_night_time", it)
                        }
                        HomeToggle("DRC", state.drc, active) {
                            vm.setOption("b_drc", it)
                        }
                        HomeToggle("Surround", state.surround, active) {
                            vm.setOption("b_rear", it)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Audioformat", color = cockpitMuted, fontSize = (12 * t.fontScale).sp,
                            modifier = Modifier.weight(1f))
                        Text(
                            state.audioFormat.takeUnless { it == "–" } ?: "Noch nicht gemeldet",
                            color = cockpitText,
                            fontSize = (13 * t.fontScale).sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeToggle(label: String, value: Boolean?, active: Boolean, onChanged: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = (12 * LocalCockpitPack.current.theme.fontScale).sp)
        Switch(
            checked = value ?: false,
            onCheckedChange = onChanged,
            enabled = active && value != null
        )
    }
}
