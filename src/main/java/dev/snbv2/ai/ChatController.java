package dev.snbv2.ai;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

/**
 * Main application MVC controller.
 * 
 * @author Brian Jimerson
 */
@Controller
public class ChatController {

    @Autowired
    @Qualifier("openAiChatClient")
    ChatClient openAiChatClient;

    @Autowired
    @Qualifier("ollamaChatClient")
    ChatClient ollamaChatClient;

    ChatClient openAiOverrideChatClient;

    @Value("${openai.base-url.override}")
    String openAiBaseUrlOverride;

    String aiModel;

    /**
     * Default view controller.
     * 
     * @param model   The model to use for the view.
     * @param session The HTTP session to use.
     * @return The view name to use (home.html).
     */
    @GetMapping(path = { "/", "/index" })
    public String index(Model model, HttpSession session) {

        Chat chat = new Chat();
        model.addAttribute("chat", chat);
        setViewAttributes(session, model, aiModel == null ? "openai" : aiModel);

        return "home";
    }

    /**
     * Controller method to handle a chat prompt POST.
     * 
     * @param userPrompt The prompt entered by the user.
     * @param model      The model to use for the view.
     * @param session    The HTTP session to use.
     * @return The view name to use (home.html).
     */
    @PostMapping("/chat")
    public String chat(@ModelAttribute Chat userPrompt, Model model, HttpSession session) {

        setViewAttributes(session, model, userPrompt.getAiModel());
        ChatHistory chatHistory = (ChatHistory) session.getAttribute("chatHistory");
        chatHistory.addChatItem(userPrompt.getPrompt());

        ChatClient chatClient;
        if (openAiOverrideChatClient != null) {
            chatClient = openAiOverrideChatClient;
        } else {
            switch (userPrompt.getAiModel()) {
                case "openai":
                    chatClient = openAiChatClient;
                    break;
                case "ollama":
                    chatClient = ollamaChatClient;
                    break;
                default:
                    chatClient = openAiChatClient;
                    break;
            }
        }

        ChatResponse chatResponse = chatClient.prompt().user(userPrompt.getPrompt()).call().chatResponse();
        String chatContent = chatResponse.getResult().getOutput().getText();
        model.addAttribute("content", chatContent);
        model.addAttribute("chatPrompt", userPrompt.getPrompt());

        return "home";
    }

    /**
     * Controller method to change the LLM model being used.
     * 
     * @param aiModel The AI model to use. Currently, 'openai' and 'ollama' are
     *                supported.
     * @param model   The model to use for the view.
     * @param session The HTTP session to use.
     * @return The view to use (home.html).
     */
    @GetMapping("/aimodel/{aiModel}")
    public String changeAiModel(@PathVariable String aiModel, Model model, HttpSession session) {

        this.aiModel = aiModel;
        setViewAttributes(session, model, aiModel);

        return "home";

    }

    /**
     * Controller method to override the API key to use. The
     * openAiOverrideChatClient is built with the API key so that it can be used
     * instead of the default openAiChatClient.
     * 
     * @param model   The model to use for the view
     * @param session The HTTP session to use
     * @return The view to use (home.html).
     */
    @PostMapping("/overrideApiKey")
    public String overrideApiKey(@RequestParam Map<String, String> body, Model model, HttpSession session) {

        OpenAiApi openAiApi = new OpenAiApi(openAiBaseUrlOverride, body.get("apiKey"));
        OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
                .model("gpt-4o")
                .temperature(0.4)
                .maxTokens(200)
                .build();
        OpenAiChatModel openAiChatModel = new OpenAiChatModel(openAiApi, openAiChatOptions);
        ChatClient.Builder openAiClientBuilder = ChatClient.builder(openAiChatModel);
        openAiClientBuilder.defaultAdvisors(new ErrorHandlingAdvisor());
        openAiOverrideChatClient = openAiClientBuilder.build();

        aiModel = "openai";
        setViewAttributes(session, model, "openai");

        return "home";
    }

    /**
     * Removes the API key override by setting the openAiOverrideChatClient to null.
     * 
     * @param model The model to use.
     * @param session The HTTP session to use.
     * @return The view to use (home.html).
     */
    @GetMapping("/removeApiKeyOverride")
    public String removeApiKeyOverride(Model model, HttpSession session) {

        openAiOverrideChatClient = null;
        setViewAttributes(session, model, aiModel == null ? "openai" : aiModel);

        return "home";
    }

    /**
     * Ensures that there is the attributes the view needs exist.
     * 
     * @param session The HTTP session to verify.
     * @param model   The model to use for the view.
     * @param aiModel The AI model that is being used.
     */
    private void setViewAttributes(HttpSession session, Model model, String aiModel) {
        if (session.getAttribute("chatHistory") == null) {
            session.setAttribute("chatHistory", new ChatHistory());
        }

        model.addAttribute("aiModel", aiModel);
        model.addAttribute("apiKeyOverride", openAiOverrideChatClient != null);
    }

}
