package dev.snbv2.ai;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.ToString;

/**
 * Container for chat items in history
 * 
 * @author Brian Jimerson
 */
@Getter
@ToString
public class ChatHistory {

    List<String> chatHistory;

    /**
     * Default constructor
     */
    public ChatHistory() {
        chatHistory = new ArrayList<>();
    }

    /**
     * Adds an item to the chat history
     * 
     * @param chat The chat item to add
     */
    public void addChatItem(String chat) {
        this.chatHistory.add(0, chat);
    }

}
