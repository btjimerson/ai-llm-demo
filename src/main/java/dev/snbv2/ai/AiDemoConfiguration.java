package dev.snbv2.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

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

    /**
     * Creates a RestClient (wrapped by a ChatClient) with specific client
     * interceptors attached.
     * 
     * @param configurer The Rest Client Configurer to use
     * @return A RestClient Builder with the specific interceptors.
     */
    @Bean
    @Scope("prototype")
    RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
        RestClient.Builder builder = RestClient
                .builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.simple().build())
                .requestInterceptor(new ClientAuthorizationInterceptor())
                .requestInterceptor(new ChatClientLoggingInterceptor());

        return configurer.configure(builder);

    }

    @Bean
    public RestTemplate restTemplate() {

        RestTemplate restTemplate = new RestTemplate();
        List<ClientHttpRequestInterceptor> interceptors = restTemplate.getInterceptors();
        if (CollectionUtils.isEmpty(interceptors)) {
            interceptors = new ArrayList<>();
        }
        interceptors.add(new ChatClientLoggingInterceptor());
        restTemplate.setInterceptors(interceptors);
        return restTemplate;

    }

    /**
     * Creates a chat client bean for OpenAI
     * 
     * @return An OpenAI chat client
     */
    @Bean
    @Qualifier("openAiChatClient")
    public ChatClient openAiChatClient() {

        OpenAiApi openAiApi = new OpenAiApi(openAiBaseUrl, openAiApiKey);
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
    public ChatClient ollamaChatClient() {

        OllamaApi ollamaApi = new OllamaApi(ollamaBaseUrl);
        ModelManagementOptions modelManagementOptions = new ModelManagementOptions(
                PullModelStrategy.WHEN_MISSING, null, Duration.ofMinutes(5), Integer.valueOf(2));
        OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .modelManagementOptions(modelManagementOptions)
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
