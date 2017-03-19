package com.echsylon.blocks.network;

import java.util.List;

/**
 * This interface describes the minimum required capabilities of any network
 * clients in order to be usable by the Blocks infrastructure.
 */
public interface NetworkClient {

    /**
     * This data structure describes a header key/value entry.
     */
    @SuppressWarnings("WeakerAccess")
    final class Header {
        public final String key;
        public final String value;

        public Header(final String key, final String value) {
            this.key = key;
            this.value = value;
        }
    }

    /**
     * Performs a synchronous network request.
     *
     * @param url         The target URL of the request.
     * @param method      The HTTP method.
     * @param headers     Any optional key/value header pairs. May be null.
     * @param payload     Any optional data to send. May be null.
     * @param contentType The type of payload being sent. May be null.
     * @return The server response as a byte array.
     */
    byte[] execute(final String url,
                   final String method,
                   final List<Header> headers,
                   final byte[] payload,
                   final String contentType);

}
