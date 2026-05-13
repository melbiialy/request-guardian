package dev.once.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryIdempotencyStoreTest {

    private InMemoryIdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryIdempotencyStore(3600);
    }

    @Test
    void get_returnsNull_whenKeyMissing() {
        assertThat(store.get("unknown")).isNull();
    }

    @Test
    void setAndGet_roundTripsCompletedResponse() {
        IdempotentResponse saved = new IdempotentResponse(
                200,
                "{\"ok\":true}".getBytes(),
                "application/json",
                Instant.now(),
                Map.of("X-Custom", "v"),
                Status.COMPLETED
        );
        store.set("k1", saved);

        IdempotentResponse got = store.get("k1");
        assertThat(got).isNotNull();
        assertThat(got.statusCode()).isEqualTo(200);
        assertThat(got.body()).isEqualTo("{\"ok\":true}".getBytes());
        assertThat(got.contentType()).isEqualTo("application/json");
        assertThat(got.headers()).containsEntry("X-Custom", "v");
        assertThat(got.isInFlight()).isFalse();
    }

    @Test
    void get_expiresCompletedEntry_afterTtl() {
        store = new InMemoryIdempotencyStore(1);
        IdempotentResponse old = new IdempotentResponse(
                201,
                new byte[0],
                "text/plain",
                Instant.now().minusSeconds(5),
                Map.of(),
                Status.COMPLETED
        );
        store.set("old", old);

        assertThat(store.get("old")).isNull();
    }

    @Test
    void get_doesNotExpireInFlight_evenWhenTimestampIsOld() {
        store = new InMemoryIdempotencyStore(1);
        store.setInFlight("flight");
        // Artificially replace with stale in-flight (get normally returns in-flight as stored)
        store.delete("flight");
        store.set("flight", new IdempotentResponse(
                0,
                null,
                null,
                Instant.now().minusSeconds(100),
                Map.of(),
                Status.IN_FLIGHT
        ));

        IdempotentResponse got = store.get("flight");
        assertThat(got).isNotNull();
        assertThat(got.isInFlight()).isTrue();
    }

    @Test
    void setInFlight_marksInFlight() {
        store.setInFlight("k");
        IdempotentResponse got = store.get("k");
        assertThat(got).isNotNull();
        assertThat(got.isInFlight()).isTrue();
    }

    @Test
    void delete_removesKey() {
        store.set("k", IdempotentResponse.inFlight());
        store.delete("k");
        assertThat(store.get("k")).isNull();
    }
}
