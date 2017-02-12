package com.echsylon.blocks.callback;

/**
 * This interface represents a request abstraction. A response will be delivered
 * eventually in the future to any attached response listeners. The caller can
 * attach three types of listeners:
 * <p>
 * A {@link SuccessListener} will receive any successfully produced data. The
 * caller is responsible to provide a correctly typed listener.
 * <p>
 * A {@link ErrorListener} will receive any errors the request mechanism can't
 * recover from. The error is delivered (not thrown) as {@code Throwable}
 * callback argument.
 * <p>
 * A {@link FinishListener} will act as an event channel that will simply signal
 * that the request has finished. No information is provided through this
 * callback, not even the state of the response. This channel can be used to
 * perform any state reset operations, like hiding progress bars, etc, which
 * aren't dependant on the success or failure state of a request.
 *
 * @param <T> The type of result object that is expected from the request. Any
 *            attached {@code SuccessListeners} must have the same type of
 *            generic definition.
 */
@SuppressWarnings("WeakerAccess")
public interface Request<T> {

    /**
     * Attaches a success listener to be called once a result is successfully
     * produced.
     *
     * @param listener The success listener.
     * @return The request object the listener was attached to. This allows for
     * chaining calls.
     */
    Request<T> withSuccessListener(SuccessListener<T> listener);

    /**
     * Attaches an error listener to be called if the request terminally fails
     * to produce a result.
     *
     * @param listener The error listener.
     * @return The request object the listener was attached to. This allows for
     * chaining calls.
     */
    Request<T> withErrorListener(ErrorListener listener);

    /**
     * Attaches a finish signal listener to be called regardless of the finish
     * state of the request.
     *
     * @param listener The finish listener.
     * @return The request object the listener was attached to. This allows for
     * chaining calls.
     */
    Request<T> withFinishListener(FinishListener listener);

}
