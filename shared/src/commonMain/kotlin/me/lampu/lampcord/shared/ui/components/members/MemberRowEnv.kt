package me.lampu.lampcord.shared.ui.components.members

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import me.lampu.lampcord.shared.model.Guild
import me.lampu.lampcord.shared.state.PresenceStore

@Stable
class MemberRowEnv(
    val guild: Guild?,
    val currentUserId: String?,
    val currentUserStatus: String?,
    val developerMode: Boolean,
    val presenceStore: PresenceStore,
    val relationshipTypes: State<Map<String, Int>>,
    val animate: Boolean,
    val isTouch: Boolean,
    val loadImages: Boolean
)
