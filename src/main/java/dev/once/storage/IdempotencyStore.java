package dev.once.storage;

public interface IdempotencyStore {
    void set(String key,IdempotentResponse idempotentResponse);
    IdempotentResponse get(String key);
    void delete(String key);
    void setInFlight(String key);
}
