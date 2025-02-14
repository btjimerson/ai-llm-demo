package dev.snbv2.ai;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import io.micrometer.core.instrument.util.IOUtils;

public class ChatClientInterceptor implements ClientHttpRequestInterceptor {

    private static final Log LOG = LogFactory.getLog(ChatClientInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        
        LOG.debug(String.format("Request: %s %s", request.getMethod(), request.getURI()));
        LOG.debug(String.format("Request Headers: %s", request.getHeaders()));
        LOG.debug(String.format("Request Body: %s", truncateBody(new String(body), 1000)));

        ClientHttpResponse response = execution.execute(request, body);

        BufferingClientHttpResponseWrapper wrapper = new BufferingClientHttpResponseWrapper(response);

        LOG.debug(String.format("Response: %s %s", wrapper.getStatusCode(), wrapper.getStatusText()));
        LOG.debug(String.format("Response Headers: %s", wrapper.getHeaders()));
        LOG.debug(String.format("Response Body: %s", truncateBody(IOUtils.toString(wrapper.getBody(), Charset.forName("UTF-8")), 1000)));
    
        return wrapper;
    }

    private String truncateBody(String body, int maxLength) {
        if (body == null || body.length() <= maxLength) {
            return body;
        } else {
            StringBuffer sb = new StringBuffer();
            sb.append(body.substring(0, maxLength));
            sb.append("\n...truncated to ");
            sb.append(maxLength);
            sb.append(" characters...");
            return sb.toString();
        }
    }

    protected class BufferingClientHttpResponseWrapper implements ClientHttpResponse {

        ClientHttpResponse response;
        byte[] body;

        BufferingClientHttpResponseWrapper(ClientHttpResponse response) {
            this.response = response;
        }

        @Override
        public InputStream getBody() throws IOException {
            if (this.body == null) {
                this.body = StreamUtils.copyToByteArray(this.response.getBody());
            }
            return new ByteArrayInputStream(this.body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.response.getHeaders();
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return this.response.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return this.response.getStatusText();
        }

        @Override
        public void close() {
            this.response.close();
        }
        
    }
    
}
