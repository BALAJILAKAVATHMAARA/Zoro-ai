package com.zoro.ai.commands

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Turns a spoken sentence into a real Android action. Returns what ZORO should say back. */
class CommandHandler(private val ctx: Context) {

    private var torchOn = false

    private val apps = mapOf(
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "maps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "telegram" to "org.telegram.messenger"
    )

    fun handle(raw: String): String {
        val t = raw.lowercase(Locale.ROOT).replace(".", "").trim()
            .removePrefix("hey zoro").trim(' ', ',')

        return when {
            t.contains("flashlight") || t.contains("torch") -> torch(!t.contains("off"))
            t.contains("volume") || t.contains("louder") || t.contains("quieter") -> volume(t)
            t.contains("alarm") -> alarm(t)
            t.startsWith("open ") || t.startsWith("launch ") -> openThing(t.substringAfter(" ").trim())
            t.contains("take a photo") || t.contains("take a picture") -> openCamera("Opening the camera. Tap the shutter to capture.")
            t.startsWith("play ") && t.contains("youtube") -> youtube(t)
            t.startsWith("search ") -> search(t)
            t.contains("battery") -> battery()
            t.contains("what time") || t.contains("current time") -> "It's ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}."
            t.contains("weather") -> {
                go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=weather+today")))
                "Here's today's weather."
            }
            t.contains("hello") || t.contains("hi zoro") || t == "hi" -> "Hello! How can I help you?"
            else -> "I heard: $t. I can't answer general questions yet. That arrives when the OpenAI brain is connected in the next phase."
        }
    }

    private fun go(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent); true
    } catch (e: Exception) { false }

    private fun openThing(name: String): String {
        if (name.startsWith("setting")) {
            go(Intent(Settings.ACTION_SETTINGS)); return "Opening settings."
        }
        if (name.contains("camera")) return openCamera("Opening the camera.")
        val key = apps.keys.firstOrNull { name.contains(it) }
            ?: return "I don't know how to open $name yet."
        val launch = ctx.packageManager.getLaunchIntentForPackage(apps.getValue(key))
            ?: return "$key isn't installed on this phone."
        return if (go(launch)) "Opening $key." else "I couldn't open $key."
    }

    private fun openCamera(msg: String): String =
        if (go(Intent(MediaStore.ACTION_IMAGE_CAPTURE))) msg else "I couldn't open the camera."

    private fun torch(on: Boolean): String = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "This phone has no flashlight."
        cm.setTorchMode(id, on); torchOn = on
        if (on) "Flashlight on." else "Flashlight off."
    } catch (e: Exception) { "I couldn't control the flashlight." }

    private fun volume(t: String): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val up = listOf("increase", "raise", "up", "louder").any { t.contains(it) }
        val down = listOf("decrease", "lower", "down", "reduce", "quieter").any { t.contains(it) }
        return when {
            up -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); "Volume up." }
            down -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); "Volume down." }
            else -> "Do you want the volume up or down?"
        }
    }

    private fun alarm(t: String): String {
        val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(t.substringAfter("alarm"))
            ?: return "What time should I set the alarm for?"
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        when (m.groupValues[3]) {
            "pm" -> if (h < 12) h += 12
            "am" -> if (h == 12) h = 0
        }
        if (h > 23 || min > 59) return "That time doesn't look right. Please say it again."
        val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, h)
            putExtra(AlarmClock.EXTRA_MINUTES, min)
            putExtra(AlarmClock.EXTRA_MESSAGE, "ZORO alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        return if (go(i)) "Alarm set for %d:%02d.".format(h, min) else "I couldn't set the alarm."
    }

    private fun youtube(t: String): String {
        val q = t.removePrefix("play ").replace("on youtube", "").trim()
        go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))))
        return "Showing $q on YouTube. Tap a video to play it."
    }

    private fun search(t: String): String {
        val q = t.removePrefix("search").replace("on google", "").replace("for", "").trim()
        if (q.isEmpty()) return "What should I search for?"
        go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
        return "Searching for $q."
    }

    private fun battery(): String {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return "Battery is at ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent."
    }
}
