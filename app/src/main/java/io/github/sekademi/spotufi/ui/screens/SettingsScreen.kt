package io.github.sekademi.spotufi.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import io.github.sekademi.spotufi.BuildConfig
import io.github.sekademi.spotufi.data.BatteryOptimizationHelper
import io.github.sekademi.spotufi.data.preferences.CROSSFADE_MAX_MS
import io.github.sekademi.spotufi.data.preferences.StreamQuality
import io.github.sekademi.spotufi.data.preferences.getCellularQuality
import io.github.sekademi.spotufi.data.preferences.getCrossfadeMs
import io.github.sekademi.spotufi.data.preferences.setCrossfadeMs
import io.github.sekademi.spotufi.data.preferences.getDownloadQuality
import io.github.sekademi.spotufi.data.preferences.isVideoFallbackEnabled
import io.github.sekademi.spotufi.data.preferences.getWifiQuality
import io.github.sekademi.spotufi.data.preferences.setCellularQuality
import io.github.sekademi.spotufi.data.preferences.setDownloadQuality
import io.github.sekademi.spotufi.data.preferences.setVideoFallbackEnabled
import io.github.sekademi.spotufi.data.preferences.setWifiQuality
import io.github.sekademi.spotufi.data.update.UpdateChecker
import io.github.sekademi.spotufi.data.preferences.getUpdateRepoUrl
import io.github.sekademi.spotufi.data.preferences.setUpdateRepoUrl
import io.github.sekademi.spotufi.data.preferences.resetUpdateRepoUrl
import io.github.sekademi.spotufi.data.preferences.DEFAULT_UPDATE_REPO_URL
import io.github.sekademi.spotufi.data.preferences.getDownloadFolderName
import io.github.sekademi.spotufi.data.preferences.setDownloadFolderName
import io.github.sekademi.spotufi.data.preferences.resetDownloadFolderName
import io.github.sekademi.spotufi.data.preferences.DEFAULT_DOWNLOAD_FOLDER
import io.github.sekademi.spotufi.ui.theme.AppBackground
import io.github.sekademi.spotufi.ui.theme.AppPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var wifiQ by remember { mutableStateOf(getWifiQuality(context)) }
    var cellQ by remember { mutableStateOf(getCellularQuality(context)) }
    var dlQ by remember { mutableStateOf(getDownloadQuality(context)) }
    var crossfadeMs by remember { mutableStateOf(getCrossfadeMs(context).toFloat()) }
    var videoFallback by remember { mutableStateOf(isVideoFallbackEnabled(context)) }
    var batteryOptExempt by remember { mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimization(context)) }
    var updateRepoUrl by remember { mutableStateOf(getUpdateRepoUrl(context)) }
    var downloadFolderName by remember { mutableStateOf(getDownloadFolderName(context)) }

    val batteryOptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        batteryOptExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimization(context)
    }

    // Manual update check state
    var updateCheckState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(26.dp)
                            .clickable { navController.popBackStack() }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                // Clear the bottom nav + mini player so the last section
                // (account / log out) isn't hidden under the bar.
                .padding(bottom = 200.dp)
        ) {
            SectionTitle("Background playback")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        batteryOptLauncher.launch(BatteryOptimizationHelper.buildAppSettingsIntent(context))
                    }
                    .background(Color(0xFF1A1A20))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Battery optimization", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (batteryOptExempt) "Exempt — app won't be killed" else "Not exempt — tap to change",
                        color = if (batteryOptExempt) Color(0xFF81C784) else Color(0xFFB3B3B3),
                        fontSize = 12.sp,
                    )
                }
                if (batteryOptExempt) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Enabled",
                        tint = AppPalette,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            BatteryOptimizationHelper.getManufacturerTips()?.let { (name, tip) ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tip for $name",
                    color = Color(0xFFB3B3B3),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = tip,
                    color = Color(0xFF808080),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1A20))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Audio quality")
            QualityPicker(
                title = "Streaming over Wi-Fi",
                selected = wifiQ,
            ) { wifiQ = it; setWifiQuality(context, it) }

            QualityPicker(
                title = "Streaming over cellular",
                selected = cellQ,
            ) { cellQ = it; setCellularQuality(context, it) }

            QualityPicker(
                title = "Download quality",
                selected = dlQ,
            ) { dlQ = it; setDownloadQuality(context, it) }

            // Live lossless-server status (spotbye). Lossless only resolves when a
            // server is up; otherwise playback goes straight to YouTube.
            var losslessStatus by remember { mutableStateOf("Lossless servers: checking\u2026") }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                val up = try { com.metrolist.spotify.SpotiFlac.upLosslessProviders() } catch (_: Exception) { null }
                losslessStatus = when {
                    up == null -> "Lossless servers: status unavailable"
                    up.isEmpty() -> "Lossless servers: 0/3 up \u2014 streaming (YouTube)"
                    else -> "Lossless servers: ${up.size}/3 up (${up.sorted().joinToString(", ")})"
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(
                    losslessStatus,
                    color = Color(0xFFB3B3B3),
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Matching")
            SettingsSwitchRow(
                title = "Allow video fallback",
                subtitle = "Use regular YouTube videos only after Music song results fail",
                checked = videoFallback,
            ) {
                videoFallback = it
                setVideoFallbackEnabled(context, it)
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Crossfade")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Crossfade", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (crossfadeMs <= 0f) "Off" else "${(crossfadeMs / 1000f).let { String.format("%.0f", it) }}s",
                    color = if (crossfadeMs <= 0f) Color(0xFFB3B3B3) else AppPalette,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Blend the end of a song into the start of the next",
                color = Color(0xFFB3B3B3),
                fontSize = 13.sp,
            )
            Slider(
                value = crossfadeMs,
                onValueChange = { crossfadeMs = it },
                onValueChangeFinished = { setCrossfadeMs(context, crossfadeMs.toInt()) },
                valueRange = 0f..CROSSFADE_MAX_MS.toFloat(),
                steps = (CROSSFADE_MAX_MS / 1000) - 1, // 1-second stops
                colors = SliderDefaults.colors(
                    thumbColor = AppPalette,
                    activeTrackColor = AppPalette,
                    inactiveTrackColor = Color(0xFF333333),
                ),
            )
            Spacer(Modifier.height(12.dp))
            SectionTitle("Updates")
            Text(
                "Update source repository",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "GitHub repo URL used to check for new versions",
                color = Color(0xFFB3B3B3),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = updateRepoUrl,
                onValueChange = {
                    updateRepoUrl = it
                    setUpdateRepoUrl(context, it)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AppPalette,
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor = AppPalette,
                    focusedPlaceholderColor = Color(0xFF666666),
                    unfocusedPlaceholderColor = Color(0xFF666666),
                ),
                placeholder = { Text("https://github.com/Owner/Repo") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reset to default",
                        tint = if (updateRepoUrl != DEFAULT_UPDATE_REPO_URL) AppPalette else Color(0xFF444444),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable {
                                updateRepoUrl = DEFAULT_UPDATE_REPO_URL
                                resetUpdateRepoUrl(context)
                            }
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Download folder name",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Subfolder inside Music/ for exported downloads",
                color = Color(0xFFB3B3B3),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = downloadFolderName,
                onValueChange = {
                    downloadFolderName = it
                    setDownloadFolderName(context, it)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AppPalette,
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor = AppPalette,
                    focusedPlaceholderColor = Color(0xFF666666),
                    unfocusedPlaceholderColor = Color(0xFF666666),
                ),
                placeholder = { Text(DEFAULT_DOWNLOAD_FOLDER) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reset to default",
                        tint = if (downloadFolderName != DEFAULT_DOWNLOAD_FOLDER) AppPalette else Color(0xFF444444),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable {
                                downloadFolderName = DEFAULT_DOWNLOAD_FOLDER
                                resetDownloadFolderName(context)
                            }
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            SectionTitle("Account")
            Text(
                text = "Log out",
                color = Color(0xFFE57373),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        io.github.sekademi.spotufi.data.api.SpotifySession.setSpDc(context, "")
                        io.github.sekademi.spotufi.data.api.Api.HomeCache.clear()
                        navController.navigate(io.github.sekademi.spotufi.ui.navigation.Routes.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    .padding(vertical = 14.dp)
            )
            Spacer(Modifier.height(12.dp))
            SectionTitle("About")

            // Version row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateRepoUrl))
                        context.startActivity(intent)
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Version",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Spotufi ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        color = Color(0xFFB3B3B3),
                        fontSize = 12.sp,
                    )
                }
            }

            // Check for updates row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = updateCheckState !is UpdateCheckState.Checking) {
                        scope.launch {
                            updateCheckState = UpdateCheckState.Checking
                            val info = UpdateChecker.check(context)
                            updateCheckState = when {
                                info != null -> UpdateCheckState.UpdateAvailable(info)
                                else         -> UpdateCheckState.UpToDate
                            }
                        }
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Check for updates",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when (val s = updateCheckState) {
                            is UpdateCheckState.Idle             -> "Tap to check for a new version"
                            is UpdateCheckState.Checking         -> "Checking…"
                            is UpdateCheckState.UpToDate         -> "You're up to date ✓"
                            is UpdateCheckState.UpdateAvailable  -> "Spotufi ${s.info.version} is available!"
                        },
                        color = when (updateCheckState) {
                            is UpdateCheckState.UpdateAvailable -> AppPalette
                            is UpdateCheckState.UpToDate        -> Color(0xFF4CAF50)
                            else                                -> Color(0xFFB3B3B3)
                        },
                        fontSize = 12.sp,
                    )
                }
                when (val s = updateCheckState) {
                    is UpdateCheckState.Checking -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = AppPalette,
                    )
                    is UpdateCheckState.UpdateAvailable -> Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Update available",
                        tint = AppPalette,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(s.info.downloadUrl))
                                    )
                                }
                            },
                    )
                    else -> Unit
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────
// State machine for the manual update-check button
// ─────────────────────────────────────────────────────────
private sealed interface UpdateCheckState {
    object Idle   : UpdateCheckState
    object Checking : UpdateCheckState
    object UpToDate : UpdateCheckState
    data class UpdateAvailable(val info: UpdateChecker.UpdateInfo) : UpdateCheckState
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = AppPalette,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFFB3B3B3), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppPalette,
                uncheckedThumbColor = Color(0xFFB3B3B3),
                uncheckedTrackColor = Color(0xFF333333),
            ),
        )
    }
}

@Composable
private fun QualityPicker(
    title: String,
    selected: StreamQuality,
    onSelect: (StreamQuality) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        StreamQuality.values().forEach { q ->
            val isSel = q == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(q) }
                    .background(if (isSel) Color(0xFF1A1A20) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(q.label, color = Color.White, fontSize = 15.sp)
                    Text(q.detail, color = Color(0xFFB3B3B3), fontSize = 12.sp)
                }
                if (isSel) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = AppPalette,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
