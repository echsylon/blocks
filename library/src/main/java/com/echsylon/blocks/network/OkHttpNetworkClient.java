package com.echsylon.blocks.network;

import com.annimon.stream.Stream;
import com.echsylon.blocks.network.exception.NoConnectionException;
import com.echsylon.blocks.network.exception.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

import static com.echsylon.blocks.network.Utils.closeSilently;
import static com.echsylon.blocks.network.Utils.info;

/**
 * This class is responsible for executing any HTTP requests and delivering
 * domain objects parsed from the returned JSON content.
 */
@SuppressWarnings("WeakerAccess")
public class OkHttpNetworkClient implements NetworkClient {

    /**
     * This class is expected to be extended by custom request builders. It's
     * sole purpose is to expose a {@code OkHttpNetworkClient} and means of
     * preparing it with client side enforced cache behavior.
     *
     * @param <T> The type of the extending class. The cache configuration
     *            methods are forced to type cast to and return such an object,
     *            in order to allow convenient method chaining.
     */
    public static class CachedRequestBuilder<T extends CachedRequestBuilder> {
        protected final OkHttpNetworkClient okHttpNetworkClient = new OkHttpNetworkClient();

        /**
         * Sets a forced cache age for a success response to this request. This
         * cache age will override any cache metrics provided by the server.
         *
         * @param seconds The max age in seconds.
         * @return This builder object, allowing method chaining.
         */
        @SuppressWarnings("unchecked")
        public T hardCache(int seconds) {
            okHttpNetworkClient.forcedCacheDuration = seconds;
            return (T) this;
        }

        /**
         * Sets an optional cache age for a success response to this request.
         * This cache age will only be honored if the server doesn't provide any
         * cache metrics.
         *
         * @param seconds The max age in seconds.
         * @return This builder object, allowing method chaining.
         */
        @SuppressWarnings("unchecked")
        public T softCache(int seconds) {
            okHttpNetworkClient.maybeForcedCacheDuration = seconds;
            return (T) this;
        }

        /**
         * Sets a max age of expired cache entries during which they will still
         * be accepted.
         *
         * @param seconds The max age in seconds.
         * @return This builder object, allowing method chaining.
         */
        @SuppressWarnings("unchecked")
        public T maxStale(int seconds) {
            okHttpNetworkClient.maxStaleDuration = seconds;
            return (T) this;
        }
    }

    /**
     * This class allows the caller to configure the com.echsylon.blocks.network
     * client behavior. Some settings, cache settings (directory and size), or
     * redirect settings are only read once during the life time of the parent
     * com.echsylon.blocks.network client. Other settings may change during
     * runtime.
     */
    public static class Settings {
        private final File cacheDirectory;
        private final int cacheSizeBytes;
        private final boolean followSslRedirects;
        private final boolean followRedirects;

        /**
         * Creates a default settings object with no cache and allowing all
         * kinds of redirects.
         */
        public Settings() {
            this(null, 0, true, true);
        }

        /**
         * Creates a new settings object with custom configuration.
         *
         * @param cacheDirectory     The directory to store response caches in.
         * @param cacheSizeBytes     The maximum size of the cache.
         * @param followRedirects    Whether to follow redirects or not.
         * @param followSslRedirects Whether to follow SSL redirects that
         *                           redirect to non-SSL endpoints or not.
         */
        public Settings(final File cacheDirectory,
                        final int cacheSizeBytes,
                        final boolean followRedirects,
                        final boolean followSslRedirects) {

            this.cacheDirectory = cacheDirectory;
            this.cacheSizeBytes = cacheSizeBytes;
            this.followRedirects = followRedirects;
            this.followSslRedirects = followSslRedirects;
        }

        /**
         * @return The directory to cache data in. Null means no cache. Defaults
         * to null.
         */
        public File cacheDirectory() {
            return cacheDirectory;
        }

        /**
         * @return The maximum amount of bytes to use for caching. Ignored if no
         * cache directory is provided.
         */
        public long maxCacheSizeBytes() {
            return cacheSizeBytes;
        }

        /**
         * @return Boolean true if redirects should be followed. Defaults to
         * true.
         */
        public boolean followRedirects() {
            return followRedirects;
        }

        /**
         * @return Boolean true if HTTPS to HTTP redirects should be followed.
         * Defaults to true.
         */
        public boolean followSslRedirects() {
            return followSslRedirects;
        }

    }

    /**
     * This class provides the body content to the real request as expected by
     * the backing HTTP client.
     */
    private static final class DefaultRequestBody extends RequestBody {
        /**
         * Prepares a new instance of the {@code DefaultRequestBody} class with
         * the given payload and content type, or null if payload is null.
         *
         * @param payload     The request body as a byte array.
         * @param contentType The content type of the request body.
         * @return A request body wrapper object or null if a null pointer
         * payload is provided.
         */
        private static DefaultRequestBody prepare(final byte[] payload,
                                                  final String contentType) {
            return payload != null ?
                    new DefaultRequestBody(payload, contentType) :
                    null;
        }

        private final byte[] payload;
        private final String contentType;

        private DefaultRequestBody(final byte[] payload,
                                   final String contentType) {
            this.contentType = contentType;
            this.payload = payload;
        }

        @Override
        public MediaType contentType() {
            return MediaType.parse(contentType);
        }

        @Override
        public void writeTo(final BufferedSink sink) throws IOException {
            sink.write(payload);
        }
    }

    /**
     * Enables means of injecting default settings that will apply to all new
     * instances of this network client implementation.
     */
    public static void settings(final Settings newSettings) {
        settings = newSettings == null ?
                new Settings() :
                newSettings;
    }

    private static OkHttpClient okHttpClient;
    private static Settings settings;
    private int maxStaleDuration = 0;
    private int maybeForcedCacheDuration = 0;
    private int forcedCacheDuration = 0;

    public OkHttpNetworkClient() {
        if (settings == null)
            settings = new Settings();

        File cacheDir = settings.cacheDirectory();
        Cache cache = cacheDir != null ?
                new Cache(cacheDir, settings.maxCacheSizeBytes()) :
                null;

        okHttpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(this::maybeOverrideCacheControl)
                .followSslRedirects(settings.followSslRedirects())
                .followRedirects(settings.followRedirects())
                .cache(cache)
                .build();
    }

    /**
     * Synchronously performs an HTTP request and tries to parse the response
     * content body into an instance of the given class. If anything would go
     * wrong or if any preconditions aren't honored, then an exception will be
     * thrown.
     *
     * @param url     The URL to terminate in.
     * @param method  The request method.
     * @param headers Any optional key/value header pairs.
     * @param payload Any optional data to send through the request.
     * @return The server response body.
     * @throws ResponseStatusException If the response returned an unsuccessful
     *                                 (4xx or 5xx) status code.
     * @throws NoConnectionException   If a connection to the given URL couldn't
     *                                 be established
     * @throws IllegalStateException   If execute() is called after shutdown()
     *                                 has been called.
     * @throws RuntimeException        If any other unexpected runtime error has
     *                                 occurred.
     */
    @Override
    public byte[] execute(final String url,
                          final String method,
                          final List<Header> headers,
                          final byte[] payload,
                          final String contentType) {

        return executeWithFallback(url, method, headers, payload, contentType, false);
    }

    /**
     * Forces the OkHttpNetworkClient to aggressively release its internal
     * resources and reset it's state.
     * <p>
     * Any enqueued requests that isn't actively executing yet are removed
     * from the queue. A best-effort attempt is made to also terminate any
     * executing requests, but no guarantees can be given that they will
     * terminate immediately.
     */
    public void shutdownNow() {
        if (okHttpClient != null) {
            Dispatcher dispatcher = okHttpClient.dispatcher();
            if (dispatcher != null) {
                ExecutorService executorService = dispatcher.executorService();
                if (executorService != null)
                    executorService.shutdownNow();
            }

            ConnectionPool connectionPool = okHttpClient.connectionPool();
            if (connectionPool != null)
                connectionPool.evictAll();

            Cache cache = okHttpClient.cache();
            if (cache != null)
                closeSilently(cache);

            okHttpClient = null;
        }
    }

    /**
     * Internal implementation of the {@link #execute(String, String, List,
     * byte[], String)} method.
     */
    private byte[] executeWithFallback(final String url,
                                       final String method,
                                       final List<Header> headers,
                                       final byte[] payload,
                                       final String contentType,
                                       final boolean doFallback) {

        if (okHttpClient == null)
            throw new IllegalStateException("Calling execute() after shutdown() was called");

        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(url);
        requestBuilder.method(method, DefaultRequestBody.prepare(payload, contentType));
        requestBuilder.cacheControl(new CacheControl.Builder()
                .maxStale(maxStaleDuration, TimeUnit.SECONDS)
                .build());

        if (headers != null)
            Stream.of(headers)
                    .forEach(header -> requestBuilder.addHeader(header.key, header.value));

        try {
            Request request = requestBuilder.build();
            Call call = okHttpClient.newCall(request);
            Response response = call.execute();

            if (response.isSuccessful())
                return response.body().bytes();

            throw new ResponseStatusException(response.code(), response.message());
        } catch (IOException e) {
            info(e, "Couldn't execute request due to a connectivity error: %s", url);

            // Only fallback to expired cache if not already in fallback mode and fallback is
            // enabled.
            if (!doFallback && maxStaleDuration > 0) {
                info("Attempting a cache fallback: %s", url);
                return executeWithFallback(url, method, headers, payload, contentType, true);
            }

            // No fallback, throw exception.
            throw new NoConnectionException(e);
        } catch (IllegalStateException e) {
            info(e, "This request instance has already been executed: %s", url);
            throw new RuntimeException(e);
        }
    }

    /**
     * Applies any forced client side cache control settings.
     *
     * @param chain The OkHttp request chain.
     * @return The (maybe) prepared request.
     * @throws IOException Would something go wrong in the request chain.
     */
    private Response maybeOverrideCacheControl(final Interceptor.Chain chain) throws IOException {
        // Let the original response come through.
        Response response = chain.proceed(chain.request());

        // Force any hard client side cache settings
        if (forcedCacheDuration > 0)
            response = response
                    .newBuilder()
                    .header("Cache-Control", "public, max-age=" + forcedCacheDuration)
                    .build();

        // Maybe force any client side cache settings
        if (maybeForcedCacheDuration > 0 && response.header("Cache-Control") == null)
            response = response
                    .newBuilder()
                    .header("Cache-Control", "public, max-age=" + maybeForcedCacheDuration)
                    .build();

        return response;
    }

}
