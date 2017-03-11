package com.echsylon.blocks.network;

import com.annimon.stream.Stream;
import com.echsylon.blocks.network.exception.NoConnectionException;
import com.echsylon.blocks.network.exception.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
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
    public static class SettingsFactory {
        /**
         * @return Boolean true if redirects should be followed. Defaults to
         * true.
         */
        public boolean followRedirects() {
            return true;
        }

        /**
         * @return Boolean true if HTTPS <-> HTTP redirects should be followed.
         * Defaults to true.
         */
        public boolean followSslRedirects() {
            return true;
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
     * Initializes the runtime state of the {@code DefaultNetworkClient} objects
     * with default settings.
     *
     * @see SettingsFactory for defaults.
     */
    public static void initialize() {
        initialize(new SettingsFactory());
    }

    /**
     * Initializes the runtime state of the {@code DefaultNetworkClient} objects
     * with custom settings.
     *
     * @param settings The custom settings.
     */
    public static void initialize(final SettingsFactory settings) {
        if (okHttpClient == null) {
            File cacheDir = settings.cacheDirectory();
            Cache cache = cacheDir != null ?
                    new Cache(cacheDir, settings.maxCacheSizeBytes()) :
                    null;

            okHttpClient = new OkHttpClient.Builder()
                    .followSslRedirects(settings.followSslRedirects())
                    .followRedirects(settings.followRedirects())
                    .cache(cache)
                    .build();
        }
    }

    /**
     * Forces the DefaultNetworkClient to aggressively release its internal
     * resources and reset it's state.
     */
    public static void shutdownNow() {
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


    private static OkHttpClient okHttpClient;

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
     * @throws RuntimeException        If any other unexpected runtime error
     *                                 would occur.
     */
    @Override
    public byte[] execute(String url,
                          String method,
                          List<Header> headers,
                          byte[] payload) {

        if (okHttpClient == null) {
            info("Initializing default http client");
            initialize();
        }

        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(url);
        requestBuilder.method(method, new JsonRequestBody(payload));
        Stream.of(headers)
                .forEach(header -> requestBuilder.addHeader(header.key, header.value));

        try {
            Request request = requestBuilder.build();
            Call call = okHttpClient.newCall(request);
            Response response = call.execute();

            if (response.isSuccessful())
                response.body().bytes();

            throw new ResponseStatusException(response.code(), response.message());
        } catch (IOException e) {
            info(e, "Couldn't execute request due to some connectivity issues: %s", url);
            throw new NoConnectionException(e);
        } catch (IllegalStateException e) {
            info(e, "This request instance has already been executed: %s", url);
            throw new RuntimeException(e);
        }
    }

}
