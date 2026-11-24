package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.ChatConversation
import com.example.model.ChatMessage
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeConversations(userId: String): Flow<List<ChatConversation>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/conversations"
        emitAll(
            db.collection("users").document(uid).collection("conversations")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatConversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/conversations/$conversationId/messages"
        emitAll(
            db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createConversation(
        userId: String,
        title: String,
        modelId: String,
        preview: String
    ): Result<String> {
        var docPath = "users/$userId/conversations"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Cross-user write rejected." }
            val docRef = db.collection("users").document(uid)
                .collection("conversations").document()
            docPath = docRef.path
            val safeTitle = title.trim().ifBlank { "Yangi suhbat" }.take(120)
            val safeModel = modelId.trim().ifBlank { "hydrogen_pro" }.take(64)
            val safePreview = preview.trim().take(300)
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "modelId" to safeModel,
                "lastMessagePreview" to safePreview,
                "messageCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveMessageAndUpdateConversation(
        userId: String,
        conversationId: String,
        message: ChatMessage,
        newTitleIfNeeded: String? = null,
        updatedMessageCount: Long
    ): Result<String> {
        val convRef = db.collection("users").document(userId)
            .collection("conversations").document(conversationId)
        val msgRef = convRef.collection("messages").document()
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Cross-user write rejected." }
            require(conversationId.isNotBlank()) { "Conversation ID cannot be blank" }

            val safeText = message.text.take(32000)
            val safeThinking = message.thinkingText?.take(16000)?.takeIf { it.isNotBlank() }
            val safeUrls = message.groundingUrls.take(10).map { it.take(500) }
            val safePlaces = message.mapsPlaceNames.take(10).map { it.take(200) }

            val messagePayload = buildMap<String, Any> {
                put("userId", uid)
                put("role", message.role)
                put("text", safeText)
                put("modelId", message.modelId.take(64))
                safeThinking?.let { put("thinkingText", it) }
                message.attachmentType?.takeIf { it.isNotBlank() }?.let { put("attachmentType", it) }
                message.attachmentName?.takeIf { it.isNotBlank() }?.let { put("attachmentName", it.take(255)) }
                message.attachmentUri?.takeIf { it.isNotBlank() }?.let { put("attachmentUri", it.take(1024)) }
                if (safeUrls.isNotEmpty()) put("groundingUrls", safeUrls)
                if (safePlaces.isNotEmpty()) put("mapsPlaceNames", safePlaces)
                put("createdAt", FieldValue.serverTimestamp())
            }

            val convUpdatePayload = buildMap<String, Any> {
                if (!newTitleIfNeeded.isNullOrBlank()) {
                    put("title", newTitleIfNeeded.trim().take(120))
                }
                put("modelId", message.modelId.take(64))
                put("lastMessagePreview", safeText.replace("\n", " ").take(300))
                put("messageCount", updatedMessageCount.coerceAtLeast(0L))
                put("updatedAt", FieldValue.serverTimestamp())
            }

            val batch = db.batch()
            batch.set(msgRef, messagePayload)
            batch.update(convRef, convUpdatePayload)
            batch.commit().await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(userId: String, conversationId: String): Result<Unit> {
        val convRef = db.collection("users").document(userId)
            .collection("conversations").document(conversationId)
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Cross-user delete rejected." }
            val messages = convRef.collection("messages").get().await()
            if (!messages.isEmpty) {
                val batch = db.batch()
                for (doc in messages.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }
            convRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, convRef.path)
            Result.failure(e)
        }
    }
}
