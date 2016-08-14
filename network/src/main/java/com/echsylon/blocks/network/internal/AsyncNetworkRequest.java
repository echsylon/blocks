package com.echsylon.blocks.network.internal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.echsylon.blocks.network.ErrorListener;
import com.echsylon.blocks.network.FinishListener;
import com.echsylon.blocks.network.NetworkClient;
import com.echsylon.blocks.network.Request;
import com.echsylon.blocks.network.SuccessListener;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/**
 * This is a default network {@link Request} implementation. This class will hold a hard reference
 * to any attached listeners until a result is produced, at which point all listener references are
 * released.
 *
 * @param <T> The type of expected result object. The caller can use {@link Void} when expecting
 *            intentionally empty results.
 */
public final class AsyncNetworkRequest<T> extends FutureTask<T> implements Request<T> {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static AsyncNetworkRequest lastRequest;

    /**
     * This class will prepare a suitable request object for the given parameters. If the target URL
     * is the same as the last still pending request url, then that request object will be returned
     * in order to prevent unnecessary duplicate requests to the server.
     *
     * @param networkClient      The transport to send the request through.
     * @param url                The target URL.
     * @param method             The request method.
     * @param headers            Any optional key/value headers.
     * @param jsonContent        Any optional json to send along with the request.
     * @param expectedResultType The Java class implementation of the expected result.
     * @param <V>                The result type declaration.
     * @return A request object to attach any callback implementations to.
     */
    public static <V> Request<V> prepareRequest(@NonNull NetworkClient networkClient,
                                                @NonNull String url,
                                                @NonNull String method,
                                                @Nullable Map<String, String> headers,
                                                @Nullable String jsonContent,
                                                @NonNull Class<V> expectedResultType) {

        return lastRequest == null || !url.equals(lastRequest.id) ?
                new AsyncNetworkRequest<>(networkClient, url, method, headers, jsonContent, expectedResultType) :
                lastRequest;
    }

    private final CallbackManager<T> callbackManager;
    private final String id;

    /**
     * Intentionally hidden constructor.
     *
     * @param networkClient      The transport to send the request through.
     * @param url                The target URL.
     * @param method             The request method.
     * @param headers            Any optional key/value headers.
     * @param jsonContent        Any optional json to send along with the request.
     * @param expectedResultType The Java class implementation of the expected result.
     */
    private AsyncNetworkRequest(@NonNull NetworkClient networkClient,
                                @NonNull String url,
                                @NonNull String method,
                                @Nullable Map<String, String> headers,
                                @Nullable String jsonContent,
                                @NonNull Class<T> expectedResultType) {

        super(() -> networkClient.request(url, method, headers, jsonContent, expectedResultType));
        id = url;
        callbackManager = new CallbackManager<>();
        EXECUTOR.submit(this);
        lastRequest = this;
    }

    /**
     * Used internally by the java concurrency framework. Don't call this method on your own.
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
     * Attaches a success listener to this request. Note that the listener will only be called once
     * and if the request produces an error, the listener won't be called at all.
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
     * Attaches an error listener to this request. Note that the listener will only be called once
     * and if the request doesn't produce an error, the listener won't be called at all.
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
     * Attaches a finished state listener to this request. Note that the listener will be called
     * exactly once regardless if the produced result is a success or a failure.
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
