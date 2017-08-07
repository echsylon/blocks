package com.echsylon.blocks.network;

import com.echsylon.blocks.callback.DefaultRequest;

import java.util.List;

/**
 * This class offers means of convenient request construction and enqueueing.
 */
@SuppressWarnings("WeakerAccess")
public class DefaultRequestFactory {

    /**
     * Enqueues a new byte array request. It's up to the caller to apply
     * appropriate post processing on the result.
     *
     * @param networkClient The transport to send the request through.
     * @param url           The target URL.
     * @param method        The request method.
     * @param headers       Any optional key/value headers.
     * @param payload       Any optional payload to send along with the
     *                      request.
     * @return A request object to attach any callback implementations to.
     */
    public static DefaultRequest<byte[]> enqueueDefaultRequest(final NetworkClient networkClient,
                                                               final String url,
                                                               final String method,
                                                               final List<NetworkClient.Header> headers,
                                                               final byte[] payload) {

        // Don't even try if the required tools aren't provided.
        if (networkClient == null)
            throw new IllegalArgumentException("The NetworkClient mustn't be null");

        return new DefaultRequest<>(() -> {
            String contentType = null;
            int last = headers != null ? headers.size() - 1 : -1;
            for (int i = last; i >= 0 && contentType == null; i--)
                if ("Content-Type".equals(headers.get(i).key))
                    contentType = headers.get(i).value;

            return networkClient.execute(url, method, headers, payload, contentType);
        });
    }

    /**
     * Enqueues a new JSON request, attempting to transform the given payload to
     * a JSON string.
     *
     * @param networkClient      The transport to send the request through.
     * @param url                The target URL.
     * @param method             The request method.
     * @param headers            Any optional key/value headers.
     * @param payload            Any optional payload to send along with the
     *                           request. Will be serialized as JSON.
     * @param jsonParser         The JSON parser to use when transforming to and
     *                           from JSON notation.
     * @param expectedResultType The class description of the expected result.
     * @param <T>                The type declaration of the response class.
     * @return A request object to attach any callback implementations to.
     */
    public static <T> DefaultRequest<T> enqueueJsonRequest(final NetworkClient networkClient,
                                                           final String url,
                                                           final String method,
                                                           final List<NetworkClient.Header> headers,
                                                           final Object payload,
                                                           final JsonParser jsonParser,
                                                           final Class<T> expectedResultType) {

        // Don't even try if the required tools aren't provided.
        if (networkClient == null)
            throw new IllegalArgumentException("The NetworkClient mustn't be null");

        if (jsonParser == null)
            throw new IllegalArgumentException("The JsonParser mustn't be null");

        return new DefaultRequest<>(() -> {
            byte[] payloadBytes = payload != null ?
                    jsonParser.toJson(payload).getBytes() :
                    null;

            byte[] responseBytes = networkClient.execute(url, method, headers, payloadBytes, "application/json");
            String responseJson = new String(responseBytes);
            return jsonParser.fromJson(responseJson, expectedResultType);
        });
    }

}
