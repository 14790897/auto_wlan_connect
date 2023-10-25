package com.example.auto_wlan_connect

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat


class SettingsActivity : AppCompatActivity() {
    private lateinit var settingsFragment: SettingsFragment
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //添加返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        //点开settings时，替换当前页面
        settingsFragment = SettingsFragment()
        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, settingsFragment)
            .commit()
    }

    //设置布局
    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            val editTextPreference: EditTextPreference? =
                findPreference("wlan_website")
            editTextPreference?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference, newValue ->
                    // newValue is the text entered by the user
                    // finishSettings(newValue)  // 这行需要修改，因为 finishSettings 是 SettingsActivity 的方法，而不是 SettingsFragment 的方法
                    // Return true to update the preference's saved value
                    true
                }
        }
    }

    //添加返回功能
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            //find "wlan_website"
            val editTextPreference: EditTextPreference? = settingsFragment?.preferenceManager?.findPreference("wlan_website")
            val newValue = editTextPreference?.text
            if (newValue != null) {
                finishSettings(newValue)
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    //当输入完成时向mainactivity发送信息
    private fun finishSettings(newValue: String) {
        // 保存到 SharedPreferences
        val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("wlan_website", newValue)
            apply()
        }

        // 通过 Intent 传递数据到 MainActivity
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("wlan_website", newValue)
        setResult(RESULT_OK, intent)
        finish()
    }
}
