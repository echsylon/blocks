package com.echsylon.blocks.network;

import com.echsylon.blocks.callback.DefaultRequest;
import com.echsylon.blocks.callback.Request;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * This is a JSON {@link Request} implementation. It knows how to produce JSON
 * from any given given payload (and deliver it in JSON form) and it will also
 * make an attempt to convert any server response body to a corresponding Java
 * object of choice. If the server isn't responding with JSON this request will
 * fail miserably and call any attached error callbacks.
 * <p>
 * This implementation will hold a hard reference to any attached callbacks
 * until a result is produced, at which point all listener references are
 * released.
 *
 * @param <T> The type of expected result object. The caller can use {@link
 *            Void} when expecting intentionally empty results.
 */
@SuppressWarnings("WeakerAccess")
public final class JsonNetworkRequest<T> extends DefaultRequest<T> {
    private static final Object LAST_REQUEST_LOCK = new Object();
    private static final String JSON_CONTENT_TYPE = "application/json";

    /**
     * This class will prepare and enqueue a suitable request object for the
     * given parameters. If the target URL is the same as the last still pending
     * request url, then that request object will be returned in order to
     * prevent unnecessary duplicate requests to the server.
     *
     * @param networkClient      The transport to send the request through.
     * @param url                The target URL.
     * @param method             The request method.
     * @param headers            Any optional key/value headers.
     * @param payload            Any optional payload to send along with the
     *                           request. Will be serialized as JSON.
     * @param jsonParser         The JSON parser implementation to use when
     *                           producing the final result of the request.
     * @param expectedResultType The Java class implementation of the expected
     *                           result.
     * @param <V>                The result type declaration.
     * @return A request object to attach any callback implementations to.
     */
    public static <V> JsonNetworkRequest<V> enqueue(final NetworkClient networkClient,
                                                    final String url,
                                                    final String method,
                                                    final List<NetworkClient.Header> headers,
                                                    final Object payload,
                                                    final JsonParser jsonParser,
                                                    final Class<V> expectedResultType) {

        // Don't even try if the required tools aren't provided.
        if (networkClient == null)
            throw new IllegalArgumentException("The NetworkClient mustn't be null");

        if (jsonParser == null)
            throw new IllegalArgumentException("The JsonParser mustn't be null");

        // This code is expected to be run on the main thread. We need to
        // ensure that none of our worker threads interfere with the main
        // thread when operating on the shared "lastRequest" object.
        synchronized (LAST_REQUEST_LOCK) {
            if (lastRequest == null || (lastRequest.tag != null && !lastRequest.tag.equals(url))) {
                Callable<V> callable = () -> {
                    byte[] payloadBytes = payload != null ?
                            jsonParser.toJson(payload).getBytes() :
                            null;

                    byte[] responseBytes = networkClient.execute(url, method, headers, payloadBytes, JSON_CONTENT_TYPE);
                    String responseJson = new String(responseBytes);
                    return jsonParser.fromJson(responseJson, expectedResultType);
                };

                lastRequest = new JsonNetworkRequest<>(url, callable);
                enqueue(lastRequest);
            }
        }

        //noinspection unchecked
        return lastRequest;
    }

    private static JsonNetworkRequest lastRequest;
    private final String tag;

    /**
     * Intentionally hidden constructor.
     *
     * @param tag      A tag for this request, may be null.
     * @param callable The actual job content of this com.echsylon.blocks.network request.
     */
    private JsonNetworkRequest(final String tag, final Callable<T> callable) {
        super(callable);
        this.tag = tag;
    }

    /**
     * Used internally by the java concurrency framework. Don't call this method
     * on your own.
     */
    @Override
    protected void done() {
        super.done();

        // This method is executed on one of our worker threads. We need to
        // ensure that none of the worker threads interfere with each other
        // on this single shared "lastRequest" object.
        synchronized (LAST_REQUEST_LOCK) {
            if (this == lastRequest)
                lastRequest = null;
        }
    }

    /**
     * Removes all previously added listeners without calling through to any of
     * them.
     */
    public void terminate() {
        callbackManager.terminate();
    }

}
