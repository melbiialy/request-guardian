package dev.once.storage;



import java.time.Instant;
import java.util.Map;

public record IdempotentResponse(int statusCode, byte[] body, String contentType, Instant timestamp, Map<String ,String > headers, Status status) {
    public static IdempotentResponse inFlight() {
        return new IdempotentResponse(0, null, null, Instant.now(), Map.of(), Status.IN_FLIGHT);
    }

    public boolean isInFlight() {
        return status == Status.IN_FLIGHT;
    }
}
