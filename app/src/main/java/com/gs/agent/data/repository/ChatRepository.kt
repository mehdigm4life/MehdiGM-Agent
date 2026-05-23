package com.gs.agent.data.repository

import com.gs.agent.data.db.ChatDao
import com.gs.agent.data.db.ConversationEntity
import com.gs.agent.data.db.MessageEntity
import com.gs.agent.data.models.ChatMessage
import com.gs.agent.data.models.Role
import com.gs.agent.data.models.ToolInvocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID

class ChatRepository(private val dao: ChatDao) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeConversations(): Flow<List<ConversationEntity>> = dao.observeConversations()

    suspend fun createConversation(providerId: String, model: String, title: String = "New Chat"): ConversationEntity {
        val c = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            providerId = providerId,
            model = model
        )
        dao.upsertConversation(c)
        return c
    }

    suspend fun deleteConversation(id: String) {
        dao.deleteMessagesFor(id)
        dao.deleteConversation(id)
    }

    suspend fun renameConversation(id: String, title: String) {
        val c = dao.getConversation(id) ?: return
        dao.upsertConversation(c.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun touchConversation(id: String) {
        val c = dao.getConversation(id) ?: return
        dao.upsertConversation(c.copy(updatedAt = System.currentTimeMillis()))
    }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        dao.observeMessages(conversationId).map { list -> list.map { it.toModel() } }

    suspend fun getMessages(conversationId: String): List<ChatMessage> =
        dao.getMessages(conversationId).map { it.toModel() }

    suspend fun saveMessage(conversationId: String, message: ChatMessage) {
        dao.upsertMessage(message.toEntity(conversationId))
        touchConversation(conversationId)
    }

    private fun MessageEntity.toModel(): ChatMessage {
        val tools: List<ToolInvocation> = runCatching {
            json.decodeFromString<List<ToolInvocation>>(toolCallsJson)
        }.getOrDefault(emptyList())
        return ChatMessage(
            id = id,
            role = Role.valueOf(role),
            content = content,
            timestamp = timestamp,
            toolCalls = tools
        )
    }

    private fun ChatMessage.toEntity(convId: String): MessageEntity =
        MessageEntity(
            id = id,
            conversationId = convId,
            role = role.name,
            content = content,
            timestamp = timestamp,
            toolCallsJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ToolInvocation.serializer()), toolCalls)
        )
}
