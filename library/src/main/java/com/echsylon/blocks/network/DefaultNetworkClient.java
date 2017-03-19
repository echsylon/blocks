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
public class DefaultNetworkClient implements NetworkClient {

    /**
     * This class allows the caller to configure the network client behavior.
     * Any custom implementations are encouraged to override members of this
     * default implementation.
     */
    public static class Settings {
        private int maybeForcedCacheDuration = 0;
        private int forcedCacheDuration = 0;

        /**
         * @return Boolean true if redirects should be followed. Defaults to
         * true.
         */
        public boolean followRedirects() {
            return true;
        }

        /**
         * @return Boolean true if HTTPS to HTTP redirects should be followed.
         * Defaults to true.
         */
        public boolean followSslRedirects() {
            return true;
        }

        /**
         * @return Boolean true if cached content should be returned on
         * connectivity errors. Defaults to false. Note that it's the clients
         * and servers responsibility to provide proper cache headers to
         * populate the cache in the first place.
         */
        public boolean fallbackToCache() {
            return false;
        }

        /**
         * @return The max age in seconds of a cache entry to accept when
         * falling back to cached content.
         */
        public int maxFallbackCacheStale() {
            return 0;
        }

        /**
         * Sets the time to force-cache responses that the server doesn't
         * specify any cache metrics for. Zero (0) means don't cache. Defaults
         * to zero.
         *
         * @param seconds The time to live in seconds.
         */
        public void maybeForceCache(int seconds) {
            maybeForcedCacheDuration = seconds;
        }

        /**
         * @return The number of seconds any unconstrained server responses will
         * currently be cached for.
         */
        public int maybeForcedCacheDuration() {
            return maybeForcedCacheDuration;
        }

        /**
         * Sets the time to force-cache server responses for. This method will
         * override any server provided cache constraints. Zero (0) means don't
         * cache. Defaults to zero.
         *
         * @param seconds The time to live in seconds.
         */
        public void forceCache(int seconds) {
            forcedCacheDuration = seconds;
        }

        /**
         * @return The number of seconds any server response will currently be
         * cached for.
         */
        public int forcedCacheSeconds() {
            return forcedCacheDuration;
        }

        /**
         * @return The directory to cache data in. Null means no cache. Defaults
         * to no cache (returns null).
         */
        public File cacheDirectory() {
            return null;
        }

        /**
         * @return The maximum amount of bytes to use for caching. Ignored if no
         * cache directory is provided.
         */
        public long maxCacheSizeBytes() {
            return 0;
        }
    }

    /**
     * This class provides the body content to the real request as expected by
     * the backing HTTP client.
     */
    private static final class JsonRequestBody extends RequestBody {
        private final byte[] payload;

        private JsonRequestBody(final byte[] payload) {
            this.payload = payload == null ?
                    new byte[0] :
                    payload;
        }

        @Override
        public MediaType contentType() {
            return MediaType.parse("application/json");
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            sink.write(payload);
        }
    }

    /**
     * Initializes the runtime state of the single {@code DefaultNetworkClient}
     * object with the supplied settings. Any previously created instance is
     * forcefully shut down and replaced with this new instance.
     *
     * @param settings The custom settings to initialize the new instance with.
     * @return The singleton instance of this class. Never null.
     */
    public static DefaultNetworkClient createNewInstance(final Settings settings) {
        if (instance != null) {
            instance.shutdownNow();
            instance = null;
        }

        instance = new DefaultNetworkClient(settings == null ?
                new Settings() :
                settings);

        return instance;
    }

    /**
     * Ensures there is a singleton instance of this class and returns it.
     *
     * @return The singleton instance of this class. Never null.
     */
    public static DefaultNetworkClient getInstance() {
        return instance == null ?
                createNewInstance(null) :
                instance;
    }


    private static DefaultNetworkClient instance;
    private OkHttpClient okHttpClient;
    private Settings settings;

    /**
     * Intentionally hidden constructor
     */
    private DefaultNetworkClient(Settings settings) {
        File cacheDir = settings.cacheDirectory();
        Cache cache = cacheDir != null ?
                new Cache(cacheDir, settings.maxCacheSizeBytes()) :
                null;

        this.settings = settings;
        this.okHttpClient = new OkHttpClient.Builder()
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
    public byte[] execute(String url,
                          String method,
                          List<Header> headers,
                          byte[] payload) {

        return execute(url, method, headers, payload, false);
    }

    /**
     * Same as {@link #execute(String, String, List, byte[])} but with an
     * additional fallback flag controlling any fallback-to-cache behavior.
     */
    private byte[] execute(final String url,
                           final String method,
                           final List<Header> headers,
                           final byte[] payload,
                           final boolean isFallback) {

        if (okHttpClient == null)
            throw new IllegalStateException("Calling execute() after shutdown() was called");

        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(url);
        requestBuilder.method(method, payload != null ?
                new JsonRequestBody(payload) :
                null);

        if (headers != null)
            Stream.of(headers)
                    .forEach(header -> requestBuilder.addHeader(header.key, header.value));

        if (isFallback)
            requestBuilder.cacheControl(new CacheControl.Builder()
                    .maxAge(settings.maxFallbackCacheStale(), TimeUnit.SECONDS)
                    .build());

        try {
            Request request = requestBuilder.build();
            Call call = okHttpClient.newCall(request);
            Response response = call.execute();

            if (response.isSuccessful())
                return response.body().bytes();

            throw new ResponseStatusException(response.code(), response.message());
        } catch (IOException e) {
            info(e, "Couldn't execute request due to a connectivity error: %s", url);
            if (!isFallback && settings.fallbackToCache())
                return execute(url, method, headers, payload, true);
            throw new NoConnectionException(e);
        } catch (IllegalStateException e) {
            info(e, "This request instance has already been executed: %s", url);
            throw new RuntimeException(e);
        }
    }

    /**
     * Forces the DefaultNetworkClient to aggressively release its internal
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
        if (settings.forcedCacheDuration > 0)
            response = response.newBuilder()
                    .header("Cache-Control", "max-age=" +
                            settings.forcedCacheDuration)
                    .build();

        // Maybe force any client side cache settings
        if (settings.maybeForcedCacheDuration > 0 &&
                response.header("Cache-Control") == null)
            response = response.newBuilder()
                    .header("Cache-Control", "max-age=" +
                            settings.maybeForcedCacheDuration)
                    .build();

        return response;
    }

}
