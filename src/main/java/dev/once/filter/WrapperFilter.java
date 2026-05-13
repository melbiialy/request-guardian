package dev.once.filter;

import dev.once.storage.IdempotencyStore;
import dev.once.storage.IdempotentResponse;
import dev.once.storage.Status;
import dev.once.wrapper.ContentCachingResponseWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class WrapperFilter extends OncePerRequestFilter {
    private final IdempotencyStore idempotencyStore;

    public WrapperFilter(IdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, responseWrapper);
        }finally {
            String key = (String) request.getAttribute("idempotency_key");
            if (key!=null){
                byte[] body = responseWrapper.getContent();
                Map<String,String> headers = new HashMap<>();
                responseWrapper.getHeaderNames().forEach(name -> headers.put(name, responseWrapper.getHeader(name)));
                idempotencyStore.set(key,new IdempotentResponse(response.getStatus(),body,
                        response.getContentType(), Instant.now(),headers, Status.COMPLETED));
            }

        }
    }
}
