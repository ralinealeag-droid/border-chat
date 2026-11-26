package com.example

import com.example.data.ChatReceiptOverride
import com.example.data.ChatRoom
import com.example.data.ChatType
import com.example.data.MessageReceiptStatus
import com.example.data.ReadReceiptPrivacySettings
import com.example.data.computeMessageReceiptStatus
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadReceiptPrivacyUnitTest {

    private val directChat = ChatRoom(
        id = "dm_alice_bob",
        type = ChatType.DIRECT.name,
        memberUids = listOf("alice", "bob")
    )

    private val groupChat = ChatRoom(
        id = "grp_team",
        type = ChatType.GROUP.name,
        memberUids = listOf("alice", "bob", "charlie")
    )

    @Test
    fun `granular privacy settings resolve global, group, and per-chat overrides accurately`() {
        val settings = ReadReceiptPrivacySettings(
            readReceiptsEnabled = false,
            groupReadReceiptsEnabled = true,
            chatReadReceiptOverrides = mapOf(
                "dm_alice_bob" to ChatReceiptOverride.ALWAYS_ON.name,
                "grp_team" to ChatReceiptOverride.ALWAYS_OFF.name
            )
        )

        // Direct chat overridden to ALWAYS_ON despite global OFF
        assertTrue(settings.isReadReceiptsEnabledFor(directChat))
        // Group chat overridden to ALWAYS_OFF despite group ON
        assertFalse(settings.isReadReceiptsEnabledFor(groupChat))

        // Un-overridden chats fall back to respective global/group defaults
        val otherDirect = directChat.copy(id = "dm_alice_dave")
        val otherGroup = groupChat.copy(id = "grp_other")
        assertFalse(settings.isReadReceiptsEnabledFor(otherDirect))
        assertTrue(settings.isReadReceiptsEnabledFor(otherGroup))
    }

    @Test
    fun `message receipt status transitions across sending, sent, delivered, and blue ticks`() {
        val ts = Timestamp(1_700_000_000L, 0)

        assertEquals(
            MessageReceiptStatus.SENDING,
            computeMessageReceiptStatus(
                createdAt = null,
                senderId = "alice",
                memberUids = listOf("alice", "bob"),
                readByUids = listOf("alice"),
                readReceiptsVisible = true,
                isAnyOtherMemberOnline = true
            )
        )

        assertEquals(
            MessageReceiptStatus.SENT,
            computeMessageReceiptStatus(
                createdAt = ts,
                senderId = "alice",
                memberUids = listOf("alice", "bob"),
                readByUids = listOf("alice"),
                readReceiptsVisible = true,
                isAnyOtherMemberOnline = false
            )
        )

        assertEquals(
            MessageReceiptStatus.DELIVERED,
            computeMessageReceiptStatus(
                createdAt = ts,
                senderId = "alice",
                memberUids = listOf("alice", "bob"),
                readByUids = listOf("alice"),
                readReceiptsVisible = true,
                isAnyOtherMemberOnline = true
            )
        )

        // Direct chat read -> READ (blue ticks)
        assertEquals(
            MessageReceiptStatus.READ,
            computeMessageReceiptStatus(
                createdAt = ts,
                senderId = "alice",
                memberUids = listOf("alice", "bob"),
                readByUids = listOf("alice", "bob"),
                readReceiptsVisible = true,
                isAnyOtherMemberOnline = false
            )
        )

        // Reciprocal privacy hides blue ticks when readReceiptsVisible is false
        assertEquals(
            MessageReceiptStatus.DELIVERED,
            computeMessageReceiptStatus(
                createdAt = ts,
                senderId = "alice",
                memberUids = listOf("alice", "bob"),
                readByUids = listOf("alice", "bob"),
                readReceiptsVisible = false,
                isAnyOtherMemberOnline = true
            )
        )

        // Group chat requires all members to read before turning blue; partial read stays DELIVERED
        assertEquals(
            MessageReceiptStatus.DELIVERED,
            computeMessageReceiptStatus(
                createdAt = ts,
                senderId = "alice",
                memberUids = listOf("alice", "bob", "charlie"),
                readByUids = listOf("alice", "bob"),
                readReceiptsVisible = true,
                isAnyOtherMemberOnline = false
            )
        )
        assertEquals(
            MessageReceiptStatus.READ,
            computeMessageReceiptStatus(
                createdAt = ts,
                senderId = "alice",
                memberUids = listOf("alice", "bob", "charlie"),
                readByUids = listOf("alice", "bob", "charlie"),
                readReceiptsVisible = true,
                isAnyOtherMemberOnline = false
            )
        )
    }
}
