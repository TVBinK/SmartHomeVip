package com.example.smarthome

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smarthome.databinding.ActivityMainBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private var isListeningForCommands = false
    private lateinit var database: FirebaseDatabase
    private var isDoorOpen = false // Biến lưu trạng thái cửa
    private var isFanOn = false // Trạng thái quạt
    private var isLightOn = false // Trạng thái đèn
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Khởi tạo View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Yêu cầu quyền microphone
        requestMicrophonePermission()
        database = FirebaseDatabase.getInstance()

        // Khởi tạo TextToSpeech
        tts = TextToSpeech(this, this)
        // đồng bộ trạng thái
        syncDoorStatus()
        syncFanStatus()
        syncLightStatus()
        syncTemperatureAndHumidity()


        // Khởi tạo SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.statusVoice.text = "Hệ thống sẵn sàng lắng nghe..."
            }

            override fun onBeginningOfSpeech() {
                binding.statusVoice.text = "Đang lắng nghe..."
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                binding.statusVoice.text = "Kết thúc lắng nghe, xử lý..."
            }

            override fun onError(error: Int) {
                binding.statusVoice.text = "Lỗi khi lắng nghe, đang khởi động lại..."
                startListening()
            }

            override fun onResults(results: Bundle?) {
                val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                binding.statusVoice.text = "Kết quả: $spokenText"
                handleSpeechInput(spokenText)
                startListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partialText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                binding.statusVoice.text = "Đang nghe: $partialText"
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Bắt đầu lắng nghe
        startListening()

        // Xử lý sự kiện nút doorButton
        binding.doorButton.setOnClickListener {
            val doorStatusRef = database.reference.child("door").child("isOn")

            if (isDoorOpen) {
                // Mở cửa
                doorStatusRef.setValue(false)
                Toast.makeText(this, "Đã mở cửa!", Toast.LENGTH_SHORT).show()
                speakOut("Ok, đã mở cửa rồi nhé")
                binding.statusDoor.text = "Trạng thái cửa: Đóng"
            } else {
                // Đóng cửa
                doorStatusRef.setValue(true)
                Toast.makeText(this, "Đã đóng cửa!", Toast.LENGTH_SHORT).show()
                speakOut("Ok, đã đóng cửa rồi nhé")
                binding.statusDoor.text = "Trạng thái cửa: Mở"
            }

            isDoorOpen = !isDoorOpen
        }
        binding.fanButton.setOnClickListener {
            val fanStatusRef = database.reference.child("fan").child("isOn")
            if (isFanOn) {
                fanStatusRef.setValue(false)
                Toast.makeText(this, "Đã bật quạt!", Toast.LENGTH_SHORT).show()
                speakOut("Đã bật quạt.")
                binding.statusFan.text = "Trạng thái quạt: Bật"
            } else {
                fanStatusRef.setValue(true)
                Toast.makeText(this, "Đã tắt quạt!", Toast.LENGTH_SHORT).show()
                speakOut("Đã tắt quạt.")
                binding.statusFan.text = "Trạng thái quạt: Tắt"
            }
            isFanOn = !isFanOn
        }

        // Xử lý sự kiện lightButton
        binding.lightButton.setOnClickListener {
            val lightStatusRef = database.reference.child("light").child("isOn")
            if (isLightOn) {
                lightStatusRef.setValue(false)
                Toast.makeText(this, "Đã bật đèn!", Toast.LENGTH_SHORT).show()
                speakOut("Đã bật đèn.")
                binding.statusLight.text = "Trạng thái đèn: Bật"
            } else {
                lightStatusRef.setValue(true)
                Toast.makeText(this, "Đã tắt đèn!", Toast.LENGTH_SHORT).show()
                speakOut("Đã tắt đèn.")
                binding.statusLight.text = "Trạng thái đèn: Tắt"
            }
            isLightOn = !isLightOn
        }

    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

        // Bắt đầu lắng nghe
        speechRecognizer.startListening(intent)

        // Tạo Handler để dừng việc lắng nghe sau 5 giây
        Handler(mainLooper).postDelayed({
            // Dừng SpeechRecognizer sau 5 giây
            speechRecognizer.stopListening()
            binding.statusVoice.text = "Dừng lắng nghe sau 5 giây"
        }, 5000)
    }

    private fun handleSpeechInput(spokenText: String?) {
        if (spokenText == null) return

        if (!isListeningForCommands) {
            if (spokenText.contains("alo alo", ignoreCase = true)) {
                isListeningForCommands = true
                Toast.makeText(this, "Sẵn sàng nhận lệnh!", Toast.LENGTH_SHORT).show()
                binding.statusVoice.text = "Đã nhận được lệnh."
            }
        } else {
            val doorStatusRef = database.reference.child("door").child("isOn")
            val fanStatusRef = database.reference.child("fan").child("isOn")
            val lightStatusRef = database.reference.child("light").child("isOn")
            when {
                //Điều khiển cửa
                spokenText.contains("mở cửa", ignoreCase = true) -> {
                    Toast.makeText(this, "Đã mở cửa!", Toast.LENGTH_SHORT).show()
                    binding.statusVoice.text = "Lệnh: Mở cửa"
                    doorStatusRef.setValue(false) // Cập nhật trạng thái "mở cửa"
                    speakOut("Ok tôi đã mở cửa rồi nhé")
                    isListeningForCommands = false
                    isDoorOpen = true // Cập nhật trạng thái cửa
                }
                spokenText.contains("đóng cửa", ignoreCase = true) -> {
                    Toast.makeText(this, "Đã đóng cửa!", Toast.LENGTH_SHORT).show()
                    binding.statusVoice.text = "Lệnh: Đóng cửa"
                    doorStatusRef.setValue(true) // Cập nhật trạng thái "đóng cửa"
                    speakOut("Ok tôi đã đóng cửa rồi nhé")
                    isListeningForCommands = false
                    isDoorOpen = false // Cập nhật trạng thái cửa
                }
                // Điều khiển quạt
                spokenText.contains("bật quạt", ignoreCase = true) -> {
                    Toast.makeText(this, "Đã bật quạt!", Toast.LENGTH_SHORT).show()
                    binding.statusVoice.text = "Lệnh: Bật quạt"
                    fanStatusRef.setValue(false) // Cập nhật trạng thái "bật quạt"
                    speakOut("Tôi đã bật quạt rồi nhé")
                    isListeningForCommands = false
                    isFanOn = true // Cập nhật trạng thái quạt
                }
                spokenText.contains("tắt quạt", ignoreCase = true) -> {
                    Toast.makeText(this, "Đã tắt quạt!", Toast.LENGTH_SHORT).show()
                    binding.statusVoice.text = "Lệnh: Tắt quạt"
                    fanStatusRef.setValue(true) // Cập nhật trạng thái "tắt quạt"
                    speakOut("Tôi đã tắt quạt rồi nhé")
                    isListeningForCommands = false
                    isFanOn = false // Cập nhật trạng thái quạt
                }

                // Điều khiển đèn
                spokenText.contains("bật đèn", ignoreCase = true) -> {
                    Toast.makeText(this, "Đã bật đèn!", Toast.LENGTH_SHORT).show()
                    binding.statusVoice.text = "Lệnh: Bật đèn"
                    lightStatusRef.setValue(false) // Cập nhật trạng thái "bật đèn"
                    speakOut("Đèn đã được bật rồi nhé")
                    isListeningForCommands = false
                    isLightOn = true // Cập nhật trạng thái đèn
                }
                spokenText.contains("tắt đèn", ignoreCase = true) -> {
                    Toast.makeText(this, "Đã tắt đèn!", Toast.LENGTH_SHORT).show()
                    binding.statusVoice.text = "Lệnh: Tắt đèn"
                    lightStatusRef.setValue(true) // Cập nhật trạng thái "tắt đèn"
                    speakOut("Đèn đã được tắt rồi nhé")
                    isListeningForCommands = false
                    isLightOn = false // Cập nhật trạng thái đèn
                }

                else -> {
                    Toast.makeText(this, "Không nhận diện được yêu cầu.", Toast.LENGTH_SHORT).show()
                    binding.statusVoice.text = "Không nhận diện được lệnh."
                }
            }
        }
    }

    private fun requestMicrophonePermission() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 200)
        }
    }

    private fun speakOut(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("vi", "VN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Ngôn ngữ không được hỗ trợ!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Khởi tạo TTS thất bại!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        tts.stop()
        tts.shutdown()
    }
    private fun syncDoorStatus() {
        val doorStatusRef = database.reference.child("door").child("isOn")
        doorStatusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isDoorOpen = snapshot.getValue(Boolean::class.java) ?: false
                if (isDoorOpen) {
                    binding.statusDoor.text = "Trạng thái cửa: Đóng"
                } else {
                    binding.statusDoor.text = "Trạng thái cửa: Mở"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Không thể đồng bộ trạng thái cửa: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
    private fun syncFanStatus() {
        val fanStatusRef = database.reference.child("fan").child("isOn")
        fanStatusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isFanOn = snapshot.getValue(Boolean::class.java) ?: false
                binding.statusFan.text = if (isFanOn) "Trạng thái quạt: Tắt" else "Trạng thái quạt: Bật"
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Không thể đồng bộ trạng thái quạt: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun syncLightStatus() {
        val lightStatusRef = database.reference.child("light").child("isOn")
        lightStatusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isLightOn = snapshot.getValue(Boolean::class.java) ?: false
                binding.statusLight.text = if (isLightOn) "Trạng thái đèn: Tắt" else "Trạng thái đèn: Bật"
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Không thể đồng bộ trạng thái đèn: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
    private fun syncTemperatureAndHumidity() {
        val environmentRef = database.reference.child("environment")

        environmentRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Lấy dữ liệu từ Firebase
                val temperature = snapshot.child("temperature").getValue(Double::class.java) ?: 0.0
                val humidity = snapshot.child("humidity").getValue(Double::class.java) ?: 0.0

                // Cập nhật giao diện
                binding.temperatureStatus.text = "Nhiệt độ: $temperature °C"
                binding.humidityStatus.text = "Độ ẩm: $humidity %"
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Không thể đồng bộ nhiệt độ/độ ẩm: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

}
