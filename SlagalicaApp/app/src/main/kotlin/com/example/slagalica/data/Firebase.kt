package com.example.slagalica.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Centralna tačka pristupa Firebase servisima.
 * Umjesto da svuda pozivamo FirebaseAuth.getInstance(), koristimo ove lazy singletone.
 */
object FirebaseProvider {
    val auth: FirebaseAuth by lazy { Firebase.auth }
    val db: FirebaseFirestore by lazy { Firebase.firestore }

    /** UID trenutno ulogovanog korisnika ili null ako niko nije ulogovan. */
    val currentUid: String?
        get() = auth.currentUser?.uid
}

/** Nazivi kolekcija u Firestore bazi - na jednom mjestu da se izbjegnu greške u kucanju. */
object FirestoreCollections {
    const val USERS = "users"
    const val USERNAMES = "usernames"        // mapiranje korisnickoIme -> email (za login po imenu)
    const val NOTIFICATIONS = "notifications" // podkolekcija unutar users/{uid}/notifications
    const val GAME_RESULTS = "gameResults"    // podkolekcija unutar users/{uid}/gameResults

    // Podaci za igre (vidi GameDataRepository)
    const val KZZ_QUESTIONS = "kzzQuestions"
    const val SPOJNICE_RUNDE = "spojniceRunde"
    const val ASOCIJACIJE_RUNDE = "asocijacijeRunde"
}
