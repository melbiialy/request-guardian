package dev.once.storage;

import com.fasterxml.jackson.core.JsonProcessingException;

public interface IdempotencyStore {
    void set(String key,IdempotentResponse idempotentResponse) throws JsonProcessingException;
    IdempotentResponse get(String key);
    void delete(String key);
    void setInFlight(String key);
}
