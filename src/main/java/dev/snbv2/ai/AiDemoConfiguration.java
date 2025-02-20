package dev.snbv2.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

/**
 * Configuration class for the application
 */
@Configuration
public class AiDemoConfiguration {
    
    @Value("${llm.use-embeddings}")
	String useEmbeddings;

    /**
     * Creates a RestClient (wrapped by a ChatClient) with specific client
     * interceptors attached.
     * @param configurer The Rest Client Configurer to use
     * @return A RestClient Builder with the specific interceptors.
     */
	@Bean
	@Scope("prototype")
	RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
		RestClient.Builder builder = 
			RestClient
                .builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.simple().build())
                .requestInterceptor(new ClientAuthorizationInterceptor())
			    .requestInterceptor(new ChatClientLoggingInterceptor());
		
		return configurer.configure(builder);

	}

    /**
     * Creates a ChatClient with the appropriate Advisors.
     * @param chatClientBuilder The ChatClient Builder to use
     * @param vectorStore The VectorStore to use for embeddings
     * @return A ChatClient with the appropriate Advisors.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        if (Boolean.valueOf(useEmbeddings)) {
            return chatClientBuilder.defaultAdvisors(new ErrorHandlingAdvisor(), new QuestionAnswerAdvisor(vectorStore)).build();
        } else {
            return chatClientBuilder.defaultAdvisors(new ErrorHandlingAdvisor()).build();
        }
    }

    /**
     * Creates a FilterRegistrationBean for an AuthorizationFilter.
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
