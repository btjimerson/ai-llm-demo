package dev.snbv2.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration class for the application.
 * 
 * @author Brian Jimerson
 */
@Configuration
public class AiDemoConfiguration {

    @Value("${openai.api-key}")
    String openAiApiKey;

    @Value("${openai.base-url}")
    String openAiBaseUrl;

    @Value("${ollama.base-url}")
    String ollamaBaseUrl;

    @Value("${ollama.model}")
    String ollamaModel;

    /**
     * Creates a REST client builder with custom interceptors to be used by chat
     * clients.
     * 
     * @return A REST client builder for use by chat clients.
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        RestClient.Builder builder = RestClient
                .builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.simple().build())
                .requestInterceptor(new ClientAuthorizationInterceptor())
                .requestInterceptor(new ChatClientLoggingInterceptor());
        return builder;
    }

    /**
     * Creates a chat client bean for OpenAI
     * 
     * @return An OpenAI chat client
     */
    @Bean
    @Qualifier("openAiChatClient")
    public ChatClient openAiChatClient(RestClient.Builder restClientBuilder) {

        OpenAiApi openAiApi = new OpenAiApi(openAiBaseUrl, openAiApiKey, restClientBuilder, WebClient.builder());
        OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
                .model("gpt-4o")
                .temperature(0.4)
                .maxTokens(200)
                .build();
        OpenAiChatModel openAiChatModel = new OpenAiChatModel(openAiApi, openAiChatOptions);
        ChatClient.Builder openAiClientBuilder = ChatClient.builder(openAiChatModel);
        openAiClientBuilder.defaultAdvisors(new ErrorHandlingAdvisor());
        return openAiClientBuilder.build();

    }

    /**
     * Creates a chat client bean for Ollama
     * 
     * @return An Ollama chat client
     */
    @Bean
    @Qualifier("ollamaChatClient")
    public ChatClient ollamaChatClient(RestClient.Builder restClientBuilder) {

        OllamaApi ollamaApi = new OllamaApi(ollamaBaseUrl, restClientBuilder, WebClient.builder());
        OllamaOptions ollamaOptions = OllamaOptions.builder().model(ollamaModel).build();
        OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(ollamaOptions)
                .modelManagementOptions(ModelManagementOptions.defaults())
                .build();
        ChatClient.Builder ollamaClientBuilder = ChatClient.builder(ollamaChatModel);
        return ollamaClientBuilder.build();

    }

    /**
     * Creates a FilterRegistrationBean for an AuthorizationFilter.
     * 
     * @return A FilterRegistrationBean for an AuthorizationFilter.
     */
    @Bean
    public FilterRegistrationBean<AuthorizationFilter> filterRegistrationBean() {

        FilterRegistrationBean<AuthorizationFilter> registration = new FilterRegistrationBean<AuthorizationFilter>();
        registration.setFilter(new AuthorizationFilter());
        registration.addUrlPatterns("/*");
        registration.setName("tokenRequestFilter");
        return registration;

    }

}
