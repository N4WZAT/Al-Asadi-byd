package com.hussein.byd.voiceassistant

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hussein.byd.voiceassistant.databinding.ActivityAboutBinding

/** شاشة "حول التطبيق": معلومات المبرمج والأوامر المدعومة. */
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val phone = getString(R.string.dev_phone)
        binding.tvDevName.text = getString(R.string.dev_name)
        binding.tvDevPhone.text = phone
        binding.tvVersion.text = getString(R.string.about_version, versionName())

        binding.btnBack.setOnClickListener { finish() }

        binding.btnCall.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.reply_not_available, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("phone", phone))
            Toast.makeText(this, R.string.about_copied, Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }
}
