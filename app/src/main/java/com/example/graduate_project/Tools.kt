package com.example.graduate_project

import android.util.Log
import java.math.BigInteger
import java.security.MessageDigest

fun md5(input:String): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
}

fun police_login(id:String,password:String):Boolean{
    var success:Boolean=false
    //val hash_id=md5(id)
    //val hash_password=md5(password)
    var police_password= get_police_password(id)
    if (password==police_password){success=true}
    return success
}