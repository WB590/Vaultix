package com.example.vaultix.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.suspendCancellableCoroutine

object FirebaseRepository {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun currentUser(): FirebaseUser? = auth.currentUser

    suspend fun login(email: String, password: String) {
        suspendCancellableCoroutine { continuation ->
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { continuation.resume(Unit) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
    }

    suspend fun register(email: String, password: String) {
        suspendCancellableCoroutine { continuation ->
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { continuation.resume(Unit) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
    }

    suspend fun loginWithGoogleIdToken(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
    }

    fun logout() {
        auth.signOut()
    }

    suspend fun getOrCreateUserSecurity(defaultSaltGenerator: () -> String): UserSecurityConfig {
        val uid = currentUser()?.uid ?: error("Utilisateur non connecté")
        val userRef = firestore.collection("users").document(uid)
        val snapshot = userRef.get().await()
        val existingSalt = snapshot.getString("salt")
        val salt = if (!existingSalt.isNullOrBlank()) {
            existingSalt
        } else {
            val newSalt = defaultSaltGenerator()
            userRef.set(mapOf("salt" to newSalt), com.google.firebase.firestore.SetOptions.merge()).await()
            newSalt
        }

        return UserSecurityConfig(
            salt = salt,
            masterVerifierCipher = snapshot.getString("masterVerifierCipher"),
            masterVerifierIv = snapshot.getString("masterVerifierIv")
        )
    }

    suspend fun saveMasterVerifier(cipherTextBase64: String, ivBase64: String) {
        val uid = currentUser()?.uid ?: error("Utilisateur non connecté")
        firestore.collection("users")
            .document(uid)
            .set(
                mapOf(
                    "masterVerifierCipher" to cipherTextBase64,
                    "masterVerifierIv" to ivBase64
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()
    }

    suspend fun getPasswordEntries(): List<PasswordEntry> {
        val uid = currentUser()?.uid ?: error("Utilisateur non connecté")
        val result = firestore.collection("users")
            .document(uid)
            .collection("passwords")
            .get()
            .await()

        return result.documents.mapNotNull { doc ->
            val site = doc.getString("site")
            val encryptedPassword = doc.getString("password")
            val iv = doc.getString("iv")
            if (site.isNullOrBlank() || encryptedPassword.isNullOrBlank() || iv.isNullOrBlank()) {
                null
            } else {
                PasswordEntry(
                    id = doc.id,
                    site = site,
                    encryptedPassword = encryptedPassword,
                    iv = iv
                )
            }
        }
    }

    suspend fun addPassword(site: String, encryptedPassword: String, iv: String) {
        val uid = currentUser()?.uid ?: error("Utilisateur non connecté")
        val payload = mapOf(
            "site" to site,
            "password" to encryptedPassword,
            "iv" to iv
        )
        firestore.collection("users")
            .document(uid)
            .collection("passwords")
            .add(payload)
            .await()
    }

    suspend fun deletePassword(passwordId: String) {
        val uid = currentUser()?.uid ?: error("Utilisateur non connecté")
        firestore.collection("users")
            .document(uid)
            .collection("passwords")
            .document(passwordId)
            .delete()
            .await()
    }
}

data class UserSecurityConfig(
    val salt: String,
    val masterVerifierCipher: String?,
    val masterVerifierIv: String?
)
