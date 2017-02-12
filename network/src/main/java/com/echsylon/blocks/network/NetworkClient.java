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
     * @param url                The target URL of the request.
     * @param method             The HTTP method.
     * @param headers            Any optional key/value header pairs. May be
     *                           null.
     * @param payload            Any optional data to send. May be null.
     * @param expectedResultType The Java class implementation of the expected
     *                           result DTO.
     * @param <T>                The type of expected result.
     * @return A Java class object, of the defined type, which describes the
     * response body DTO.
     */
    <T> T execute(String url, String method, List<Header> headers, byte[] payload, Class<T> expectedResultType);

}
