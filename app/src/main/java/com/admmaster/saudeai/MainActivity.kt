package com.admmaster.saudeai

import ChatAdapter
import ChatMessage
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.admmaster.saudeai.databinding.ActivityMainBinding
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter
    private var sessionId: String? = null
    private lateinit var speechRecognizer: SpeechRecognizer
    private val REQUEST_RECORD_AUDIO_PERMISSION = 1

    // Variáveis para o temporizador
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var startTime: Long = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Usando View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatAdapter = ChatAdapter(mutableListOf())
        binding.recyclerViewChat.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }

        // Inicializa o layout com apenas o botão de áudio visível
        binding.buttonSend.visibility = View.GONE
        binding.buttonAudio.visibility = View.VISIBLE

        // Observa alterações no campo de entrada de mensagem
        binding.editTextMessage.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) {
                    binding.buttonSend.visibility = View.GONE
                    binding.buttonAudio.visibility = View.VISIBLE
                } else {
                    binding.buttonSend.visibility = View.VISIBLE
                    binding.buttonAudio.visibility = View.GONE
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Configuração do botão de envio de texto
        binding.buttonSend.setOnClickListener {
            val message = binding.editTextMessage.text.toString().trim()
            if (!TextUtils.isEmpty(message)) {
                sendMessage(message)
                binding.editTextMessage.text.clear() // Limpa o campo de entrada após enviar
            }
        }

        // Configuração do botão de áudio com temporizador
        binding.buttonAudio.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    checkAudioPermissionAndStartListening()
                    startTimer() // Inicia o temporizador
                    true
                }
                MotionEvent.ACTION_UP -> {
                    speechRecognizer.stopListening() // Para o reconhecimento ao soltar o botão
                    stopTimer() // Para o temporizador
                    true
                }
                else -> false
            }
        }

        // Inicialização do SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.let {
                    val recognizedText = it[0]  // Pega o primeiro resultado de reconhecimento
                    sendMessage(recognizedText)  // Envia como mensagem de texto
                }
            }

            override fun onError(error: Int) {
                Toast.makeText(this@MainActivity, "Erro no reconhecimento de áudio", Toast.LENGTH_SHORT).show()
            }

            // Outros métodos vazios do RecognitionListener que não precisam ser implementados
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun checkAudioPermissionAndStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        }
        speechRecognizer.startListening(intent)
    }

    private fun startTimer() {
        binding.editTextMessage.visibility = View.GONE // Oculta o campo de entrada de texto
        binding.textViewTimer.visibility = View.VISIBLE // Mostra o contador de gravação
        startTime = System.currentTimeMillis()

        timerHandler = Handler(Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                binding.textViewTimer.text = "Gravando áudio... ${elapsedSeconds}s"
                timerHandler?.postDelayed(this, 1000)
            }
        }
        timerHandler?.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerHandler?.removeCallbacks(timerRunnable!!)
        binding.textViewTimer.visibility = View.GONE // Oculta o contador de gravação
        binding.editTextMessage.visibility = View.VISIBLE // Mostra o campo de entrada de texto novamente
    }

    private fun sendMessage(message: String) {
        // Adiciona a mensagem do usuário imediatamente
        chatAdapter.addMessage(ChatMessage(message, true))
        binding.recyclerViewChat.scrollToPosition(chatAdapter.itemCount - 1)

        startThinkingIndicator()

        // Adiciona uma mensagem de "pensando" do bot
        val thinkingMessage = "Bot..."
        chatAdapter.addMessage(ChatMessage(thinkingMessage, false)) // Adiciona a mensagem de "pensando"
        binding.recyclerViewChat.scrollToPosition(chatAdapter.itemCount - 1)

        // Chama a API
        val chatService = ChatService.create()
        val request = ChatRequest(mensagem_usuario = message, session_id = sessionId)

        chatService.sendMessage(request).enqueue(object : retrofit2.Callback<ChatResponse> {
            override fun onResponse(call: retrofit2.Call<ChatResponse>, response: retrofit2.Response<ChatResponse>) {
                stopThinkingIndicator()

                if (response.isSuccessful) {
                    response.body()?.let {
                        // Remove os ** da resposta do bot
                        val formattedResponse = it.resposta.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")


                        // Remove a última mensagem (pensando) antes de adicionar a resposta do bot
                        chatAdapter.removeLastMessage() // Método para remover a última mensagem
                        chatAdapter.addMessage(ChatMessage(formattedResponse, false)) // Adiciona a resposta do bot
                        sessionId = it.session_id
                        binding.recyclerViewChat.scrollToPosition(chatAdapter.itemCount - 1)
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Erro ao receber resposta", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: retrofit2.Call<ChatResponse>, t: Throwable) {
                stopThinkingIndicator()
                Toast.makeText(this@MainActivity, "Erro ao enviar mensagem", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private var thinkingHandler: Handler? = null
    private var thinkingRunnable: Runnable? = null

    private fun startThinkingIndicator() {
        val messages = listOf("", ".", "..", "...")
        var index = 0

        thinkingHandler = Handler(Looper.getMainLooper())
        thinkingRunnable = object : Runnable {
            override fun run() {
                chatAdapter.updateLastMessage(messages[index]) // Atualiza a última mensagem
                index = (index + 1) % messages.size
                thinkingHandler?.postDelayed(this, 500) // Troca a cada 500ms
            }
        }
        thinkingHandler?.post(thinkingRunnable!!)
    }

    private fun stopThinkingIndicator() {
        thinkingHandler?.removeCallbacks(thinkingRunnable!!)
        thinkingHandler = null
        thinkingRunnable = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                Toast.makeText(this, "Permissão de áudio negada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy() // Libera o SpeechRecognizer
    }
}

data class ChatRequest(
    @SerializedName("mensagem_usuario") val mensagem_usuario: String,
    @SerializedName("session_id") val session_id: String? = null
)

data class ChatResponse(
    @SerializedName("resposta") val resposta: String,
    @SerializedName("session_id") val session_id: String
)

interface ChatService {
    @Headers("Content-Type: application/json")
    @POST("api/chat")
    fun sendMessage(@Body request: ChatRequest): retrofit2.Call<ChatResponse>

    companion object {
        private const val BASE_URL = "https://admmaster.com.br/"

        fun create(): ChatService {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ChatService::class.java)
        }
    }
}