package dev.snbv2.ai;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class TokenInjectorRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                
                if (attributes != null) {
                    request.getHeaders().remove(HttpHeaders.AUTHORIZATION);
                    String authorizationToken = (String) attributes.getAttribute(HttpHeaders.AUTHORIZATION, 0);
                    request.getHeaders().add(HttpHeaders.AUTHORIZATION, authorizationToken);
                }
                
                ClientHttpResponse response = execution.execute(request, body);
                return response;

    }
    
}
