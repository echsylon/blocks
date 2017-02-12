package com.echsylon.blocks.network;

import com.echsylon.blocks.callback.CallbackManager;
import com.echsylon.blocks.callback.DefaultCallbackManager;
import com.echsylon.blocks.callback.ErrorListener;
import com.echsylon.blocks.callback.FinishListener;
import com.echsylon.blocks.callback.Request;
import com.echsylon.blocks.callback.SuccessListener;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/**
 * This is a default network {@link Request} implementation. This class will
 * hold a hard reference to any attached listeners until a result is produced,
 * at which point all listener references are released.
 *
 * @param <T> The type of expected result object. The caller can use {@link
 *            Void} when expecting intentionally empty results.
 */
@SuppressWarnings("WeakerAccess")
public final class JsonNetworkRequest<T> extends FutureTask<T> implements Request<T> {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(5);
    private static JsonNetworkRequest lastRequest;

    /**
     * This class will prepare a suitable request object for the given
     * parameters. If the target URL is the same as the last still pending
     * request url, then that request object will be returned in order to
     * prevent unnecessary duplicate requests to the server.
     *
     * @param networkClient      The transport to send the request through.
     * @param url                The target URL.
     * @param method             The request method.
     * @param headers            Any optional key/value headers.
     * @param jsonPayload        Any optional json to send along with the
     *                           request.
     * @param expectedResultType The Java class implementation of the expected
     *                           result.
     * @param <V>                The result type declaration.
     * @return A request object to attach any callback implementations to.
     */
    public static <V> JsonNetworkRequest<V> prepare(final NetworkClient networkClient,
                                                    final String url,
                                                    final String method,
                                                    final List<NetworkClient.Header> headers,
                                                    final String jsonPayload,
                                                    final Class<V> expectedResultType) {

        if (lastRequest != null && lastRequest.tag != null && lastRequest.tag.equals(url)) {
            byte[] payload = jsonPayload != null ? jsonPayload.getBytes() : null;
            Callable<V> callable = () -> networkClient.execute(url, method, headers, payload, expectedResultType);
            lastRequest = new JsonNetworkRequest<>(url, callable);
            EXECUTOR.submit(lastRequest);
        }

        //noinspection unchecked
        return lastRequest;
    }

    private final CallbackManager<T> callbackManager;
    private final String tag;

    /**
     * Intentionally hidden constructor.
     *
     * @param tag      A tag for this request, may be null.
     * @param callable The actual job content of this network request.
     */
    private JsonNetworkRequest(final String tag, final Callable<T> callable) {

        super(callable);
        this.tag = tag;
        this.callbackManager = new DefaultCallbackManager<>();
    }

    /**
     * Used internally by the java concurrency framework. Don't call this method
     * on your own.
     */
    @Override
    protected void done() {
        super.done();

        if (this == lastRequest)
            lastRequest = null;

        try {
            callbackManager.deliverSuccessOnMainThread(get());
        } catch (InterruptedException | ExecutionException e) {
            callbackManager.deliverErrorOnMainThread(e.getCause());
        }
    }

    /**
     * Attaches a success listener to this request. Note that the listener will
     * only be called once and if the request produces an error, the listener
     * won't be called at all.
     *
     * @param listener The success listener.
     * @return This request object, allowing chaining of requests.
     */
    @Override
    public Request<T> withSuccessListener(SuccessListener<T> listener) {
        callbackManager.addSuccessListener(listener);
        return this;
    }

    /**
     * Attaches an error listener to this request. Note that the listener will
     * only be called once and if the request doesn't produce an error, the
     * listener won't be called at all.
     *
     * @param listener The error listener.
     * @return This request object, allowing chaining of requests.
     */
    @Override
    public Request<T> withErrorListener(ErrorListener listener) {
        callbackManager.addErrorListener(listener);
        return this;
    }

    /**
     * Attaches a finished state listener to this request. Note that the
     * listener will be called exactly once regardless if the produced result is
     * a success or a failure.
     *
     * @param listener The finish state listener.
     * @return This request object, allowing chaining of requests.
     */
    @Override
    public Request<T> withFinishListener(FinishListener listener) {
        callbackManager.addFinishListener(listener);
        return this;
    }

}
