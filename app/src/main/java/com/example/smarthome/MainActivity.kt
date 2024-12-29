package com.example.smarthome

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smarthome.databinding.ActivityMainBinding
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListeningForCommands = false
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Khởi tạo View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Yêu cầu quyền microphone
        requestMicrophonePermission()
        database = FirebaseDatabase.getInstance()

        // Khởi tạo SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.statusTextView.text = "Hệ thống sẵn sàng lắng nghe..."
            }

            override fun onBeginningOfSpeech() {
                binding.statusTextView.text = "Đang lắng nghe..."
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                binding.statusTextView.text = "Kết thúc lắng nghe, xử lý..."
            }

            override fun onError(error: Int) {
                binding.statusTextView.text = "Lỗi khi lắng nghe, đang khởi động lại..."
                startListening()
            }

            override fun onResults(results: Bundle?) {
                val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                binding.statusTextView.text = "Kết quả: $spokenText"
                handleSpeechInput(spokenText)
                startListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partialText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                binding.statusTextView.text = "Đang nghe: $partialText"
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Bắt đầu lắng nghe
        startListening()
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

        // Bắt đầu lắng nghe
        speechRecognizer.startListening(intent)

        // Tạo Handler để dừng việc lắng nghe sau 3 giây
        Handler(mainLooper).postDelayed({
            // Dừng SpeechRecognizer sau 3 giây
            speechRecognizer.stopListening()
            binding.statusTextView.text = "Dừng lắng nghe sau 3 giây"
        }, 3000) // 3000 milliseconds = 3 giây
    }


    private fun handleSpeechInput(spokenText: String?) {
        if (spokenText == null) return

        if (!isListeningForCommands) {
            if (spokenText.contains("alo alo", ignoreCase = true)) {
                isListeningForCommands = true
                Toast.makeText(this, "Sẵn sàng nhận lệnh!", Toast.LENGTH_SHORT).show()
                binding.statusTextView.text = "Đã nhận được lệnh."
            }
        } else {
            val doorStatusRef = database.reference.child("door").child("isOn")

            when {
                spokenText.contains("mở cửa", ignoreCase = true) -> {
                    Toast.makeText(this, "Đã mở cửa!", Toast.LENGTH_SHORT).show()
                    binding.statusTextView.text = "Lệnh: Mở cửa"
                    doorStatusRef.setValue(true) // Cập nhật trạng thái "mở cửa"
                    isListeningForCommands = false
                }
                spokenText.contains("đóng cửa", ignoreCase = true) -> {
                    Toast.makeText(this, "Đã đóng cửa!", Toast.LENGTH_SHORT).show()
                    binding.statusTextView.text = "Lệnh: Đóng cửa"
                    doorStatusRef.setValue(false) // Cập nhật trạng thái "đóng cửa"
                    isListeningForCommands = false
                }
                else -> {
                    Toast.makeText(this, "Không nhận diện được yêu cầu.", Toast.LENGTH_SHORT).show()
                    binding.statusTextView.text = "Không nhận diện được lệnh."
                }
            }
        }
    }

    private fun requestMicrophonePermission() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 200)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}