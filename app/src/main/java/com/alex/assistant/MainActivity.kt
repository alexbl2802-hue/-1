package com.alex.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.alex.assistant.databinding.ActivityMainBinding
import java.util.Locale

/**
 * Голосовой ассистент "Alex" на встроенных движках Android:
 *  - SpeechRecognizer — распознавание речи (STT)
 *  - TextToSpeech     — озвучка ответа (TTS)
 * Тяжёлые ML-модели исходного JARVIS не нужны: всё работает на системных сервисах.
 */
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var isListening = false

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startListening()
            } else {
                setStatus(getString(R.string.status_no_mic))
                Toast.makeText(this, R.string.status_no_mic, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tts = TextToSpeech(this, this)

        binding.micButton.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                ensureMicThenListen()
            }
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus(getString(R.string.status_no_recognizer))
            binding.micButton.isEnabled = false
        }
    }

    // region TTS

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ru", "RU"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Русская озвучка недоступна — откатываемся на язык по умолчанию.
                tts?.setLanguage(Locale.getDefault())
            }
            ttsReady = true
        } else {
            ttsReady = false
            setStatus(getString(R.string.status_no_tts))
        }
    }

    private fun speak(text: String) {
        binding.responseText.text = text
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "alex-utterance")
        }
    }

    // endregion

    // region Speech recognition

    private fun ensureMicThenListen() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            startListening()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(recognitionListener)
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        isListening = true
        binding.micButton.setText(R.string.btn_stop)
        setStatus(getString(R.string.status_listening))
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        isListening = false
        binding.micButton.setText(R.string.btn_listen)
        setStatus(getString(R.string.status_idle))
        speechRecognizer?.stopListening()
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            isListening = false
            binding.micButton.setText(R.string.btn_listen)
            setStatus(errorMessage(error))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = firstResult(partialResults)
            if (!text.isNullOrBlank()) {
                binding.heardText.text = text
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            binding.micButton.setText(R.string.btn_listen)
            val text = firstResult(results)
            if (text.isNullOrBlank()) {
                setStatus(getString(R.string.status_nothing_heard))
                return
            }
            binding.heardText.text = text
            setStatus(getString(R.string.status_idle))
            handleCommand(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> getString(R.string.status_nothing_heard)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> getString(R.string.status_no_mic)
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> getString(R.string.status_network_error)
        else -> getString(R.string.status_error)
    }

    // endregion

    /** Простейший разбор команд. Расширяется по мере надобности. */
    private fun handleCommand(raw: String) {
        val cmd = raw.lowercase(Locale.getDefault()).trim()
        val response = when {
            cmd.containsAny("привет", "здравствуй", "хай") ->
                getString(R.string.reply_hello)

            cmd.containsAny("как дела", "как ты") ->
                getString(R.string.reply_how_are_you)

            cmd.containsAny("который час", "сколько времени", "время") -> {
                val now = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(java.util.Date())
                getString(R.string.reply_time, now)
            }

            cmd.containsAny("какой сегодня день", "какая дата", "дата", "число") -> {
                val today = java.text.SimpleDateFormat("d MMMM yyyy", Locale("ru", "RU"))
                    .format(java.util.Date())
                getString(R.string.reply_date, today)
            }

            cmd.containsAny("как тебя зовут", "кто ты", "твоё имя", "твое имя") ->
                getString(R.string.reply_name)

            cmd.containsAny("спасибо", "благодарю") ->
                getString(R.string.reply_thanks)

            cmd.containsAny("пока", "до свидания", "выключись") ->
                getString(R.string.reply_bye)

            else ->
                getString(R.string.reply_unknown, raw)
        }
        speak(response)
    }

    private fun setStatus(text: String) {
        binding.statusText.text = text
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

private fun String.containsAny(vararg needles: String): Boolean =
    needles.any { this.contains(it) }
