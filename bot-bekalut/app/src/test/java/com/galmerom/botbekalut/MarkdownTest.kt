package com.galmerom.botbekalut

import com.galmerom.botbekalut.domain.Block
import com.galmerom.botbekalut.domain.ChatImage
import com.galmerom.botbekalut.domain.ChatMessage
import com.galmerom.botbekalut.domain.Inline
import com.galmerom.botbekalut.domain.blocksToPlain
import com.galmerom.botbekalut.domain.messagePlainText
import com.galmerom.botbekalut.domain.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test
    fun parsesHebrewTableLinkAndImage() {
        val source = """
            בוקר טוב **עולם**

            [פוסט](https://x.com/elonmusk/status/1)

            | מדד | ערך |
            | --- | --- |
            | ת״א 35 | 2,000 |

            ![גרף](https://example.com/a.png)
        """.trimIndent()
        val blocks = parseMarkdown(source)
        assertTrue(blocks.any { it is Block.Paragraph })
        val table = blocks.filterIsInstance<Block.Table>().single()
        assertEquals(listOf("מדד", "ערך"), table.rows.first())
        assertEquals(listOf("ת״א 35", "2,000"), table.rows[1])
        val image = blocks.filterIsInstance<Block.Image>().single()
        assertEquals("גרף", image.alt)
        assertEquals("https://example.com/a.png", image.url)
        val plain = blocksToPlain(blocks)
        assertTrue(plain.contains("פוסט"))
        assertTrue(plain.contains("https://x.com/elonmusk/status/1"))
        assertTrue(plain.contains("ת״א 35"))
    }

    @Test
    fun keepsEmojiAndAutolink() {
        val blocks = parseMarkdown("עדכון 🚀 https://example.com/news.")
        val paragraph = blocks.filterIsInstance<Block.Paragraph>().single()
        val texts = paragraph.inlines.flatMap { flatten(it) }
        assertTrue(texts.any { it.contains("🚀") })
        assertTrue(paragraph.inlines.any { it is Inline.Link && it.url == "https://example.com/news" })
    }

    @Test
    fun copyIncludesImageUrlFromField() {
        val message = ChatMessage(
            id = "1",
            botId = "ilan",
            content = "שלום",
            images = listOf(ChatImage("https://example.com/pic.png", "תמונה")),
        )
        val plain = messagePlainText(message)
        assertTrue(plain.contains("שלום"))
        assertTrue(plain.contains("https://example.com/pic.png"))
    }

    private fun flatten(inline: Inline): List<String> = when (inline) {
        is Inline.Text -> listOf(inline.value)
        is Inline.Code -> listOf(inline.value)
        Inline.LineBreak -> emptyList()
        is Inline.Style -> inline.children.flatMap { flatten(it) }
        is Inline.Link -> inline.children.flatMap { flatten(it) }
    }
}
