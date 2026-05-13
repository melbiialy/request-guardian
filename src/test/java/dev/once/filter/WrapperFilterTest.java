package dev.once.filter;

import dev.once.storage.IdempotencyStore;
import dev.once.storage.IdempotentResponse;
import dev.once.storage.InMemoryIdempotencyStore;
import dev.once.storage.Status;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WrapperFilterTest {

    @Test
    void cachesResponse_whenIdempotencyKeyPresent() throws ServletException, IOException {
        IdempotencyStore store = new InMemoryIdempotencyStore(3600);
        WrapperFilter filter = new WrapperFilter(store);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        request.setAttribute("idempotency_key", "POST:/api:idem-1");

        filter.doFilterInternal(request, response, (req, res) -> {
            HttpServletResponse http = (HttpServletResponse) res;
            http.setStatus(201);
            http.setContentType("application/json");
            res.getOutputStream().write("{\"id\":1}".getBytes(StandardCharsets.UTF_8));
        });

        IdempotentResponse cached = store.get("POST:/api:idem-1");
        assertThat(cached).isNotNull();
        assertThat(cached.statusCode()).isEqualTo(201);
        assertThat(cached.contentType()).contains("application/json");
        assertThat(new String(cached.body(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":1}");
        assertThat(cached.status()).isEqualTo(Status.COMPLETED);
    }

    @Test
    void doesNotWriteToStore_whenKeyAbsent() throws ServletException, IOException {
        IdempotencyStore store = new InMemoryIdempotencyStore(3600);
        WrapperFilter filter = new WrapperFilter(store);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {
            ((HttpServletResponse) res).setStatus(200);
            res.getOutputStream().write("ok".getBytes(StandardCharsets.UTF_8));
        });

        assertThat(store.get("POST:/api:idem-1")).isNull();
    }
}
