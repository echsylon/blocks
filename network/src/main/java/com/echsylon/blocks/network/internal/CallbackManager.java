package com.echsylon.blocks.network.internal;

import android.os.Handler;
import android.os.Looper;

import com.annimon.stream.Stream;
import com.echsylon.blocks.network.ErrorListener;
import com.echsylon.blocks.network.FinishListener;
import com.echsylon.blocks.network.SuccessListener;

import java.util.ArrayList;

/**
 * This class is responsible for temporarily caching a reference, and delivering a result to any
 * given callback implementations. This class has no knowledge, what so ever, of how to produce a
 * result nor of the implementation specific details of the result or error objects.
 * <p>
 * This implementation will hold hard references to the attached callback implementations until the
 * result is delivered, at which point the references are released. No duplicate references are kept
 * to any callback.
 * <p>
 * Adding additional listeners <em>after</em> a result has been delivered will cause the listener to
 * be called immediately if applicable, without being cached. If, for example, adding a success
 * listener after an error result has been produced, then nothing can happen - the listener will
 * neither be cached nor called.
 *
 * @param <T> The type of result any added success listeners can handle.
 */
class CallbackManager<T> {
    private enum FinishState {
        SUCCESS, ERROR, NONE
    }

    private final Object successLock = new Object();
    private final Object errorLock = new Object();
    private final Object finishLock = new Object();

    private ArrayList<SuccessListener<T>> successListeners = new ArrayList<>();
    private ArrayList<ErrorListener> errorListeners = new ArrayList<>();
    private ArrayList<FinishListener> finishListeners = new ArrayList<>();

    private FinishState finishState = FinishState.NONE;
    private Object result = null;

    /**
     * Stores a reference to the given success callback internally until the result is delivered. If
     * a success result has already been delivered, then the given listener will also be called with
     * the previously produced result. If no result has been delivered yet and a reference to the
     * given callback is already being held on to, then nothing happens (no duplicate references are
     * kept).
     * <p>
     * Each success listener will only be called [0..1] times.
     *
     * @param listener The success callback implementation.
     */
    void addSuccessListener(SuccessListener<T> listener) {
        if (listener == null)
            return;

        switch (finishState) {
            case NONE:
                synchronized (successLock) {
                    if (!successListeners.contains(listener))
                        successListeners.add(listener);
                }
                break;
            case SUCCESS:
                deliverSuccessOnMainThread(listener);
                break;
            default:
                break;
        }
    }

    /**
     * Stores a reference to the given error callback internally until the result is delivered. If
     * an error result has already been delivered, then the given listener will also be called with
     * the previously produced error. If no result has been delivered yet and a reference to the
     * given callback is already being held on to, then nothing happens (no duplicate references are
     * kept).
     * <p>
     * Each error listener will only be called [0-1] times.
     *
     * @param listener The error callback implementation.
     */

    void addErrorListener(ErrorListener listener) {
        if (listener == null)
            return;

        switch (finishState) {
            case NONE:
                synchronized (errorLock) {
                    if (!errorListeners.contains(listener))
                        errorListeners.add(listener);
                }
                break;
            case ERROR:
                deliverErrorOnMainThread(listener);
                break;
            default:
                break;
        }
    }

    /**
     * Stores a reference to the given finish callback internally until the result is delivered. If
     * a result (success or error) has already been delivered, then the given listener will also be
     * called immediately. If no result has been delivered yet and a reference to the given callback
     * is already being held on to, then nothing happens (no duplicate references are kept).
     * <p>
     * Each finish listener will only be called exactly once.
     *
     * @param listener The finish callback implementation.
     */
    void addFinishListener(FinishListener listener) {
        if (listener == null)
            return;

        switch (finishState) {
            case NONE:
                synchronized (finishLock) {
                    if (!finishListeners.contains(listener))
                        finishListeners.add(listener);
                }
                break;
            case SUCCESS: // Intentional fallthrough
            case ERROR:
                notifyFinishOnMainThread(listener);
                break;
            default:
                // Ignore undefined result states
                break;
        }
    }

    /**
     * Delivers the given success result object to all attached success listeners and then signals
     * the finish state to all attached finish listeners. This method will release <em>all</em>
     * cached callback references once called.
     *
     * @param result The success result object.
     */
    void deliverSuccessOnMainThread(T result) {
        this.finishState = FinishState.SUCCESS;
        this.result = result;

        new Handler(Looper.getMainLooper()).post(() -> {
            synchronized (finishLock) {
                Stream.of(finishListeners)
                        .filter(listener -> listener != null)
                        .forEach(FinishListener::onFinish);
                finishListeners.clear();
            }

            synchronized (successLock) {
                Stream.of(successListeners)
                        .filter(listener -> listener != null)
                        .forEach(listener -> listener.onSuccess(result));
                successListeners.clear();
            }
        });

        // Since a result can't be both success and failure at the same time, it's safe to release
        // the error listeners too here.
        synchronized (errorLock) {
            errorListeners.clear();
        }
    }

    /**
     * Delivers the given error cause to all attached error listeners and then signals the finish
     * state to all attached finish listeners. This method will release <em>all</em> cached callback
     * references once called.
     *
     * @param cause The error.
     */
    void deliverErrorOnMainThread(Throwable cause) {
        this.finishState = FinishState.SUCCESS;
        this.result = cause;

        new Handler(Looper.getMainLooper()).post(() -> {
            synchronized (finishLock) {
                Stream.of(finishListeners)
                        .filter(listener -> listener != null)
                        .forEach(FinishListener::onFinish);
                finishListeners.clear();
            }

            synchronized (errorLock) {
                Stream.of(errorListeners)
                        .filter(listener -> listener != null)
                        .forEach(listener -> listener.onError((Throwable) result));
                errorListeners.clear();
            }
        });

        // Since a result can't be both success and failure at the same time, it's safe to release
        // the success listeners too here.
        synchronized (successLock) {
            successListeners.clear();
        }
    }

    /**
     * Delivers a previously delivered success result again, but only to the given success listener.
     * No finish listeners are called at this point.
     *
     * @param listener The success callback implementation to re-deliver to.
     */
    @SuppressWarnings("unchecked") // Prevent Lint type cast warning
    private void deliverSuccessOnMainThread(SuccessListener<T> listener) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (listener != null)
                listener.onSuccess((T) result);
        });
    }

    /**
     * Delivers a previously delivered error result again, but only to the given error listener. No
     * finish listeners are called at this point.
     *
     * @param listener The error callback implementation to re-deliver to.
     */
    private void deliverErrorOnMainThread(ErrorListener listener) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (listener != null)
                listener.onError((Throwable) result);
        });
    }

    /**
     * Notifies the given finish listener about a already occurred finish state. No other success or
     * error listeners are called at this point.
     *
     * @param listener The finish callback implementation to notify again.
     */
    private void notifyFinishOnMainThread(FinishListener listener) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (listener != null)
                listener.onFinish();
        });
    }

}
