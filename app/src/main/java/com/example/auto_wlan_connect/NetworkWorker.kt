package com.example.auto_wlan_connect

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy

class NetworkWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private var wifiNetwork: Network? = null

    override suspend fun doWork(): Result {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wifiNetwork = network
            }
        }

        connectivityManager.requestNetwork(request, networkCallback)

        val username = inputData.getString("username") ?: return Result.failure()
        val password = inputData.getString("password") ?: return Result.failure()
        //settingactivity的消息
        var wlanWebsite = inputData.getString("wlanWebsite") ?: return Result.failure()

        // Wait for a while to get the WiFi network, or you can make it more elegant.
        // Here just for example.
        Thread.sleep(200)

        return postRequest(username, password, wlanWebsite)
    }

    private fun postRequest(username: String, password: String, wlanWebsite: String): Result {
        val client = wifiNetwork?.let { network->
            OkHttpClient.Builder()
                .socketFactory(network.socketFactory)// 强制使用校园网WiFi发送post请求
                .proxy(Proxy.NO_PROXY)
                .build()
        } ?: OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()  // Fallback to default if wifiNetwork is null
        val formBody = FormBody.Builder()
            .add("user", username)
            .add("password", password)
            .build()
        val url = wlanWebsite ?: "http://captiveportal-login.shnu.edu.cn/auth/index.html/u"

        val request = Request.Builder()
            .url(url)
            //https://serverless.liuweiqing.top/api/sendEmail
            //https://captiveportal-login.shnu.edu.cn/auth/index.html/u
            .post(formBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success()
            } else {
                Result.failure(
                    workDataOf(
                        "error" to "Failed to login.",
                        "status_code" to response.code,
                        "response_message" to response.body?.string()
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(
                workDataOf(
                    "error" to "Login request failed: ${e.message}",
                    "exception_type" to e.javaClass.simpleName
                )
            )
        }
    }
}
