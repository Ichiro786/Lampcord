package me.lampu.lampcord.shared.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    /**
     * Logs a message without clobbering what the logger already knows about it.
     *
     * REPLACE used to overwrite `oldContent` and `isDeleted` whenever history was re-fetched
     * after a restart, wiping the edit/delete record the logger exists to keep. An existing
     * row only has its payload refreshed when its content is unchanged.
     */
    @Transaction
    suspend fun insert(message: MessageEntity) {
        if (insertIgnore(message) != -1L) return
        val existing = getMessageById(message.id) ?: return
        if (existing.content == message.content && !existing.isDeleted) {
            update(existing.copy(jsonPayload = message.jsonPayload))
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM logged_messages WHERE channelId = :channelId ORDER BY timestamp DESC LIMIT 500")
    suspend fun getMessagesForChannel(channelId: String): List<MessageEntity>

    @Query("SELECT * FROM logged_messages WHERE id = :id")
    suspend fun getMessageById(id: String): MessageEntity?

    /** Read-modify-write for an edit; the lookup and update must share a transaction. */
    @Transaction
    suspend fun applyEdit(
        id: String,
        content: String,
        jsonPayload: String
    ) {
        val existing = getMessageById(id) ?: return
        if (existing.content == content) return
        update(
            existing.copy(
                content = content,
                oldContent = existing.content,
                jsonPayload = jsonPayload
            )
        )
    }

    @Query("UPDATE logged_messages SET isDeleted = 1 WHERE id = :id")
    suspend fun markDeleted(id: String)

    @Query("DELETE FROM logged_messages")
    suspend fun clearAll()

    /**
     * Drops plain messages (never edited, never deleted) older than [cutoffIso]. Those carry no
     * information the logger needs, and without pruning the table grew forever.
     */
    @Query("DELETE FROM logged_messages WHERE isDeleted = 0 AND oldContent IS NULL AND timestamp < :cutoffIso")
    suspend fun pruneUnremarkable(cutoffIso: String)
}
