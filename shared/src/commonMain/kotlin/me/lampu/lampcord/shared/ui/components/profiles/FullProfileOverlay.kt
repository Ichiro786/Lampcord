package me.lampu.lampcord.shared.ui.components.profiles

import androidx.compose.runtime.Composable
import me.lampu.lampcord.shared.state.ProfileStore

@Composable
expect fun FullProfileOverlay(profileStore: ProfileStore)
