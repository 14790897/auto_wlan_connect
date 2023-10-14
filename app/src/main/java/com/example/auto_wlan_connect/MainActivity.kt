package com.example.auto_wlan_connect

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.auto_wlan_connect.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    //网络变化时，执行请求
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.button.setOnClickListener { view ->
            val url = "https://github.com/14790897/auto_wlan_connect/issues"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        }
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        val loginButton: Button = findViewById(R.id.login_button)
        loginButton.setOnClickListener {
            val username: String = findViewById<EditText>(R.id.username_input).text.toString()
            val password: String = findViewById<EditText>(R.id.password_input).text.toString()
            saveCredentials(username, password)
            postRequest(username, password)
        }

        // Create key for encryption
//        val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
//        if (!sharedPreferences.getBoolean("key_created", false)) {
//            CryptoManager.createKey(keyAlias)
//            sharedPreferences.edit().putBoolean("key_created", true).apply()
//        }

        // 加载保存的用户名和密码
        val sharedPreferences = getSharedPreferences("credentials", Context.MODE_PRIVATE)
        val savedUsername = sharedPreferences.getString("username", "")
        val savedPassword = sharedPreferences.getString("password", "")
        findViewById<EditText>(R.id.username_input).setText(savedUsername)
        findViewById<EditText>(R.id.password_input).setText(savedPassword)
    }

    private fun saveCredentials(username: String, password: String) {
        val sharedPreferences = getSharedPreferences("credentials", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("username", username)
            putString("password", password)
            apply()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun showAlert(message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun postRequest(username: String, password: String) {
        val client = OkHttpClient()
        val formBody = FormBody.Builder()
            .add("user", username)
            .add("password", password)
            .build()
        val request = Request.Builder()
            .url("https://captiveportal-login.shnu.edu.cn/auth/index.html/u")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                e.printStackTrace()
                showAlert("Login request failed: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful) {
                    showAlert("Successfully logged in")
                } else {
                    showAlert("Failed to login. Status code: ${response.code}, Content: ${response.body?.string()}")
                }
            }
        })
    }
}