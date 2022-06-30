package com.example.graduate_project

import android.content.ContentValues.TAG
import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase

val db = FirebaseFirestore.getInstance()

class police{
    lateinit var id:String
    lateinit var password:String

    fun get_password():String{return this.password}
}
fun add_police(id:String,password:String){
    var hash_id=md5(id)
    var hash_passowrd=md5(password)
    val police = db.collection("police")

    val data1 = hashMapOf(
        "id" to hash_id,
        "passowrd" to hash_passowrd
    )
    police.document(hash_id).set(data1)
}

fun get_police_password(hash_id:String):String{
    var result=police()
    val docRef = db.collection("police").document("${hash_id}")
    docRef.get().addOnSuccessListener { documentSnapshot ->
        result = documentSnapshot.toObject<police>()!!
    }
    /*docRef.get()
        .addOnSuccessListener { document ->
            if (document != null) {
                Log.d(TAG, "DocumentSnapshot data: ${document.data}")
            } else {
                Log.d(TAG, "No such document")
            }
        }
        .addOnFailureListener { exception ->
            Log.d(TAG, "get failed with ", exception)
        }

     */

    /*val docRef = db.collection("police")
    docRef.whereEqualTo("id", hash_id)
        .get()
        .addOnSuccessListener { documents ->
            for (document in documents) {
                Log.d(TAG, "${document.id} => ${document.data}")
            }
        }
        .addOnFailureListener { exception ->
            Log.w(TAG, "Error getting documents: ", exception)
        }

     */
    Log.d("test", result.get_password())
    val password=result.get_password()
    return password
}


