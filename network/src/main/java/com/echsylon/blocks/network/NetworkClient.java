package com.echsylon.blocks.network;

import java.util.Map;

/**
 * This interface describes the minimum required capabilities of any network clients in order to be
 * usable by the Blocks infrastructure.
 */
public interface NetworkClient {

    /**
     * Performs a synchronous network request.
     *
     * @param url                The target URL of the request.
     * @param method             The HTTP method.
     * @param headers            Any optional key/value header pairs. May be null.
     * @param body               Any optional data to send. May be null.
     * @param expectedResultType The Java class implementation of the expected result DTO.
     * @param <T>                The type of expected result.
     * @return A Java class object, of the defined type, which describes the response body DTO.
     */
    <T> T request(String url, String method, Map<String, String> headers, Object body, Class<T> expectedResultType);

}
