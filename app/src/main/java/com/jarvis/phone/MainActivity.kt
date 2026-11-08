package com.jarvis.phone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var btn: Button
    private lateinit var jarvis: Jarvis

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    private var running = false
    private var pendingSpeech = 0
    private var awakeUntil = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F14"))
            setPadding((16 * dp).toInt(), (40 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt())
        }
        val title = TextView(this).apply {
            text = "J.A.R.V.I.S"
            setTextColor(Color.parseColor("#4FC3F7"))
            textSize = 28f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        logView = TextView(this).apply {
            setTextColor(Color.parseColor("#C8D6E5"))
            textSize = 15f
            typeface = Typeface.MONOSPACE
            text = "Скажи «Джарвис, открой телеграм»\n\n"
        }
        scroll = ScrollView(this).apply { addView(logView) }
        btn = Button(this).apply {
            text = "▶ Включить"
            textSize = 18f
            setOnClickListener { toggle() }
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = (16 * dp).toInt(); bottomMargin = (16 * dp).toInt() })
        root.addView(btn, LinearLayout.LayoutParams(-1, (64 * dp).toInt()))
        setContentView(root)

        jarvis = Jarvis(this) { say(it) }
        tts = TextToSpeech(this, this)
        requestPermissions(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS
            ), 1
        )
    }

    private fun log(s: String) {
        logView.append(s + "\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) { log("⚠ Синтез речи недоступен"); return }
        val r = tts?.setLanguage(Locale("ru", "RU"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            log("⚠ Нет русского голоса: Настройки → Синтез речи → установить русский")
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) = finished()
            @Deprecated("deprecated")
            override fun onError(id: String?) = finished()
        })
        ttsReady = true
    }

    private fun finished() {
        main.post {
            pendingSpeech = (pendingSpeech - 1).coerceAtLeast(0)
            if (pendingSpeech == 0) listen(300)
        }
    }

    private fun say(text: String) {
        log("Джарвис: $text")
        if (!ttsReady) return
        pendingSpeech++
        recognizer?.cancel()
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "u" + System.nanoTime())
    }

    private fun toggle() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            log("⚠ На телефоне нет службы распознавания речи (нужно приложение Google)")
            return
        }
        running = !running
        btn.text = if (running) "■ Выключить" else "▶ Включить"
        if (running) {
            log("● Слушаю… (скажи «Джарвис»)")
            listen(0)
        } else {
            recognizer?.cancel()
            log("○ Остановлен")
        }
    }

    private fun listen(delayMs: Long) {
        main.postDelayed({
            if (!running || pendingSpeech > 0) return@postDelayed
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(listener)
                }
            }
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            recognizer?.startListening(i)
        }, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) heard(text)
            if (pendingSpeech == 0) listen(200)
        }

        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                running = false
                btn.text = "▶ Включить"
                log("⚠ Нет доступа к микрофону")
                return
            }
            listen(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 800 else 300)
        }

        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(p: Bundle?) {}
        override fun onEvent(t: Int, p: Bundle?) {}
    }

    private fun heard(text: String) {
        val low = text.lowercase(Locale.ROOT)
        val now = SystemClock.elapsedRealtime()
        val hasWake = Jarvis.WAKE.any { low.contains(it) }
        if (!hasWake && now > awakeUntil) return

        log("Вы: $text")
        if (jarvis.clean(text).isEmpty()) {
            awakeUntil = now + 8000
            say("Слушаю, сэр")
        } else {
            awakeUntil = 0
            jarvis.handle(text)
        }
    }

    override fun onDestroy() {
        running = false
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
