package com.example.auto_wlan_connect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.Menu
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.auto_wlan_connect.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import android.Manifest
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.Observer


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    //网络变化时，执行请求
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: NetworkCallback
    //请求权限
    private val REQUEST_LOCATION_PERMISSION = 1



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.button.setOnClickListener { _ ->
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


        // 注册网络变化回调
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        registerNetworkCallback()

        //请求网络权限
        requestPermissions()
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

    private fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                networkCapabilities?.let {
                    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val wifiInfo = wifiManager.connectionInfo
                    if (wifiInfo != null && wifiInfo.ssid != null) {
                        val ssid = wifiInfo.ssid.replace("\"", "")  // 删除SSID两边的双引号
                        // 现在你可以使用SSID变量
                        if (ssid == "CMCC-2202-5G" || ssid == "CMCC-2202") {//
                            scheduleWork()
                        }
                    }
                }
            }
        }

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }
    private fun scheduleWork() {
        val workRequest = OneTimeWorkRequestBuilder<NetworkWorker>()
            .setInputData(workDataOf("username" to "your-username", "password" to "your-password"))
            .build()

        // Enqueue the work request
        WorkManager.getInstance(applicationContext).enqueue(workRequest)

        // Post the observe method call to the main thread
        Handler(Looper.getMainLooper()).post {
        // Observe the work request's status
        WorkManager.getInstance(applicationContext)
            .getWorkInfoByIdLiveData(workRequest.id)
            .observe(this, Observer { workInfo ->
                workInfo?.let {
                    if (it.state.isFinished) {
                        // Get the output data
                        val error = it.outputData.getString("error") ?: "No error message available"
                        Toast.makeText(this, if (error != "No error message available") error else "Successfully logged in", Toast.LENGTH_LONG).show()
                    }
                } ?: run {
                    Toast.makeText(this, "WorkInfo is null", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }


    //权限请求
    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission granted
                    registerNetworkCallback()
                } else {
                    // Permission denied
                    // Show an explanation to the user, or handle the failure as needed
                }
                return
            }
            else -> {
                // Ignore all other requests.
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}