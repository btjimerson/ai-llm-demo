package dev.snbv2.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

@Configuration
public class AiDemoConfiguration {
    
    @Value("${llm.use-embeddings}")
	String useEmbeddings;

	@Bean
	@Scope("prototype")
	RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
		RestClient.Builder builder = 
			RestClient
                .builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.simple().build())
                .requestInterceptor(new TokenInjectorRequestInterceptor())
			    .requestInterceptor(new ChatClientInterceptor());
		
		return configurer.configure(builder);

	}

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        if (Boolean.valueOf(useEmbeddings)) {
            return chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor() ,new QuestionAnswerAdvisor(vectorStore)).build();
        } else {
           return chatClientBuilder.build();
        }
    }

    @Bean
    public FilterRegistrationBean<TokenRequestFilter> filterRegistrationBean() {

        FilterRegistrationBean<TokenRequestFilter> registration = new FilterRegistrationBean<TokenRequestFilter>();
        registration.setFilter(new TokenRequestFilter());
        registration.addUrlPatterns("/*");
        registration.setName("tokenRequestFilter");
        return registration;

    }

}
