package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "offline_conversations")
data class OfflineConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val modelId: String,
    val lastMessagePreview: String,
    val messageCount: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "offline_messages")
data class OfflineMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val text: String,
    val thinkingText: String?,
    val modelId: String,
    val attachmentType: String?,
    val attachmentName: String?,
    val attachmentUri: String?,
    val createdAtMillis: Long
)

@Dao
interface OfflineChatDao {
    @Query("SELECT * FROM offline_conversations ORDER BY updatedAtMillis DESC")
    fun observeConversations(): Flow<List<OfflineConversationEntity>>

    @Query("SELECT * FROM offline_messages WHERE conversationId = :conversationId ORDER BY createdAtMillis ASC")
    fun observeMessages(conversationId: String): Flow<List<OfflineMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: OfflineConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: OfflineMessageEntity)

    @Query("DELETE FROM offline_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM offline_conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)
}

@Database(
    entities = [OfflineConversationEntity::class, OfflineMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OfflineChatDatabase : RoomDatabase() {
    abstract fun chatDao(): OfflineChatDao

    companion object {
        @Volatile
        private var INSTANCE: OfflineChatDatabase? = null

        fun getInstance(context: Context): OfflineChatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OfflineChatDatabase::class.java,
                    "eishlar_offline_chats.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
