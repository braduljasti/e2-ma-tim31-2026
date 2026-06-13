package com.example.slagalica.data

import com.example.slagalica.model.FirebaseUser
import com.google.firebase.auth.EmailAuthProvider
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth = FirebaseProvider.auth
    private val db = FirebaseProvider.db

    suspend fun register(email: String, username: String, region: String, password: String) {

        val usernameDoc = db.collection(FirestoreCollections.USERNAMES)
            .document(username.lowercase())
            .get().await()
        if (usernameDoc.exists()) {
            throw IllegalStateException("Korisničko ime je već zauzeto.")
        }

        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw IllegalStateException("Greška pri kreiranju naloga.")

        result.user?.sendEmailVerification()?.await()

        val user = FirebaseUser(
            uid = uid,
            email = email,
            username = username,
            region = region,
            createdAt = System.currentTimeMillis(),
            emailVerified = false
        )
        db.collection(FirestoreCollections.USERS).document(uid).set(user).await()

        db.collection(FirestoreCollections.USERNAMES)
            .document(username.lowercase())
            .set(mapOf("email" to email, "uid" to uid)).await()
    }

    suspend fun login(identifier: String, password: String) {
        val email = if (identifier.contains("@")) {
            identifier
        } else {

            val doc = db.collection(FirestoreCollections.USERNAMES)
                .document(identifier.lowercase())
                .get().await()
            doc.getString("email")
                ?: throw IllegalStateException("Korisnik sa tim korisničkim imenom ne postoji.")
        }

        val result = auth.signInWithEmailAndPassword(email, password).await()
        val user = result.user ?: throw IllegalStateException("Neuspješna prijava.")
        user.reload().await()

        if (!user.isEmailVerified) {
            auth.signOut()
            throw IllegalStateException("Mejl nije potvrđen. Provjerite inbox i potvrdite registraciju.")
        }

        db.collection(FirestoreCollections.USERS).document(user.uid)
            .update("emailVerified", true)
    }

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        val user = auth.currentUser ?: throw IllegalStateException("Niste prijavljeni.")
        val email = user.email ?: throw IllegalStateException("Nalog nema mejl.")
        val credential = EmailAuthProvider.getCredential(email, oldPassword)
        user.reauthenticate(credential).await()
        user.updatePassword(newPassword).await()
    }

    suspend fun resendVerification() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    fun logout() = auth.signOut()

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun isEmailVerified(): Boolean = auth.currentUser?.isEmailVerified == true

    suspend fun currentUserProfile(): FirebaseUser? {
        val uid = FirebaseProvider.currentUid ?: return null
        return db.collection(FirestoreCollections.USERS).document(uid)
            .get().await().toObject(FirebaseUser::class.java)
    }
}
