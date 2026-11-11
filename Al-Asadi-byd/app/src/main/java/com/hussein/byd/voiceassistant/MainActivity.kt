package com.hussein.byd.voiceassistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.widget.TextViewCompat
import com.hussein.byd.voiceassistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), VoiceRecognizer.Callback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var speaker: Speaker
    private lateinit var executor: CommandExecutor
    private lateinit var recognizer: VoiceRecognizer

    private var listening = false
    private var voiceReplies = true
    private var pulse: ObjectAnimator? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening() else setIdle(R.string.status_permission_denied)
        }

    /** بديل عند عدم توفر SpeechRecognizer مباشرة: نافذة التعرف على الصوت الخاصة بالنظام. */
    private val speechIntentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val list = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.filter { it.isNotBlank() }
                .orEmpty()
            if (result.resultCode == RESULT_OK && list.isNotEmpty()) handleCandidates(list)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        voiceReplies = prefs.getBoolean(KEY_VOICE_REPLIES, true)

        speaker = Speaker(this)
        executor = CommandExecutor(this)
        recognizer = VoiceRecognizer(this, this)

        setupUi()
        setIdle()
    }

    private fun setupUi() {
        binding.tvDeveloper.text = getString(
            R.string.dev_footer,
            getString(R.string.dev_name),
            getString(R.string.dev_phone)
        )
        binding.tvDeveloper.setOnClickListener { openAbout() }
        binding.btnAbout.setOnClickListener { openAbout() }

        binding.btnMic.setOnClickListener {
            if (listening) {
                recognizer.stop() // نهاية الكلام يدوياً: تُعالَج النتيجة الآن
            } else {
                startListening()
            }
        }

        binding.btnLanguage.setOnClickListener {
            recognizer.cancel()
            val next = if (isArabic()) "en" else "ar"
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(next))
        }

        binding.btnVoice.setOnClickListener {
            voiceReplies = !voiceReplies
            prefs.edit().putBoolean(KEY_VOICE_REPLIES, voiceReplies).apply()
            if (!voiceReplies) speaker.stop()
            refreshVoiceButton()
        }
        refreshVoiceButton()

        binding.btnMaps.setOnClickListener { runQuick(Target.MAPS) }
        binding.btnMusic.setOnClickListener { runQuick(Target.MUSIC) }
        binding.btnPhone.setOnClickListener { runQuick(Target.PHONE) }
        binding.btnSettings.setOnClickListener { runQuick(Target.SETTINGS) }
    }

    // ------------------------------------------------------------------
    // الاستماع
    // ------------------------------------------------------------------

    private fun startListening() {
        speaker.stop()

        if (!recognizer.isAvailable()) {
            launchSystemSpeechDialog()
            return
        }

        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        binding.tvHeard.text = ""
        binding.tvReply.text = ""
        setListening()
        recognizer.start(recognitionLanguage())
    }

    private fun launchSystemSpeechDialog() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguage())
        try {
            speechIntentLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            setIdle(R.string.status_not_available)
        }
    }

    override fun onPartial(text: String) {
        binding.tvHeard.text = text
    }

    override fun onResults(candidates: List<String>) {
        setIdle()
        handleCandidates(candidates)
    }

    override fun onError(@StringRes message: Int) {
        setIdle(message)
    }

    // ------------------------------------------------------------------
    // تنفيذ الأوامر
    // ------------------------------------------------------------------

    private fun handleCandidates(candidates: List<String>) {
        // نختار أول احتمال يفهمه المساعد، وإلا نعرض الأول كما هو
        val parsed = candidates.map { it to CommandParser.parse(it) }
        val (heard, command) = parsed.firstOrNull { it.second !is Command.Unknown } ?: parsed.first()
        binding.tvHeard.text = "“$heard”"
        showReply(executor.execute(command), speak = true)
    }

    private fun runQuick(target: Target) {
        binding.tvHeard.text = ""
        showReply(executor.execute(Command.OpenTarget(target)), speak = false)
    }

    private fun showReply(reply: String, speak: Boolean) {
        binding.tvReply.text = reply
        if (speak && voiceReplies) speaker.speak(reply, executor.locale())
    }

    // ------------------------------------------------------------------
    // الحالة والواجهة
    // ------------------------------------------------------------------

    private fun setListening() {
        listening = true
        binding.btnMic.setBackgroundResource(R.drawable.bg_mic_listening)
        binding.tvStatus.setText(R.string.status_listening)
        pulse?.cancel()
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            binding.btnMic,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.1f)
        ).apply {
            duration = 650
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun setIdle(@StringRes status: Int = R.string.status_idle) {
        listening = false
        pulse?.cancel()
        pulse = null
        binding.btnMic.scaleX = 1f
        binding.btnMic.scaleY = 1f
        binding.btnMic.setBackgroundResource(R.drawable.bg_mic_idle)
        binding.tvStatus.setText(status)
    }

    private fun refreshVoiceButton() {
        val on = voiceReplies
        binding.btnVoice.setText(if (on) R.string.btn_voice_on else R.string.btn_voice_off)
        binding.btnVoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (on) R.drawable.ic_volume_up else R.drawable.ic_volume_off, 0, 0, 0
        )
        TextViewCompat.setCompoundDrawableTintList(
            binding.btnVoice,
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent))
        )
    }

    private fun openAbout() {
        startActivity(Intent(this, AboutActivity::class.java))
    }

    private fun isArabic(): Boolean = resources.configuration.locales[0].language == "ar"

    private fun recognitionLanguage(): String = if (isArabic()) ARABIC_TAG else "en-US"

    // ------------------------------------------------------------------
    // دورة الحياة
    // ------------------------------------------------------------------

    override fun onStop() {
        super.onStop()
        if (listening) {
            recognizer.cancel()
            setIdle()
        }
        speaker.stop()
    }

    override fun onDestroy() {
        recognizer.destroy()
        speaker.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val KEY_VOICE_REPLIES = "voice_replies"

        /** لهجة التعرف على الصوت العربي. غيّرها إلى ar-SA أو ar-EG إن أردت. */
        const val ARABIC_TAG = "ar-IQ"
    }
}
