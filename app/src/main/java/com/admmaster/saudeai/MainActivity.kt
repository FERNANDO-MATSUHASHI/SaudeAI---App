package com.admmaster.saudeai

import ChatAdapter
import ChatMessage
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

        binding.buttonSend.setOnClickListener {
            val message = binding.editTextMessage.text.toString().trim()
            if (!TextUtils.isEmpty(message)) {
                sendMessage(message)
                binding.editTextMessage.text.clear() // Limpa o campo de entrada após enviar
            }
        }
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
                        // Remove a última mensagem (pensando) antes de adicionar a resposta do bot
                        chatAdapter.removeLastMessage() // Método para remover a última mensagem
                        chatAdapter.addMessage(ChatMessage(it.resposta, false)) // Adiciona a resposta do bot
                        sessionId = it.session_id
                        binding.recyclerViewChat.scrollToPosition(chatAdapter.itemCount - 1)
                    }
                } else {
                    // Se a resposta não for bem-sucedida, você pode lidar com isso aqui
                    Toast.makeText(this@MainActivity, "Erro ao receber resposta", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: retrofit2.Call<ChatResponse>, t: Throwable) {

                stopThinkingIndicator()

                // Notifica erro na chamada
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
        private const val BASE_URL = "https://chatllama-tw11.onrender.com/"

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
