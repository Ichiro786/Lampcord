package me.lampu.lampcord.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import me.lampu.lampcord.shared.settings.Settings
import me.lampu.lampcord.shared.utils.ResourceLoader

class EmojiTest {
    @Test
    fun twemojiVariationSelectorsMatchAssetNames() {
        assertEquals("$TWEMOJI_CDN_BASE_URL/2764.png", "❤️".toTwemojiUrl())
        assertEquals("$TWEMOJI_CDN_BASE_URL/1f636-200d-1f32b-fe0f.png", "😶‍🌫️".toTwemojiUrl())
        assertEquals("$TWEMOJI_CDN_BASE_URL/1f441-200d-1f5e8.png", "👁️‍🗨️".toTwemojiUrl())
    }

    @Test
    fun bundledTwemojiIsAvailable() {
        val bytes = assertNotNull(ResourceLoader.readBytes("twemoji/72x72/1f600.png"))
        assertEquals("PNG", bytes.copyOfRange(1, 4).decodeToString())
    }

    @Test
    fun customEmojiUrlUsesGifForAnimatedAndWebpForStatic() {

        assertEquals(
            "https://cdn.discordapp.com/emojis/123.gif?size=64&quality=lossless",
            customEmojiCdnUrl("123", animated = true)
        )
        assertEquals(
            "https://cdn.discordapp.com/emojis/123.webp?size=64&quality=lossless",
            customEmojiCdnUrl("123", animated = false)
        )
        assertEquals(
            "https://cdn.discordapp.com/emojis/123.webp?size=32&quality=lossless",
            customEmojiCdnUrl("123", animated = false, size = 32)
        )
    }

    @Test
    fun displayUrlPrefersGifOnlyWhenAnimated() {
        val settings = Settings.shared
        val originalReduceMotion = settings.reduceMotion
        val animatedEmoji = Emoji(id = "123", name = "party", animated = true)
        val staticUrl = "https://cdn.discordapp.com/emojis/123.webp?size=64&quality=lossless"
        try {
            settings.reduceMotion = false
            assertEquals(
                "https://cdn.discordapp.com/emojis/123.gif?size=64&quality=lossless",
                animatedEmoji.getDisplayUrl()
            )
            assertEquals(staticUrl, Emoji(id = "123", name = "smile").getDisplayUrl())
            settings.reduceMotion = true
            assertEquals(staticUrl, animatedEmoji.getDisplayUrl())
        } finally {
            settings.reduceMotion = originalReduceMotion
        }
    }

    @Test
    fun sizeIsSnappedToAPowerOfTwoBecauseTheCdnRejectsAnythingElse() {

        assertEquals(
            "https://cdn.discordapp.com/emojis/123.webp?size=64&quality=lossless",
            customEmojiCdnUrl("123", animated = false, size = 44)
        )
        assertEquals(
            "https://cdn.discordapp.com/emojis/123.webp?size=128&quality=lossless",
            customEmojiCdnUrl("123", animated = false, size = 89)
        )
        assertEquals(
            "https://cdn.discordapp.com/emojis/123.webp?size=16&quality=lossless",
            customEmojiCdnUrl("123", animated = false, size = 1)
        )
        assertEquals(
            "https://cdn.discordapp.com/emojis/123.webp?size=32&quality=lossless",
            customEmojiCdnUrl("123", animated = false, size = 32)
        )
    }
}
