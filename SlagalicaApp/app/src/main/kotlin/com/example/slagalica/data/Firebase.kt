package com.example.slagalica.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

object FirebaseProvider {
    val auth: FirebaseAuth by lazy { Firebase.auth }
    val db: FirebaseFirestore by lazy { Firebase.firestore }

    val currentUid: String?
        get() = auth.currentUser?.uid
}

object FirestoreCollections {
    const val USERS = "users"
    const val USERNAMES = "usernames"
    const val NOTIFICATIONS = "notifications"
    const val GAME_RESULTS = "gameResults"

    const val KZZ_QUESTIONS = "kzzQuestions"
    const val SPOJNICE_RUNDE = "spojniceRunde"
    const val ASOCIJACIJE_RUNDE = "asocijacijeRunde"
    const val KORAK_POJMOVI = "korakPojmovi"

    const val FRIENDSHIPS = "friendships"     // top-level: jedan dokument = obostrano prijateljstvo
    const val REGION_STANDINGS = "regionStandings"  // istorija plasmana regiona po ciklusu

    const val CHATS = "chats"                 // top-level: chats/{region}/poruke/{id} (spec 8)
    const val CHAT_MESSAGES = "poruke"

    const val MATCH_INVITES = "matchInvites"  // pozivi prijatelja na partiju (spec 7.c-e)
}
