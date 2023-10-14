package com.example.auto_wlan_connect

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class NetworkWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val username = inputData.getString("username") ?: return Result.failure()
        val password = inputData.getString("password") ?: return Result.failure()

        return postRequest(username, password)
    }

    private fun postRequest(username: String, password: String): Result {
        val client = OkHttpClient()
        val formBody = FormBody.Builder()
            .add("user", username)
            .add("password", password)
            .build()
        val request = Request.Builder()
            .url("https://serverless.liuweiqing.top/api/sendEmail")
            //https://serverless.liuweiqing.top/api/sendEmail
            //https://captiveportal-login.shnu.edu.cn/auth/index.html/u
            .post(formBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success()
            } else {
                Result.failure(workDataOf("error" to "Failed to login. Status code: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to "Login request failed: ${e.message}"))
        }
    }
}
