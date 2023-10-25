package com.example.auto_wlan_connect

import android.Manifest
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
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
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
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.Toast
import androidx.lifecycle.Observer
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import java.net.Proxy


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    //网络变化时，执行请求
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: NetworkCallback

    //请求权限
    private val REQUEST_LOCATION_PERMISSION = 1

    //settingactivity的消息
    private var wlanWebsite: String? = null

    //默认网络，用于settings未获取到的情况
    private var defaultWebsite = "http://captiveportal-login.shnu.edu.cn/auth/index.html/u"
    // firebase analytics
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //firebase analytics
        // Obtain the FirebaseAnalytics instance.
        firebaseAnalytics = Firebase.analytics
        //请求网络变化权限,但发现不需要明确请求
        requestPermissions2()
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
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val bundle = Bundle()
            bundle.putString("destination", destination.label.toString())
            firebaseAnalytics.logEvent("navigation_destination_changed", bundle)
        }

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        val loginButton: Button = findViewById(R.id.login_button)
        loginButton.setOnClickListener {
            // 用于 Firebase Analytics 的事件追踪
            val bundle = Bundle()
            bundle.putString("button_name", "login_button")
            bundle.putString("interaction_type", "click")
            firebaseAnalytics.logEvent("login_button_clicked", bundle)

            val username: String = findViewById<EditText>(R.id.username_input).text.toString()
            val password: String = findViewById<EditText>(R.id.password_input).text.toString()
            saveCredentials(username, password)
//            postRequest(username, password)
            scheduleWork(wlanWebsite?: defaultWebsite)
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
        var wlanWebsite = sharedPreferences.getString("wlanWebsite", "")

        findViewById<EditText>(R.id.username_input).setText(savedUsername)
        findViewById<EditText>(R.id.password_input).setText(savedPassword)


        val loginButton: Button = findViewById(R.id.login_button)
        loginButton.setOnClickListener {
            val username: String = findViewById<EditText>(R.id.username_input).text.toString()
            val password: String = findViewById<EditText>(R.id.password_input).text.toString()
            saveCredentials(username, password)
//            postRequest(username, password)
            scheduleWork(wlanWebsite?: String)
        }

        // checkbox相关设置
        val checkBox = findViewById<CheckBox>(R.id.checkbox_feature)
        checkBox.setOnCheckedChangeListener { buttonView, isChecked ->
            val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putBoolean("feature_enabled", checkBox.isChecked)
            editor.apply()
        }
        //加载checkbox的设置
        val sharedPreferencesSettings = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val featureEnabled = sharedPreferencesSettings.getBoolean("feature_enabled", false)
        checkBox.isChecked = featureEnabled

        if(checkBox.isChecked) {
            // 注册网络变化回调
            connectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            registerNetworkCallback()

            //请求网络权限（如果选中的话）
            requestPermissions()
        }

        // 从 SharedPreferences 中读取 settings中保存的wlan_website
        val sharedPreferencesInSettings = getSharedPreferences("settings", Context.MODE_PRIVATE)
        wlanWebsite = sharedPreferencesInSettings.getString("wlan_website", defaultWebsite) // 用一个默认值


        //记录应用被打开
        firebaseAnalytics.logEvent("app_opened", null)
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
        val client = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)  // 在这里设置代理
            .build()
        val formBody = FormBody.Builder()
            .add("user", username)
            .add("password", password)
            .build()

        val url = wlanWebsite ?: "http://captiveportal-login.shnu.edu.cn/auth/index.html/u"

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                e.printStackTrace()
                showAlert("failed: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful) {
                    showAlert("Successfully logged in")
                } else {
                    showAlert("Failed. Status code: ${response.code}, Content: ${response.body?.string()}")
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
                    val wifiManager =
                        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val wifiInfo = wifiManager.connectionInfo
                    if (wifiInfo != null && wifiInfo.ssid != null) {
                        val ssid = wifiInfo.ssid.replace("\"", "")  // 删除SSID两边的双引号
                        // 现在你可以使用SSID变量
                        if (ssid == "shnu" || ssid == "shnu-mobile") {//
                            scheduleWork(wlanWebsite?: defaultWebsite)
                        }
                    }
                }
                // 用于 Firebase Analytics 的事件追踪
                val bundle = Bundle()
                bundle.putString("network_status", "available")
                firebaseAnalytics.logEvent("network_status_changed", bundle)

            }
        }

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    private fun scheduleWork(wlanWebsite: Any) {
        // 加载保存的用户名和密码
        val sharedPreferences = getSharedPreferences("credentials", Context.MODE_PRIVATE)
        val savedUsername = sharedPreferences.getString("username", "")
        val savedPassword = sharedPreferences.getString("password", "")
        var wlanWebsite = sharedPreferences.getString("wlanWebsite", "")
        val workRequest = OneTimeWorkRequestBuilder<NetworkWorker>()
            .setInputData(workDataOf("username" to savedUsername, "password" to savedPassword, "wlanWebsite" to wlanWebsite))
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
                            val error =
                                it.outputData.getString("error") ?: "No error message available"
                            val statusCode = it.outputData.getString("status_code") ?: "No status code available"
                            val responseMessage = it.outputData.getString("response_message") ?: "No response message available"
                            if (error != "No error message available") {
                                Toast.makeText(
                                    this,
                                    "Error: $error, Response Message: $responseMessage, Status Code: $statusCode",
                                    Toast.LENGTH_LONG
                                ).show()

                                // Firebase Analytics 追踪
                                val bundle = Bundle()
                                val maxLength = 100
                                val truncatedError = if (error.length > maxLength) error.substring(0, maxLength) else error
                                bundle.putString("error_message", truncatedError)
                                bundle.putString("response_message", responseMessage)
                                bundle.putString("status_code", statusCode)
                                firebaseAnalytics.logEvent("login_failed", bundle)

                            } else {
                                Toast.makeText(
                                    this,
                                    "Successfully logged in",
                                    Toast.LENGTH_LONG
                                ).show()

                                // Firebase Analytics 追踪
                                val bundle = Bundle()
                                bundle.putString("status", "success")
                                firebaseAnalytics.logEvent("login_successful", bundle)
                            }

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
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            // Permission is not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        }
    }

    // 请求 CHANGE_NETWORK_STATE 权限
    private fun requestPermissions2() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CHANGE_NETWORK_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            // Permission is not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CHANGE_NETWORK_STATE),
                REQUEST_LOCATION_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission granted
                    registerNetworkCallback()
                    //firebase analytics
                    val bundle = Bundle()
                    bundle.putString("permission", "granted")
                    firebaseAnalytics.logEvent("location_permission", bundle)
                } else {
                    // Permission denied
                    // Show an explanation to the user, or handle the failure as needed
                    val bundle = Bundle()
                    bundle.putString("permission", "denied")
                    firebaseAnalytics.logEvent("location_permission", bundle)
                }
                return
            }

            else -> {
                // Ignore all other requests.
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    //点击设置按钮后进入设置界面
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                // Start SettingsActivity when the settings menu item is selected
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    //接受settinsactivity的消息
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && data != null) {
            //持久化wlanWebsite
            wlanWebsite = data.getStringExtra("wlan_website")
            val sharedPreferences = getSharedPreferences("credentials", Context.MODE_PRIVATE)
            with(sharedPreferences.edit()) {
                putString("wlanWebsite", wlanWebsite)
                apply()
            }
        }
    }
}


//待办事项 :需要将两个发送的方法整合到一起 10.16