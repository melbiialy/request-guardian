package dev.once.filter;

import dev.once.annotation.Idempotent;
import dev.once.exceptions.RequestInFlightException;
import dev.once.storage.IdempotencyStore;
import dev.once.storage.IdempotentResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

public class IdempotencyFilter implements HandlerInterceptor {
    private final IdempotencyStore idempotencyStore;

    public IdempotencyFilter(IdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod)){
            return true;
        }
        Idempotent annotation = ((HandlerMethod) handler).getMethodAnnotation(Idempotent.class);
        if (annotation == null){
            return true;
        }

        String header = annotation.header();
        String key = request.getHeader(header);
        if (key == null && !annotation.force()){
            return true;
        } else if (key == null){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"missing_idempotency_key\"}");
            return false;
        }
        String generatedKey =
                request.getMethod() + ":" +
                        request.getRequestURI() + ":" +
                        key;
        IdempotentResponse cached = idempotencyStore.get(generatedKey);

        if (cached != null) {
            if (cached.isInFlight()) {
                throw new RequestInFlightException("Request is in flight");
            }
            response.setStatus(cached.statusCode());
            response.setContentType(cached.contentType());
            cached.headers().forEach(response::setHeader);
            response.getOutputStream().write(cached.body());
            return false;
        }

        idempotencyStore.setInFlight(generatedKey);
        request.setAttribute("idempotency_key", generatedKey);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) throws Exception {

        String key = (String) request.getAttribute("idempotency_key");
        if (key == null) return;

        if (ex != null) {
            idempotencyStore.delete(key);
            return;
        }

    }
}
