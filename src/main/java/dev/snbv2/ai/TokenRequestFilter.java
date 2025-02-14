package dev.snbv2.ai;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

public class TokenRequestFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

            if (request instanceof HttpServletRequest httpServletRequest) {

                ServletRequestAttributes requestAttributes = 
                    ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes());
                    
                if (requestAttributes != null) {
                    requestAttributes.setAttribute(
                        HttpHeaders.AUTHORIZATION, httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION), 0);
                }
                    
            }

            chain.doFilter(request, response);
    }
    
}
