package com.echsylon.blocks.callback;

/**
 * This interface describes the minimum set of features expected from a callback
 * manager. A callback managers role should be keep track of all callbacks for a
 * given request and call the appropriate ones depending on the nature of the
 * request result.
 */
@SuppressWarnings("WeakerAccess")
public interface CallbackManager<T> {

    /**
     * Enables means of temporarily caching success listeners.
     *
     * @param listener The success callback implementation.
     */
    void addSuccessListener(SuccessListener<T> listener);

    /**
     * Enables means of temporarily caching error listeners.
     *
     * @param listener The error callback implementation.
     */

    void addErrorListener(ErrorListener listener);

    /**
     * Enables means of temporarily caching finish listeners.
     *
     * @param listener The finish callback implementation.
     */
    void addFinishListener(FinishListener listener);

    /**
     * Delivers the given success result object to all cached success
     * listeners.
     *
     * @param result The success result object.
     */
    void deliverSuccessOnMainThread(T result);

    /**
     * Delivers the given error cause to all cached error listeners.
     *
     * @param cause The error.
     */
    void deliverErrorOnMainThread(Throwable cause);

}
