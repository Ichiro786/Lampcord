package me.lampu.lampcord.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class ComponentsV2Test {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val componentsV2Payload = """
        {
          "id": "1",
          "channel_id": "2",
          "guild_id": "3",
          "content": "",
          "flags": 32768,
          "attachments": [
            {
              "id": "10",
              "filename": "cat.png",
              "size": 123,
              "url": "https://cdn.discordapp.com/attachments/2/10/cat.png",
              "proxy_url": "https://media.discordapp.net/attachments/2/10/cat.png",
              "content_type": "image/png",
              "width": 100,
              "height": 100
            }
          ],
          "components": [
            {
              "type": 17,
              "accent_color": 5793266,
              "components": [
                { "type": 10, "content": "# Hello **world**" },
                { "type": 14, "divider": true, "spacing": 2 },
                {
                  "type": 9,
                  "components": [ { "type": 10, "content": "Section text" } ],
                  "accessory": {
                    "type": 11,
                    "media": { "url": "attachment://cat.png", "attachment_id": "10" },
                    "description": "cat"
                  }
                },
                {
                  "type": 12,
                  "items": [
                    {
                      "media": { "url": "attachment://cat.png", "attachment_id": "10" },
                      "description": "cat",
                      "spoiler": false
                    }
                  ]
                },
                {
                  "type": 13,
                  "file": { "url": "attachment://cat.png", "attachment_id": "10" },
                  "name": "cat.png",
                  "size": 123
                },
                {
                  "type": 1,
                  "components": [
                    { "type": 2, "style": 5, "label": "Open", "url": "https://example.com" }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesComponentsV2Message() {
        val message = json.decodeFromString<Message>(componentsV2Payload)

        assertTrue(message.isComponentsV2)
        assertEquals(32768, message.flags)
        assertEquals(listOf(17), message.components?.map { it.type })
        assertEquals(5793266, message.components?.firstOrNull()?.accent_color)
    }

    @Test
    fun previewTextCollectsNestedTextDisplays() {
        val message = json.decodeFromString<Message>(componentsV2Payload)

        assertEquals("# Hello **world**\nSection text", message.previewText)
    }

    @Test
    fun previewTextPrefersContentWhenPresent() {
        val message = json.decodeFromString<Message>(componentsV2Payload).copy(content = "plain")

        assertEquals("plain", message.previewText)
    }

    @Test
    fun legacyMessagesAreNotComponentsV2() {
        val message = json.decodeFromString<Message>("""{"id":"1","channel_id":"2","content":"hi"}""")

        assertFalse(message.isComponentsV2)
        assertEquals("hi", message.previewText)
    }

    @Test
    fun referencedAttachmentsAreCollectedRecursively() {
        val message = json.decodeFromString<Message>(componentsV2Payload)

        val referenced = collectReferencedAttachments(message.components.orEmpty())

        assertEquals(setOf("10"), referenced)
    }

    @Test
    fun parsesMediaGalleryAndFileComponents() {
        val message = json.decodeFromString<Message>(componentsV2Payload)
        val container = assertNotNull(message.components?.firstOrNull { it.type == 17 })

        val gallery = assertNotNull(container.components?.firstOrNull { it.type == 12 })
        assertEquals("attachment://cat.png", gallery.items?.firstOrNull()?.media?.url)
        assertEquals("10", gallery.items?.firstOrNull()?.media?.attachment_id)

        val file = assertNotNull(container.components?.firstOrNull { it.type == 13 })
        assertEquals("cat.png", file.name)
        assertEquals(123, file.size)
        assertEquals("attachment://cat.png", file.file?.url)
    }

    @Test
    fun previewTextFallsBackToFileNames() {
        val message = json.decodeFromString<Message>(
            """{"id":"1","channel_id":"2","content":"","components":[{"type":13,"file":{"url":"attachment://cat.png","attachment_id":"10"},"name":"cat.png"}]}"""
        )

        assertEquals("cat.png", message.previewText)
    }

    @Test
    fun mergeAppliesComponentAndFlagUpdates() {
        val message = json.decodeFromString<Message>("""{"id":"1","channel_id":"2","content":""}""")
        val update = json.parseToJsonElement(
            """{"content":"","flags":32768,"components":[{"type":10,"content":"Updated"}]}"""
        ).let { it as kotlinx.serialization.json.JsonObject }

        val merged = message.merge(update, json)

        assertTrue(merged.isComponentsV2)
        assertEquals("Updated", merged.previewText)
    }
}
