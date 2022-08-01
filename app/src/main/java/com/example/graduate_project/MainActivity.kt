package com.example.graduate_project


import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent.KEY_DESCRIPTION
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.firestore.*


class MainActivity : AppCompatActivity() {
    data class police(
        @get: PropertyName("id") @set: PropertyName("id") var id: String = "",
        @get: PropertyName("password") @set: PropertyName("password") var password: String = ""
    )
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /** 略過登入，直接跳轉到地圖*/
//        val intent = Intent(this, MapsActivity::class.java)
//        startActivity(intent)

        var user_account: TextView = findViewById<TextView>(R.id.et2)
        var user_password: TextView = findViewById<TextView>(R.id.et3)
        val submit =
            findViewById<View>(R.id.button) as Button
        submit.setOnClickListener {
            val id: String = user_account.text.toString()
            val password: String = user_password.text.toString()
            if(id.isEmpty() or password.toString().isEmpty()){
                get_null_Dialog()
            }
            else {
                police_login("$id", "$password")
            }
        }
    }

    fun police_login(id: String, password: String) {
        val docRef = db.collection("police").document("$id")
        docRef.get(Source.SERVER).addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                val result = documentSnapshot.toObject(police::class.java)!!
                if (password == result.password) {
                    //success_dialog()
                    val intent = Intent(this, MapsActivity::class.java)
                    startActivity(intent)
                } else {
                    getDialog()
                }
            }else{
                getDialog()
            }
        }.addOnFailureListener(OnFailureListener { e ->
            Toast.makeText(this@MainActivity, "Error!", Toast.LENGTH_SHORT).show()
            Log.d(TAG, e.toString())
        })
    }

    /*fun success_dialog(){
        AlertDialog.Builder(this)
            .setTitle("正確訊息")
            .setMessage("登入成功")
            .setCancelable(true)
            .show()
    }*/
    fun getDialog(){
        AlertDialog.Builder(this)
            .setTitle("錯誤訊息")
            .setMessage("登入失敗 請檢查帳號密是否正確")
            .setCancelable(true)
            .show()
    }

    fun get_null_Dialog(){
        AlertDialog.Builder(this)
            .setTitle("錯誤訊息")
            .setMessage("請填入帳號密碼")
            .setCancelable(true)
            .show()
    }
}
