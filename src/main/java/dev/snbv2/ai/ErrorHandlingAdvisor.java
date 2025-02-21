package dev.snbv2.ai;

import java.util.HashMap;
import java.util.List;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import reactor.core.publisher.Flux;

/**
 * ChatClient Advisor to handle upstream LLM errors gracefully.
 * 
 * @author Brian Jimerson
 */
public class ErrorHandlingAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * Attempt to make the next around stream. If an exception occurs, most likely a
     * 4xx or 5xx HTTP response, use the error message as the chat response instead.
     */
    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        // Need to implement aroundCall in streaming api
        throw new UnsupportedOperationException("Unimplemented method 'aroundStream'");
    }

    /**
     * Attempt to make the next around call. If an exception occurs, most likely a
     * 4xx or 5xx HTTP response, use the error message as the chat response instead.
     */
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        try {
            return chain.nextAroundCall(advisedRequest);
        } catch (Exception e) {
            AssistantMessage assistantMessage = new AssistantMessage(
                    String.format("Error submitting chat request: %s", e.getMessage()));
            ChatResponse chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));
            return new AdvisedResponse(chatResponse, new HashMap<String, Object>());
        }
    }

}
