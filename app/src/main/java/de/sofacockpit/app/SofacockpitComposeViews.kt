package de.sofacockpit.app

import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
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

private val cockpitBackground = Color(0xFF0B0E13)
private val cockpitSurface = Color(0xFF171C24)
private val cockpitText = Color(0xFFF2F5F9)
private val cockpitMuted = Color(0xFF97A3B3)
private val cockpitBlue = Color(0xFF70BEFF)
private val cockpitGreen = Color(0xFF42D792)
private val cockpitRed = Color(0xFFEB5353)

/** Hosts the existing Tonmaschine Compose screen and its real device controls. */
class TonmaschineComposePage(context: Context, vm: MainViewModel) : ComposeView(context) {
    init {
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
            Tonmaschine(vm, connect)
        }
    }
}

/** Compact start-screen status. Values and switch states come from soundbar reports. */
class SoundbarHomeComposeView(context: Context, vm: MainViewModel) : ComposeView(context) {
    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { SoundbarHomeCard(vm) }
    }
}

@Composable
private fun SoundbarHomeCard(vm: MainViewModel) {
    val state by vm.device.collectAsState()
    val active = state.connection == de.tonmaschine.android.model.Connection.CONNECTED
    MaterialTheme(colorScheme = darkColorScheme(
        primary = cockpitBlue,
        onPrimary = Color(0xFF101B29),
        background = cockpitBackground,
        onBackground = cockpitText,
        surface = cockpitSurface,
        onSurface = cockpitText,
        onSurfaceVariant = cockpitMuted,
        secondary = cockpitBlue,
        onSecondary = Color(0xFF101B29)
    )) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = cockpitSurface,
            contentColor = cockpitText,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SOUNDBAR",
                        modifier = Modifier.weight(1f),
                        color = cockpitMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!active) {
                        val status = when (state.connection) {
                            de.tonmaschine.android.model.Connection.CONNECTING -> "Verbinde …"
                            else -> "Nicht verbunden"
                        }
                        Text(status, color = cockpitMuted, fontSize = 13.sp)
                    } else {
                        val powerColor = when (state.powerOn) {
                            true -> cockpitGreen
                            false -> cockpitRed
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
                        Text("Audioformat", color = cockpitMuted, fontSize = 12.sp,
                            modifier = Modifier.weight(1f))
                        Text(
                            state.audioFormat.takeUnless { it == "–" } ?: "Noch nicht gemeldet",
                            color = cockpitText,
                            fontSize = 13.sp,
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
        Text(label, color = cockpitText, fontSize = 12.sp)
        Switch(
            checked = value ?: false,
            onCheckedChange = onChanged,
            enabled = active && value != null
        )
    }
}
