package com.echsylon.blocks.network;

/**
 * This interface describes the asynchronous success listener callback.
 *
 * @param <T> The type of the expected result.
 */
public interface SuccessListener<T> {

    /**
     * Delivers the result to the implementing infrastructure.
     *
     * @param result The requested object.
     */
    void onSuccess(T result);
}
