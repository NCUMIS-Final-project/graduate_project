package com.example.graduate_project

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    private var user_account
            : EditText? = null
    private var user_password
            : EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        user_account = findViewById<View>(R.id.et2) as EditText
        user_password = findViewById<View>(R.id.et3) as EditText
        val submit =
            findViewById<View>(R.id.button) as Button
        // 按下按鈕 觸發事件
        submit.setOnClickListener {
            if ("123" == user_account!!.text.toString() && "321" == user_password!!.text.toString()) {
                val intent = Intent(this, MapsActivity::class.java)
                startActivity(intent)
            }
            else{
                getDialog()
            }
        }
    }

    fun getDialog(){
        AlertDialog.Builder(this)
            .setTitle("錯誤訊息")
            .setMessage("登入失敗")
            .setCancelable(true)
            .show()
    }

}