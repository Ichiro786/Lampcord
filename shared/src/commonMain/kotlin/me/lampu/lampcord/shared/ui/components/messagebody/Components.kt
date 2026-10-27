package me.lampu.lampcord.shared.ui.components.messagebody

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.lampu.lampcord.shared.model.Attachment
import me.lampu.lampcord.shared.model.MediaGalleryItem
import me.lampu.lampcord.shared.model.MessageComponent
import me.lampu.lampcord.shared.model.UnfurledMediaItem
import me.lampu.lampcord.shared.model.customEmojiCdnUrl
import me.lampu.lampcord.shared.settings.Settings
import me.lampu.lampcord.shared.state.NavigationStore
import me.lampu.lampcord.shared.ui.components.AsyncImage
import me.lampu.lampcord.shared.ui.components.DiscordMarkdownText
import me.lampu.lampcord.shared.ui.icons.Icons
import me.lampu.lampcord.shared.ui.rememberLinkOpener
import me.lampu.lampcord.shared.ui.theme.DiscordGreen
import me.lampu.lampcord.shared.ui.theme.DiscordRed
import org.koin.compose.koinInject

/**
 * Renders the `components` array of a message.
 *
 * Supports both the legacy action rows (buttons/selects) and Components V2 (containers, text
 * displays, sections, thumbnails, galleries, files and separators). Components V2 messages carry
 * their text inside components instead of `content`, so without this they appear blank.
 */
@Composable
fun MessageComponentsRow(
    components: List<MessageComponent>,
    attachments: List<Attachment> = emptyList()
) {
    Column(
        modifier = Modifier.padding(top = 4.dp).widthIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        components.forEach { MessageComponentView(it, attachments) }
    }
}

@Composable
private fun MessageComponentView(component: MessageComponent, attachments: List<Attachment>) {
    when (component.type) {
        1 -> ActionRowView(component, attachments)
        2 -> ComponentButton(component)
        3, 5, 6, 7, 8 -> SelectMenuView(component)
        9 -> SectionView(component, attachments)
        10 -> TextDisplayView(component)
        11 -> ThumbnailView(component, attachments, Modifier.size(80.dp))
        12 -> MediaGalleryView(component, attachments)
        13 -> FileView(component, attachments)
        14 -> SeparatorView(component)
        17 -> ContainerView(component, attachments)
    }
}

@Composable
private fun ActionRowView(component: MessageComponent, attachments: List<Attachment>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        component.components?.forEach { child ->
            when (child.type) {
                2 -> ComponentButton(child)
                in 3..8 -> SelectMenuView(child)
                else -> MessageComponentView(child, attachments)
            }
        }
    }
}

@Composable
private fun TextDisplayView(component: MessageComponent) {
    val content = component.content ?: return
    if (content.isBlank()) return
    DiscordMarkdownText(
        content = content,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SectionView(component: MessageComponent, attachments: List<Attachment>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            component.components?.forEach { child ->
                if (child.type == 10) TextDisplayView(child) else MessageComponentView(child, attachments)
            }
        }
        component.accessory?.let { accessory ->
            when (accessory.type) {
                2 -> ComponentButton(accessory)
                11 -> ThumbnailView(accessory, attachments, Modifier.size(80.dp))
                else -> MessageComponentView(accessory, attachments)
            }
        }
    }
}

@Composable
private fun ThumbnailView(component: MessageComponent, attachments: List<Attachment>, modifier: Modifier = Modifier) {
    val media = component.media ?: return
    val url = media.resolveUrl(attachments) ?: return
    SpoilerWrapper(
        spoiler = component.spoiler == true,
        modifier = modifier.clip(RoundedCornerShape(8.dp))
    ) {
        AsyncImage(
            model = url,
            contentDescription = component.description,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            allowAnimation = media.isAnimated
        )
    }
}

@Composable
private fun MediaGalleryView(component: MessageComponent, attachments: List<Attachment>) {
    val navigationStore: NavigationStore = koinInject()
    val galleryItems = remember(component, attachments) {
        component.items.orEmpty().mapNotNull { it.toAttachment(attachments) }
    }
    if (galleryItems.isEmpty()) return
    SpoilerWrapper(
        spoiler = component.items.orEmpty().any { it.spoiler == true },
        modifier = Modifier.fillMaxWidth()
    ) {
        MessageMosaic(
            items = galleryItems,
            onOpenItem = { index -> navigationStore.openAttachmentViewer(galleryItems, index) }
        )
    }
}

@Composable
private fun FileView(component: MessageComponent, attachments: List<Attachment>) {
    val file = component.file ?: return
    val attachment = file.toAttachment(
        attachments = attachments,
        nameOverride = component.name,
        sizeOverride = component.size,
        descriptionOverride = component.description
    ) ?: return
    FileAttachmentView(attachment)
}

@Composable
private fun SeparatorView(component: MessageComponent) {
    val spacing = if (component.spacing == 2) 12.dp else 4.dp
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = spacing / 2)) {
        if (component.divider != false) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ContainerView(component: MessageComponent, attachments: List<Attachment>) {
    val accent = component.accent_color?.let { Color(it or 0xFF000000.toInt()) }
    SpoilerWrapper(spoiler = component.spoiler == true, modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                if (accent != null) {
                    Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(accent))
                }
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    component.components?.forEach { MessageComponentView(it, attachments) }
                }
            }
        }
    }
}

@Composable
private fun ComponentButton(component: MessageComponent) {
    val isLink = component.style == 5
    val buttonColor = when (component.style) {
        1 -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        2 -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
        3 -> ButtonDefaults.buttonColors(containerColor = DiscordGreen, contentColor = Color.White)
        4 -> ButtonDefaults.buttonColors(containerColor = DiscordRed, contentColor = Color.White)
        else -> ButtonDefaults.buttonColors()
    }
    val openLink = rememberLinkOpener()
    Button(
        onClick = { if (isLink && component.url != null) openLink(component.url) },
        colors = buttonColor,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        enabled = component.disabled != true
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            component.emoji?.let { emoji ->
                val isAnimated = emoji.animated == true && !Settings.shared.reduceMotion
                val emojiUrl = emoji.id?.let { customEmojiCdnUrl(it, isAnimated, size = 64) }
                if (emojiUrl != null) {
                    AsyncImage(model = emojiUrl, contentDescription = null, allowAnimation = isAnimated, modifier = Modifier.size(16.dp))
                } else {
                    Text(emoji.name ?: "", fontSize = 14.sp)
                }
            }
            component.label?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            if (isLink) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(14.dp)) }
        }
    }
}

@Composable
fun SelectMenuView(component: MessageComponent) {
    Surface(
        modifier = Modifier.padding(vertical = 4.dp).widthIn(max = 425.dp).fillMaxWidth().height(40.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = { /* TODO */ },
        enabled = component.disabled != true
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = component.placeholder ?: "Select an option...",
                style = MaterialTheme.typography.bodyMedium,
                color = if (component.disabled == true) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(imageVector = Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SpoilerWrapper(
    spoiler: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    if (!spoiler) {
        Box(modifier = modifier, content = content)
        return
    }
    var revealed by remember { mutableStateOf(false) }
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable { revealed = true }) {
        Box(modifier = Modifier.fillMaxSize(), content = content)
        if (!revealed) {
            Box(
                modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SPOILER",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * FilePicker/theme media can reference uploaded attachments with `attachment://<filename>`.
 * Resolve those against the message's attachments; externally hosted URLs pass straight through.
 */
private fun UnfurledMediaItem.resolveUrl(attachments: List<Attachment>): String? {
    if (url.isNotBlank() && !url.startsWith("attachment://")) return url
    val existing = findAttachment(attachments)
    return existing?.url
}

private fun UnfurledMediaItem.findAttachment(attachments: List<Attachment>): Attachment? =
    attachments.firstOrNull { att ->
        (attachment_id != null && att.id == attachment_id) ||
            (url.startsWith("attachment://") && att.filename == url.removePrefix("attachment://"))
    }

private fun UnfurledMediaItem.toAttachment(
    attachments: List<Attachment>,
    nameOverride: String? = null,
    sizeOverride: Int? = null,
    descriptionOverride: String? = null
): Attachment? {
    val existing = findAttachment(attachments)
    val resolvedUrl = existing?.url
        ?: url.takeIf { it.isNotBlank() && !it.startsWith("attachment://") }
        ?: return null
    val derivedName = url.removePrefix("attachment://").takeIf {
        url.startsWith("attachment://") && it.isNotBlank()
    }
    val fileName = nameOverride
        ?: existing?.filename
        ?: derivedName
        ?: resolvedUrl.substringBefore('?').substringAfterLast('/').ifBlank { "file" }
    return Attachment(
        id = existing?.id ?: attachment_id ?: resolvedUrl.hashCode().toString(),
        filename = fileName,
        description = descriptionOverride ?: existing?.description,
        content_type = existing?.content_type ?: content_type ?: guessContentType(resolvedUrl),
        size = existing?.size ?: sizeOverride ?: 0,
        url = resolvedUrl,
        proxy_url = existing?.proxy_url ?: proxy_url ?: resolvedUrl,
        height = height ?: existing?.height,
        width = width ?: existing?.width,
        placeholder = placeholder ?: existing?.placeholder
    )
}

private fun MediaGalleryItem.toAttachment(attachments: List<Attachment>): Attachment? =
    media.toAttachment(attachments, descriptionOverride = description)

private fun guessContentType(url: String): String? =
    when (url.substringBefore('?').substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "apng" -> "image/apng"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "ogg", "oga", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        else -> null
    }
