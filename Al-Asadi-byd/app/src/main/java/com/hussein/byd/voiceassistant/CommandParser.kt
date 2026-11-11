package com.hussein.byd.voiceassistant

/**
 * يحوّل النص المنطوق (عربي أو إنجليزي) إلى [Command].
 * لا يعتمد على أي مكتبة أندرويد، لذلك يمكن اختباره بسهولة.
 */
object CommandParser {

    private val WS = Regex("\\s+")

    // ------------------------------------------------------------------
    // دوال مساعدة
    // ------------------------------------------------------------------

    /**
     * توحيد الحروف العربية للمقارنة (أ إ آ ← ا ، ى ← ي ، ة ← ه ...).
     * التحويل حرف بحرف بنفس الطول، لذلك تبقى المواضع متطابقة مع النص الأصلي.
     */
    fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                when (val c = ch.lowercaseChar()) {
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    'ى', 'ئ' -> 'ي'
                    'ؤ' -> 'و'
                    'ة' -> 'ه'
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    /** تنظيف النص: حذف التشكيل والتطويل، تحويل الأرقام الهندية، وإزالة علامات الترقيم. */
    private fun clean(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            val c = ch.lowercaseChar()
            when {
                c in 'ً'..'ٟ' || c == 'ٰ' || c == 'ـ' -> Unit
                c in '٠'..'٩' -> sb.append('0' + (c - '٠'))
                c in '۰'..'۹' -> sb.append('0' + (c - '۰'))
                c.isLetterOrDigit() || c == '+' -> sb.append(c)
                else -> sb.append(' ')
            }
        }
        return sb.toString().replace(WS, " ").trim()
    }

    /** قائمة كلمات مفتاحية موحّدة، الأطول أولاً. */
    private fun kw(vararg words: String): List<String> =
        words.map { normalize(clean(it)) }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { it.length }

    /** هل تحتوي الجملة على إحدى العبارات ككلمات كاملة؟ */
    private fun has(n: String, phrases: List<String>): Boolean {
        val padded = " $n "
        return phrases.any { padded.contains(" $it ") }
    }

    /** إذا بدأت الجملة بإحدى العبارات يُرجع الباقي (قد يكون فارغاً)، وإلا null. */
    private fun afterPrefix(n: String, text: String, prefixes: List<String>): String? {
        for (p in prefixes) {
            if (n == p) return ""
            if (n.startsWith("$p ")) return text.substring(p.length).trim()
        }
        return null
    }

    private fun stripFillers(input: String): String {
        var out = " $input "
        for (f in FILLERS) out = out.replace(" $f ", " ")
        var tokens = out.trim().split(WS).filter { it.isNotEmpty() }
        while (tokens.size > 1 && tokens.first() in WAKE_WORDS) tokens = tokens.drop(1)
        return tokens.joinToString(" ")
    }

    private fun removeFillerWords(s: String): String =
        s.split(WS).filter { it.isNotEmpty() && normalize(it) !in FILLER_WORDS }.joinToString(" ")

    private fun targetOf(n: String): Target? {
        val x = normalize(removeFillerWords(n))
        return TARGETS.firstOrNull { (_, words) -> x in words }?.first
    }

    // ------------------------------------------------------------------
    // الكلمات المفتاحية
    // ------------------------------------------------------------------

    private val FILLERS = listOf("من فضلك", "لو سمحت", "رجاء", "please", "يا مساعد", "hey assistant", "ok assistant")
    private val WAKE_WORDS = setOf("hey", "ok", "okay", "يا")
    private val FILLER_WORDS = kw("تطبيق", "برنامج", "app", "application", "the", "my", "لي").toSet()

    private val HELP = kw("مساعدة", "ساعدني", "الاوامر", "اوامر", "ما هي الاوامر", "ماذا تستطيع", "help", "commands", "what can you do")
    private val DEVELOPER = kw(
        "من صنعك", "من برمجك", "من طورك", "من صممك", "من المبرمج", "من المطور",
        "who made you", "who developed you", "who programmed you", "who created you", "who built you", "your developer"
    )

    private val NAVIGATE = kw(
        "اذهب الى", "روح الى", "خذني الى", "وجهني الى", "وديني الى", "ودني الى", "وصلني الى", "توجه الى",
        "navigate to", "take me to", "go to", "directions to", "drive to", "route to", "navigate"
    )
    private val SEARCH = kw("ابحث لي عن", "ابحث عن", "دور على", "بحث عن", "search for", "look up", "search", "google")
    private val OPEN = kw("افتح لي", "افتح", "فتح", "شغل تطبيق", "تشغيل تطبيق", "open", "launch", "start")
    private val PLAY_PREFIX = kw("شغل", "play")

    private val VOL_UP = kw(
        "ارفع الصوت", "رفع الصوت", "علي الصوت", "زيد الصوت", "زود الصوت", "اعلي الصوت", "صوت اعلي",
        "volume up", "turn it up", "turn up the volume", "turn the volume up", "increase volume",
        "increase the volume", "raise the volume", "louder"
    )
    private val VOL_DOWN = kw(
        "اخفض الصوت", "خفض الصوت", "وطي الصوت", "نزل الصوت", "قلل الصوت", "صوت اقل",
        "volume down", "turn it down", "turn down the volume", "turn the volume down", "decrease volume",
        "decrease the volume", "lower the volume", "quieter", "softer"
    )
    private val UNMUTE = kw("الغاء كتم الصوت", "الغاء الكتم", "الغي كتم الصوت", "فك كتم الصوت", "فك الكتم", "unmute", "un mute")
    private val MUTE = kw("كتم الصوت", "اكتم الصوت", "كتم", "اكتم", "صامت", "mute", "silence")

    private val NEXT = kw("التالي", "التاليه", "next", "skip")
    private val PREVIOUS = kw("السابق", "السابقه", "previous", "prev")
    private val PAUSE = kw("وقف", "توقف", "ايقاف مؤقت", "ايقاف", "pause", "stop")
    private val PLAY_ALONE = kw("شغل", "شغلي", "play", "resume", "كمل", "استئناف", "استمر", "continue")
    private val MUSIC_WORDS = kw("الموسيقي", "موسيقي", "الاغاني", "اغاني", "اغنيه", "الاغنيه", "music", "song", "songs")

    private val TIME = kw("كم الساعه", "الساعه كم", "الساعه", "كم الوقت", "الوقت", "what time", "time is it", "current time", "the time")
    private val DATE = kw("التاريخ", "اي يوم", "ايش اليوم", "شو اليوم", "what day", "the date", "what date", "today s date")

    private val NEARBY = kw("اقرب", "nearest", "closest", "nearby", "near me")
    private val THANKS = kw("شكرا", "شكرا لك", "thanks", "thank you", "thank")
    private val GREETING = kw("مرحبا", "اهلا", "هلا", "السلام عليكم", "سلام", "hello", "hi", "hey")

    private val TARGETS: List<Pair<Target, List<String>>> = listOf(
        Target.BLUETOOTH to kw("البلوتوث", "بلوتوث", "bluetooth"),
        Target.WIFI to kw("الواي فاي", "واي فاي", "الوايفاي", "وايفاي", "wifi", "wi fi"),
        Target.SETTINGS to kw("الاعدادات", "اعدادات", "settings"),
        Target.MAPS to kw("الخرائط", "خرائط", "الخريطة", "خريطة", "خرائط جوجل", "خرائط غوغل", "maps", "map", "google maps"),
        Target.MUSIC to kw("الموسيقى", "موسيقى", "الاغاني", "music", "songs"),
        Target.PHONE to kw("الهاتف", "هاتف", "التلفون", "تلفون", "الجوال", "phone", "dialer"),
        Target.HOME to kw("الشاشة الرئيسية", "الرئيسية", "الصفحة الرئيسية", "home", "home screen", "go home")
    )

    // ------------------------------------------------------------------
    // التحليل
    // ------------------------------------------------------------------

    fun parse(raw: String): Command {
        val text = stripFillers(clean(raw))
        if (text.isEmpty()) return Command.Unknown(raw)
        val n = normalize(text)
        val tokens = n.split(" ")

        if (tokens.size <= 5 && has(n, HELP)) return Command.Help
        if (has(n, DEVELOPER)) return Command.Developer

        // اتصال: "اتصل بالرقم 0771..." أو "call 0771..."
        val first = tokens[0]
        if (first.startsWith("اتصل") || first == "اتصال" || first == "call" || first == "dial") {
            val number = text.filter { it.isDigit() || it == '+' }
            return if (number.count { it.isDigit() } >= 3) Command.Dial(number) else Command.OpenTarget(Target.PHONE)
        }

        // الصوت
        if (has(n, UNMUTE)) return Command.Unmute
        if (has(n, MUTE)) return Command.Mute
        if (has(n, VOL_UP)) return Command.VolumeUp
        if (has(n, VOL_DOWN)) return Command.VolumeDown

        // الملاحة
        afterPrefix(n, text, NAVIGATE)?.let { rest ->
            if (rest.isEmpty()) return Command.Navigate("")
            targetOf(rest)?.let { return Command.OpenTarget(it) }
            return Command.Navigate(rest)
        }

        // البحث (وإذا كان عن مكان قريب نستخدم الخرائط)
        afterPrefix(n, text, SEARCH)?.let { rest ->
            if (rest.isNotEmpty() && has(normalize(rest), NEARBY)) return Command.Navigate(rest)
            return Command.Search(rest)
        }

        // فتح تطبيق أو وجهة
        afterPrefix(n, text, OPEN)?.let { rest ->
            val name = removeFillerWords(rest)
            if (name.isEmpty()) return Command.OpenApp("")
            targetOf(name)?.let { return Command.OpenTarget(it) }
            return Command.OpenApp(name)
        }

        // "أقرب محطة وقود" بدون فعل
        if (first == normalize("اقرب") || first == "nearest" || first == "closest") return Command.Navigate(text)

        // الوسائط
        if (has(n, NEXT)) return Command.MediaNext
        if (has(n, PREVIOUS)) return Command.MediaPrevious
        if (has(n, PAUSE)) return Command.MediaPause
        if (n in PLAY_ALONE || (first in PLAY_PREFIX && has(n, MUSIC_WORDS))) return Command.MediaPlay
        afterPrefix(n, text, PLAY_PREFIX)?.let { rest ->
            val name = removeFillerWords(rest)
            if (name.isNotEmpty()) return targetOf(name)?.let { Command.OpenTarget(it) } ?: Command.OpenApp(name)
        }

        // الوقت والتاريخ
        if (has(n, TIME)) return Command.CurrentTime
        if (has(n, DATE)) return Command.CurrentDate

        // اسم وجهة فقط: "الخرائط" / "home"
        targetOf(n)?.let { return Command.OpenTarget(it) }

        if (tokens.size <= 4 && has(n, THANKS)) return Command.Thanks
        if (tokens.size <= 4 && has(n, GREETING)) return Command.Greeting

        return Command.Unknown(raw)
    }
}
