package com.hussein.byd.voiceassistant

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.app.SearchManager
import android.view.KeyEvent
import androidx.annotation.StringRes
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ينفّذ [Command] على الجهاز ويُرجع نص الرد الذي يُعرض ويُنطق. */
class CommandExecutor(private val context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun locale(): Locale = context.resources.configuration.locales[0]

    fun execute(command: Command): String = when (command) {
        is Command.OpenApp -> openApp(command.name)
        is Command.OpenTarget -> openTarget(command.target)
        is Command.Navigate -> navigate(command.place)
        is Command.Search -> search(command.query)
        is Command.Dial -> dial(command.number)

        Command.VolumeUp -> volume(AudioManager.ADJUST_RAISE, R.string.reply_volume_up)
        Command.VolumeDown -> volume(AudioManager.ADJUST_LOWER, R.string.reply_volume_down)
        Command.Mute -> volume(AudioManager.ADJUST_MUTE, R.string.reply_mute, steps = 1)
        Command.Unmute -> volume(AudioManager.ADJUST_UNMUTE, R.string.reply_unmute, steps = 1)

        Command.MediaPlay -> media(KeyEvent.KEYCODE_MEDIA_PLAY, R.string.reply_media_resume)
        Command.MediaPause -> media(KeyEvent.KEYCODE_MEDIA_PAUSE, R.string.reply_media_pause)
        Command.MediaNext -> media(KeyEvent.KEYCODE_MEDIA_NEXT, R.string.reply_media_next)
        Command.MediaPrevious -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS, R.string.reply_media_prev)

        Command.CurrentTime -> context.getString(
            R.string.reply_time,
            DateFormat.getTimeInstance(DateFormat.SHORT, locale()).format(Date())
        )
        Command.CurrentDate -> context.getString(
            R.string.reply_date,
            SimpleDateFormat("EEEE d MMMM yyyy", locale()).format(Date())
        )

        Command.Help -> context.getString(R.string.reply_help)
        Command.Greeting -> context.getString(R.string.reply_greeting)
        Command.Thanks -> context.getString(R.string.reply_thanks)
        Command.Developer -> context.getString(R.string.reply_developer, context.getString(R.string.dev_name))
        is Command.Unknown -> context.getString(R.string.reply_unknown)
    }

    // ------------------------------------------------------------------
    // تشغيل الأنشطة
    // ------------------------------------------------------------------

    private fun launch(intent: Intent?): Boolean {
        if (intent == null) return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    private fun result(ok: Boolean, @StringRes success: Int, vararg args: Any): String =
        if (ok) context.getString(success, *args) else context.getString(R.string.reply_not_available)

    private fun openTarget(target: Target): String = when (target) {
        Target.MAPS -> result(openMaps(), R.string.reply_open_maps)
        Target.MUSIC -> result(openMusic(), R.string.reply_open_music)
        Target.PHONE -> result(launch(Intent(Intent.ACTION_DIAL)), R.string.reply_open_phone)
        Target.SETTINGS -> result(launch(Intent(Settings.ACTION_SETTINGS)), R.string.reply_open_settings)
        Target.BLUETOOTH -> result(launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)), R.string.reply_open_bluetooth)
        Target.WIFI -> result(launch(Intent(Settings.ACTION_WIFI_SETTINGS)), R.string.reply_open_wifi)
        Target.HOME -> result(
            launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)),
            R.string.reply_home
        )
    }

    private fun openMaps(): Boolean =
        launch(context.packageManager.getLaunchIntentForPackage(GOOGLE_MAPS))
            || launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")))

    private fun openMusic(): Boolean =
        launch(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))
            || launch(Intent("android.intent.action.MUSIC_PLAYER"))

    private fun navigate(place: String): String {
        if (place.isBlank()) return context.getString(R.string.reply_need_place)
        val q = Uri.encode(place)
        val ok = launch(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q")))
            || launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")))
            || launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$q")))
        return result(ok, R.string.reply_navigating, place)
    }

    private fun search(query: String): String {
        if (query.isBlank()) return context.getString(R.string.reply_need_query)
        val ok = launch(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))
            || launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")))
        return result(ok, R.string.reply_searching, query)
    }

    /** يفتح شاشة الاتصال بالرقم جاهزاً. لا يتم الاتصال تلقائياً (للسلامة وبدون صلاحية CALL_PHONE). */
    private fun dial(number: String): String =
        result(launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))), R.string.reply_dialing, number)

    // ------------------------------------------------------------------
    // فتح التطبيقات بالاسم
    // ------------------------------------------------------------------

    private fun openApp(name: String): String {
        if (name.isBlank()) return context.getString(R.string.reply_need_app)
        val intent = findLaunchIntent(name)
        return if (launch(intent)) {
            context.getString(R.string.reply_opening_app, name)
        } else {
            context.getString(R.string.reply_app_not_found, name)
        }
    }

    @Suppress("DEPRECATION")
    private fun findLaunchIntent(spoken: String): Intent? {
        val pm = context.packageManager
        val wanted = CommandParser.normalize(spoken).trim()
        if (wanted.isEmpty()) return null

        // 1) أسماء معروفة بالعربية والإنجليزية
        for ((names, pkg) in APP_ALIASES) {
            if (names.any { CommandParser.normalize(it) == wanted }) {
                pm.getLaunchIntentForPackage(pkg)?.let { return it }
            }
        }

        // 2) مطابقة اسم التطبيق المثبّت على الجهاز
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        var bestPackage: String? = null
        var bestScore = 0
        for (info in pm.queryIntentActivities(launcher, 0)) {
            val label = CommandParser.normalize(info.loadLabel(pm).toString()).trim()
            val score = when {
                label == wanted -> 3
                label.startsWith(wanted) -> 2
                label.length >= 3 && (label.contains(wanted) || wanted.contains(label)) -> 1
                else -> 0
            }
            if (score > bestScore) {
                bestScore = score
                bestPackage = info.activityInfo.packageName
            }
        }
        return bestPackage?.let { pm.getLaunchIntentForPackage(it) }
    }

    // ------------------------------------------------------------------
    // الصوت والوسائط
    // ------------------------------------------------------------------

    private fun volume(direction: Int, @StringRes reply: Int, steps: Int = 3): String = try {
        for (i in 0 until steps) {
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                if (i == 0) AudioManager.FLAG_SHOW_UI else 0
            )
        }
        context.getString(reply)
    } catch (e: SecurityException) {
        context.getString(R.string.reply_not_available)
    }

    private fun media(keyCode: Int, @StringRes reply: Int): String {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        return context.getString(reply)
    }

    private companion object {
        const val GOOGLE_MAPS = "com.google.android.apps.maps"

        val APP_ALIASES: List<Pair<List<String>, String>> = listOf(
            listOf("youtube", "يوتيوب", "يوتوب") to "com.google.android.youtube",
            listOf("whatsapp", "واتساب", "واتس اب", "واتس") to "com.whatsapp",
            listOf("spotify", "سبوتيفاي", "سبوتفاي") to "com.spotify.music",
            listOf("telegram", "تيليجرام", "تليجرام", "تيليغرام", "تلكرام") to "org.telegram.messenger",
            listOf("waze", "ويز") to "com.waze",
            listOf("chrome", "كروم", "جوجل كروم") to "com.android.chrome",
            listOf("anghami", "انغامي", "أنغامي") to "com.anghami",
            listOf("netflix", "نتفلكس", "نتفليكس") to "com.netflix.mediaclient",
            listOf("facebook", "فيسبوك", "فيس بوك") to "com.facebook.katana",
            listOf("instagram", "انستغرام", "انستقرام", "انستجرام") to "com.instagram.android",
            listOf("tiktok", "تيك توك") to "com.zhiliaoapp.musically",
            listOf("snapchat", "سناب شات", "سناب") to "com.snapchat.android"
        )
    }
}
