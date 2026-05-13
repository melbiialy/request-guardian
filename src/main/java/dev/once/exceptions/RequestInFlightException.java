package dev.once.exceptions;

public class RequestInFlightException extends RuntimeException {
    public RequestInFlightException(String message) {
        super(message);
    }
}
