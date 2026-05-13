package dev.once.filter;

import dev.once.annotation.Idempotent;
import dev.once.exceptions.RequestInFlightException;
import dev.once.storage.IdempotencyStore;
import dev.once.storage.IdempotentResponse;
import dev.once.storage.InMemoryIdempotencyStore;
import dev.once.storage.Status;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyFilterTest {

    private IdempotencyStore store;
    private IdempotencyFilter filter;

    private final SampleController controller = new SampleController();

    @BeforeEach
    void setUp() {
        store = new InMemoryIdempotencyStore(3600);
        filter = new IdempotencyFilter(store);
    }

    @Test
    void preHandle_continues_whenHandlerIsNotHandlerMethod() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = filter.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        assertThat(request.getAttribute("idempotency_key")).isNull();
    }

    @Test
    void preHandle_continues_whenMethodNotAnnotated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = handlerMethod("plain");

        boolean proceed = filter.preHandle(request, response, handler);

        assertThat(proceed).isTrue();
    }

    @Test
    void preHandle_continues_whenKeyAbsentAndNotForced() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pay");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = handlerMethod("optionalKey");

        boolean proceed = filter.preHandle(request, response, handler);

        assertThat(proceed).isTrue();
    }

    @Test
    void preHandle_returns400_whenForcedAndKeyMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pay");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = handlerMethod("forcedKey");

        boolean proceed = filter.preHandle(request, response, handler);

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).contains("missing_idempotency_key");
    }

    @Test
    void preHandle_replaysCachedResponse_andStopsChain() throws Exception {
        String genKey = "POST:/pay:abc";
        store.set(genKey, new IdempotentResponse(
                201,
                "created".getBytes(StandardCharsets.UTF_8),
                "text/plain",
                Instant.now(),
                Map.of("X-Trace", "1"),
                Status.COMPLETED
        ));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pay");
        request.addHeader("Idempotency-Key", "abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = handlerMethod("optionalKey");

        boolean proceed = filter.preHandle(request, response, handler);

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("created");
        assertThat(response.getHeader("X-Trace")).isEqualTo("1");
    }

    @Test
    void preHandle_throws_whenSameKeyInFlight() {
        String genKey = "POST:/pay:abc";
        store.setInFlight(genKey);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pay");
        request.addHeader("Idempotency-Key", "abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = handlerMethod("optionalKey");

        assertThatThrownBy(() -> filter.preHandle(request, response, handler))
                .isInstanceOf(RequestInFlightException.class)
                .hasMessageContaining("in flight");
    }

    @Test
    void preHandle_setsAttributeAndInFlight_onNewKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pay");
        request.addHeader("Idempotency-Key", "new-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = handlerMethod("optionalKey");

        boolean proceed = filter.preHandle(request, response, handler);

        assertThat(proceed).isTrue();
        assertThat(request.getAttribute("idempotency_key")).isEqualTo("POST:/pay:new-key");
        IdempotentResponse inflight = store.get("POST:/pay:new-key");
        assertThat(inflight).isNotNull();
        assertThat(inflight.isInFlight()).isTrue();
    }

    @Test
    void afterCompletion_deletesKey_onException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pay");
        request.setAttribute("idempotency_key", "POST:/pay:k");
        store.setInFlight("POST:/pay:k");

        filter.afterCompletion(request, new MockHttpServletResponse(), handlerMethod("optionalKey"), new RuntimeException("boom"));

        assertThat(store.get("POST:/pay:k")).isNull();
    }

    @Test
    void afterCompletion_leavesStore_whenNoException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pay");
        request.setAttribute("idempotency_key", "POST:/pay:k");
        store.setInFlight("POST:/pay:k");

        filter.afterCompletion(request, new MockHttpServletResponse(), handlerMethod("optionalKey"), null);

        assertThat(store.get("POST:/pay:k")).isNotNull();
    }

    private HandlerMethod handlerMethod(String methodName) {
        try {
            Method method = SampleController.class.getMethod(methodName);
            return new HandlerMethod(controller, method);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    static class SampleController {

        @Idempotent
        public void optionalKey() {
        }

        @Idempotent(force = true)
        public void forcedKey() {
        }

        public void plain() {
        }
    }
}
