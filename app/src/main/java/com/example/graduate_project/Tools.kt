package com.example.graduate_project

import android.app.Application
import android.content.ContentValues.TAG
import android.util.Log
import android.widget.Toast
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import java.math.BigInteger
import java.security.MessageDigest

fun md5(input:String): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
}

fun test() {
    val db = FirebaseFirestore.getInstance()
    val docRef = db.collection("carInfo").document("001")
    docRef.addSnapshotListener { snapshot, e ->
        if (e != null) {
            Log.w(TAG, "Listen failed.", e)
            return@addSnapshotListener
        }
        if (snapshot != null && snapshot.exists()) {
            Log.d("fun_test", "Current data: ${snapshot.data}")
        } else {
            Log.d("fun_test", "Current data: null")
        }
    }
}

fun test2() {
    val db = FirebaseFirestore.getInstance()
    db.collection("carInfo")
        .addSnapshotListener { value, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }
            for (dc in value!!.documentChanges) {
                var car = dc.document.toObject(MapsActivity.Car::class.java)!!
                when (dc.type) {
                    DocumentChange.Type.ADDED -> Log.d(TAG, "New city: ${dc.document.data}")
                    DocumentChange.Type.MODIFIED -> Log.d("test2", "Modified city: ${dc.document.data}")
                    DocumentChange.Type.REMOVED -> Log.d(TAG, "Removed city: ${dc.document.data}")
                }
                if (dc.type==DocumentChange.Type.ADDED){
                    Log.d("try1","$car")
                }
                if (dc.type==DocumentChange.Type.MODIFIED){
                    Log.d("try2","$car")
                }
            }
        }
}



