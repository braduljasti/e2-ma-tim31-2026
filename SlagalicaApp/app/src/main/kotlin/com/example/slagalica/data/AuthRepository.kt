package com.example.slagalica.data

import com.example.slagalica.model.FirebaseUser
import com.google.firebase.auth.EmailAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Sloj koji enkapsulira Firebase Auth + Firestore za rad sa korisnickim nalogom.
 * Pokriva funkcionalni zahtjev 1 (Registracija i logovanje).
 *
 * Sve metode su suspend i bacaju izuzetak u slucaju greske - ViewModel ih hvata
 * i prevodi u poruke za korisnika.
 */
class AuthRepository {

    private val auth = FirebaseProvider.auth
    private val db = FirebaseProvider.db

    /**
     * 1.a + 1.b - registracija unosom mejla, korisnickog imena, regiona i lozinke,
     * uz slanje verifikacionog mejla (potvrda klikom na link).
     */
    suspend fun register(email: String, username: String, region: String, password: String) {
        // Provjera da korisnicko ime nije zauzeto
        val usernameDoc = db.collection(FirestoreCollections.USERNAMES)
            .document(username.lowercase())
            .get().await()
        if (usernameDoc.exists()) {
            throw IllegalStateException("Korisničko ime je već zauzeto.")
        }

        // Kreiranje naloga u Firebase Auth
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw IllegalStateException("Greška pri kreiranju naloga.")

        // 1.b - slanje verifikacionog mejla
        result.user?.sendEmailVerification()?.await()

        // Cuvanje profila u Firestore
        val user = FirebaseUser(
            uid = uid,
            email = email,
            username = username,
            region = region,
            createdAt = System.currentTimeMillis(),
            emailVerified = false
        )
        db.collection(FirestoreCollections.USERS).document(uid).set(user).await()
        // Mapiranje korisnicko ime -> email (da bi login po imenu mogao da nadje mejl)
        db.collection(FirestoreCollections.USERNAMES)
            .document(username.lowercase())
            .set(mapOf("email" to email, "uid" to uid)).await()
    }

    /**
     * 1.c + 1.d - login unosom mejla ILI korisnickog imena i lozinke.
     * Tek nakon potvrde mejla (1.c) dozvoljavamo ulazak u aplikaciju.
     */
    suspend fun login(identifier: String, password: String) {
        val email = if (identifier.contains("@")) {
            identifier
        } else {
            // korisnicko ime -> trazimo email u mapiranju
            val doc = db.collection(FirestoreCollections.USERNAMES)
                .document(identifier.lowercase())
                .get().await()
            doc.getString("email")
                ?: throw IllegalStateException("Korisnik sa tim korisničkim imenom ne postoji.")
        }

        val result = auth.signInWithEmailAndPassword(email, password).await()
        val user = result.user ?: throw IllegalStateException("Neuspješna prijava.")
        user.reload().await()

        // 1.c - logovanje tek nakon potvrde mejla
        if (!user.isEmailVerified) {
            auth.signOut()
            throw IllegalStateException("Mejl nije potvrđen. Provjerite inbox i potvrdite registraciju.")
        }

        // Sinhronizujemo flag emailVerified u Firestore
        db.collection(FirestoreCollections.USERS).document(user.uid)
            .update("emailVerified", true)
    }

    /**
     * 1.e - resetovanje lozinke unosom stare i nove (dva puta) unutar forme.
     * Firebase trazi reautentifikaciju starom lozinkom prije promjene.
     */
    suspend fun changePassword(oldPassword: String, newPassword: String) {
        val user = auth.currentUser ?: throw IllegalStateException("Niste prijavljeni.")
        val email = user.email ?: throw IllegalStateException("Nalog nema mejl.")
        val credential = EmailAuthProvider.getCredential(email, oldPassword)
        user.reauthenticate(credential).await()
        user.updatePassword(newPassword).await()
    }

    /** Ponovno slanje verifikacionog mejla. */
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
