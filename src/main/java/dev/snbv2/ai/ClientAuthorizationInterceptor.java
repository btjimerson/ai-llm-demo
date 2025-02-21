package dev.snbv2.ai;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * ClientHttpRequestInterceptor that adds an Authorization header to
 * the request. This is used to propagate Authorization headers in the request
 * to
 * this application.
 * 
 * @author Brian Jimerson
 */
public class ClientAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    /**
     * Adds an Authorization header to the client request if needed. General flow
     * is:
     * 1. Get RequestAttributes that has the inbound Authorization header.
     * 2. Remove any existing Authorization headers from the client request.
     * 3. Add the inbound Authorization header to the client request.
     * 4. Continue executing the request.
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            String authorizationToken = (String) attributes.getAttribute(HttpHeaders.AUTHORIZATION, 0);
            if (authorizationToken != null) {
                request.getHeaders().remove(HttpHeaders.AUTHORIZATION);
                request.getHeaders().add(HttpHeaders.AUTHORIZATION, authorizationToken);
            }
        }

        ClientHttpResponse response = execution.execute(request, body);
        return response;

    }

}
