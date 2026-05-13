package dev.once.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisIdempotencyStoreTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> template = mock(RedisTemplate.class);
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private RedisIdempotencyStore store;

    @BeforeEach
    void setUp() {
        when(template.opsForValue()).thenReturn(valueOps);
        store = new RedisIdempotencyStore(template, objectMapper, 60);
    }

    @Test
    void set_serializesAndSetsWithTtl() throws Exception {
        IdempotentResponse response = new IdempotentResponse(
                200,
                "x".getBytes(),
                "text/plain",
                Instant.now(),
                Map.of(),
                Status.COMPLETED
        );

        store.set("key-1", response);

        verify(valueOps).set(eq("key-1"), any(String.class), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void get_returnsNull_whenRedisMissing() {
        when(valueOps.get("k")).thenReturn(null);

        assertThat(store.get("k")).isNull();
    }

    @Test
    void get_deserializesAndReturns_whenPresent() {
        IdempotentResponse original = new IdempotentResponse(
                422,
                "body".getBytes(),
                "application/json",
                Instant.now(),
                Map.of("H", "v"),
                Status.COMPLETED
        );
        try {
            String json = objectMapper.writeValueAsString(original);
            when(valueOps.get("k")).thenReturn(json);

            IdempotentResponse got = store.get("k");

            assertThat(got).isNotNull();
            assertThat(got.statusCode()).isEqualTo(422);
            assertThat(got.body()).isEqualTo("body".getBytes());
            assertThat(got.headers()).containsEntry("H", "v");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void setInFlight_setsShortTtl() throws Exception {
        store.setInFlight("inflight");

        verify(valueOps).set(eq("inflight"), any(String.class), eq(30L), eq(TimeUnit.SECONDS));
    }
}
