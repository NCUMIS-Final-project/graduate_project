package com.example.graduate_project

import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

val db = Firebase.firestore

fun add_data(){
    val cities = db.collection("cities")

    val data1 = hashMapOf(
        "name" to "San Francisco",
        "state" to "CA",
        "country" to "USA",
        "capital" to false,
        "population" to 860000,
        "regions" to listOf("west_coast", "norcal")
    )
    cities.document("SF").set(data1)
}
