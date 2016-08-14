package com.echsylon.blocks.network.exception;

/**
 * This class is responsible for delivering a request status related runtime exception.
 */
public class RequestException extends RuntimeException {
    public final int statusCode;

    public RequestException(int statusCode) {
        super();
        this.statusCode = statusCode;
    }

    public RequestException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public RequestException(int statusCode, Throwable casue) {
        super(casue);
        this.statusCode = statusCode;
    }

    public RequestException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * Tells whether this exception is due to a client side error. This typically maps to an HTTP
     * status code in the range [400..499].
     *
     * @return Boolean true if this exception is caused by a client side error.
     */
    public boolean isClientSideError() {
        return statusCode >= 400 && statusCode <= 499;
    }

    /**
     * Tells whether this exception is due to a server side error. This typically maps to an HTTP
     * status code in the range [500..599].
     *
     * @return Boolean true if this exception is caused by a server side error.
     */
    public boolean isServerSideError() {
        return statusCode >= 500 && statusCode <= 599;
    }
}
