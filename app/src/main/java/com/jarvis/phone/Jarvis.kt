package com.jarvis.phone

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Jarvis(private val a: Activity, private val say: (String) -> Unit) {

    private val am = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        val WAKE = listOf("джарвис", "жарвис", "джарвиз", "джервис", "jarvis")

        val ALIASES = mapOf(
            "телеграм" to listOf("telegram"),
            "ютуб" to listOf("youtube"),
            "хром" to listOf("chrome"),
            "гугл" to listOf("google"),
            "вацап" to listOf("whatsapp"),
            "ватсап" to listOf("whatsapp"),
            "инстаграм" to listOf("instagram"),
            "тикток" to listOf("tiktok"),
            "спотифай" to listOf("spotify"),
            "камера" to listOf("camera", "камера"),
            "карты" to listOf("maps", "карты"),
            "калькулятор" to listOf("calculator", "калькулятор"),
            "галерея" to listOf("gallery", "photos", "галерея", "фото"),
            "дискорд" to listOf("discord"),
            "вконтакте" to listOf("vk", "вконтакте"),
            "почта" to listOf("gmail", "mail", "почта"),
            "музыка" to listOf("music", "музыка")
        )
    }

    fun clean(text: String): String {
        var t = text.lowercase(Locale.ROOT)
        WAKE.forEach { t = t.replace(it, "") }
        return t.replace(Regex("[,!?]"), " ").replace(Regex("\\s+"), " ").trim().trim('.')
    }

    fun handle(text: String) {
        val t = clean(text)
        try {
            route(t)
        } catch (e: Exception) {
            say("Не получилось, сэр")
        }
    }

    private fun route(t: String) {
        when {
            t.contains("фонар") -> torch(!(t.contains("выключ") || t.contains("погаси")))

            Regex("громкость (?:на )?(\\d+)").containsMatchIn(t) -> {
                val p = Regex("громкость (?:на )?(\\d+)").find(t)!!.groupValues[1].toInt().coerceIn(0, 100)
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, max * p / 100, AudioManager.FLAG_SHOW_UI)
                say("Громкость $p процентов")
            }
            t.contains("громче") -> { vol(AudioManager.ADJUST_RAISE); say("Громче") }
            t.contains("тише") -> { vol(AudioManager.ADJUST_LOWER); say("Тише") }
            t.contains("без звука") || t.contains("выключи звук") -> { vol(AudioManager.ADJUST_MUTE); say("Звук выключен") }
            t.contains("включи звук") -> { vol(AudioManager.ADJUST_UNMUTE); say("Звук включён") }

            t.contains("пауза") || t.contains("останови музыку") -> { media(KeyEvent.KEYCODE_MEDIA_PAUSE); say("Пауза") }
            t.contains("продолж") || t.contains("играй") || t.contains("воспроизведи") -> { media(KeyEvent.KEYCODE_MEDIA_PLAY); say("Играю") }
            t.contains("следующ") || t.contains("дальше") -> { media(KeyEvent.KEYCODE_MEDIA_NEXT); say("Следующий трек") }
            t.contains("предыдущ") || t.contains("назад") -> { media(KeyEvent.KEYCODE_MEDIA_PREVIOUS); say("Предыдущий трек") }

            t.contains("сколько времени") || t.contains("который час") || t == "время" ->
                say("Сейчас " + SimpleDateFormat("H:mm", Locale("ru")).format(Date()))

            t.contains("будильник") -> {
                val n = Regex("\\d{1,2}").findAll(t).map { it.value.toInt() }.toList()
                if (n.isEmpty()) { say("На какое время, сэр?"); return }
                val h = n[0].coerceIn(0, 23)
                val m = (n.getOrNull(1) ?: 0).coerceIn(0, 59)
                a.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, h)
                    putExtra(AlarmClock.EXTRA_MINUTES, m)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                })
                say("Будильник на $h часов $m минут")
            }
            t.contains("таймер") -> {
                val n = Regex("\\d+").find(t)?.value?.toInt()
                if (n == null) { say("На сколько, сэр?"); return }
                val sec = when {
                    t.contains("секунд") -> n
                    t.contains("час") -> n * 3600
                    else -> n * 60
                }
                a.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, sec)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                })
                say("Таймер запущен")
            }

            t.startsWith("позвони") -> call(t.removePrefix("позвони").trim().removePrefix("по номеру").trim())

            Regex("^(найди|поищи|загугли|погугли)\\s+(.+)").containsMatchIn(t) -> {
                val q = Regex("^(найди|поищи|загугли|погугли)\\s+(.+)").find(t)!!.groupValues[2]
                start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
                say("Ищу $q")
            }

            t.startsWith("открой сайт ") || t.startsWith("зайди на ") -> {
                val q = t.removePrefix("открой сайт ").removePrefix("зайди на ").replace(" ", "")
                val url = if (q.contains(".")) "https://$q" else "https://www.google.com/search?q=" + Uri.encode(q)
                start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                say("Открываю")
            }

            t.contains("вай фай") || t.contains("вайфай") || t.contains("wi-fi") || t.contains("wifi") -> {
                start(Intent(Settings.ACTION_WIFI_SETTINGS)); say("Настройки вай-фай")
            }
            t.contains("блютуз") || t.contains("bluetooth") -> {
                start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); say("Настройки блютуз")
            }
            t.contains("настройк") -> { start(Intent(Settings.ACTION_SETTINGS)); say("Открываю настройки") }

            Regex("^(открой|запусти|включи)\\s+(.+)").containsMatchIn(t) -> {
                val name = Regex("^(открой|запусти|включи)\\s+(.+)").find(t)!!.groupValues[2].removePrefix("приложение ").trim()
                openApp(name)
            }

            else -> say("Не понял команду")
        }
    }

    private fun start(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        a.startActivity(i)
    }

    private fun vol(dir: Int) =
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)

    private fun media(code: Int) {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun torch(on: Boolean) {
        val cm = a.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) { say("Фонарика нет, сэр"); return }
        cm.setTorchMode(id, on)
        say(if (on) "Фонарик включён" else "Фонарик выключен")
    }

    private fun openApp(name: String) {
        val pm = a.packageManager
        val cands = mutableListOf(name)
        if (name.length > 4) cands += name.dropLast(1)
        ALIASES.forEach { (k, v) -> if (name.startsWith(k.dropLast(1))) cands += v }

        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        val hit = apps.firstOrNull { r ->
            val label = r.loadLabel(pm).toString().lowercase(Locale.ROOT)
            label.length >= 3 && cands.any { label.contains(it) || it.contains(label) }
        }
        val intent = hit?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
        if (intent == null) {
            say("Не нашёл приложение $name")
        } else {
            start(intent)
            say("Открываю $name")
        }
    }

    private fun call(who: String) {
        if (who.isEmpty()) { say("Кому позвонить, сэр?"); return }
        val digits = who.filter { it.isDigit() }
        val number = if (digits.length >= 5) digits else findContact(who)
        if (number == null) { say("Не нашёл контакт $who"); return }
        val uri = Uri.fromParts("tel", number, null)
        val canCall = a.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        start(Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
        say("Звоню $who")
    }

    private fun findContact(who: String): String? {
        if (a.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val stems = listOf(who, who.dropLast(1), who.dropLast(2)).filter { it.length >= 2 }
        a.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ), null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0)?.lowercase(Locale.ROOT) ?: continue
                if (stems.any { n.contains(it) }) return c.getString(1)
            }
        }
        return null
    }
}
