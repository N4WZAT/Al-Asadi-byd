package com.hussein.byd.voiceassistant

/** الوجهات المعروفة التي يمكن فتحها بالصوت. */
enum class Target { MAPS, MUSIC, PHONE, SETTINGS, BLUETOOTH, WIFI, HOME }

/** الأوامر المحلية التي يفهمها المساعد. */
sealed class Command {
    data class OpenApp(val name: String) : Command()
    data class OpenTarget(val target: Target) : Command()
    data class Navigate(val place: String) : Command()
    data class Search(val query: String) : Command()
    data class Dial(val number: String) : Command()

    object VolumeUp : Command()
    object VolumeDown : Command()
    object Mute : Command()
    object Unmute : Command()

    object MediaPlay : Command()
    object MediaPause : Command()
    object MediaNext : Command()
    object MediaPrevious : Command()

    object CurrentTime : Command()
    object CurrentDate : Command()

    object Help : Command()
    object Greeting : Command()
    object Thanks : Command()
    object Developer : Command()

    data class Unknown(val text: String) : Command()
}
