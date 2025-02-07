package dev.snbv2.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpSession;


@Controller
public class ChatController {

    @Autowired
    ChatClient chatClient;

    @Value("${llm.use-embeddings}")
	String useEmbeddings;
    
    @GetMapping(path={"/", "/index"})
    public String index(Model model, HttpSession session) {
        
        Chat chat = new Chat();
        model.addAttribute("chat", chat);
        model.addAttribute("useEmbeddings", Boolean.valueOf(useEmbeddings));
        if (session.getAttribute("chatHistory") == null) {
            session.setAttribute("chatHistory", new ChatHistory());
        }

        return "home";
    }

    @PostMapping("/chat")
    public String chat(@ModelAttribute Chat userPrompt, Model model, HttpSession session) {

        if (session.getAttribute("chatHistory") == null) {
            session.setAttribute("chatHistory", new ChatHistory());
        }
        ChatHistory chatHistory = (ChatHistory) session.getAttribute("chatHistory");
        chatHistory.addChatItem(userPrompt.getPrompt());

        String chatContent = chatClient.prompt().user(userPrompt.getPrompt()).call().content();
        model.addAttribute("content", chatContent);

        model.addAttribute("chatPrompt", userPrompt.getPrompt());
        model.addAttribute("useEmbeddings", Boolean.valueOf(useEmbeddings));

        return "home";
    }
}
