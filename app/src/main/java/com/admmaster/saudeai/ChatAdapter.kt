import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.admmaster.saudeai.databinding.ItemMessageApiBinding
import com.admmaster.saudeai.databinding.ItemMessageUserBinding

data class ChatMessage(val message: String, val isUser: Boolean) {
    val prefix: String
        get() = if (isUser) "Você:" else "Bot:"
}

class ChatAdapter(private val messages: MutableList<ChatMessage>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val USER_MESSAGE = 1
    private val API_MESSAGE = 2

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) USER_MESSAGE else API_MESSAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == USER_MESSAGE) {
            val binding = ItemMessageUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            UserMessageViewHolder(binding)
        } else {
            val binding = ItemMessageApiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ApiMessageViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (getItemViewType(position) == USER_MESSAGE) {
            (holder as UserMessageViewHolder).bind(message)
        } else {
            (holder as ApiMessageViewHolder).bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    class UserMessageViewHolder(private val binding: ItemMessageUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chatMessage: ChatMessage) {
            binding.textViewMessageUser.text = "${chatMessage.prefix} ${chatMessage.message}" // Adiciona o prefixo aqui
        }
    }

    class ApiMessageViewHolder(private val binding: ItemMessageApiBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chatMessage: ChatMessage) {
            binding.textViewMessageApi.text = "${chatMessage.prefix} ${chatMessage.message}" // Adiciona o prefixo aqui
        }
    }

    fun removeLastMessage() {
        if (messages.isNotEmpty()) {
            messages.removeAt(messages.size - 1) // Remove a última mensagem
            notifyItemRemoved(messages.size) // Notifica o adapter sobre a remoção
        }
    }

    fun updateLastMessage(newMessage: String) {
        if (messages.isNotEmpty()) {
            messages[messages.size - 1] = ChatMessage(newMessage, false) // Atualiza a última mensagem
            notifyItemChanged(messages.size - 1) // Notifica que a última mensagem foi alterada
        }
    }

}
