package com.echsylon.blocks.network.internal;

import android.util.Log;

import com.echsylon.blocks.network.JsonParser;
import com.echsylon.blocks.network.NetworkClient;
import com.echsylon.blocks.network.exception.NoConnectionException;
import com.echsylon.blocks.network.exception.RequestException;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * This class is responsible for executing any HTTP requests and delivering domain objects parsed
 * from the returned JSON content.
 */
public class DefaultNetworkClient implements NetworkClient {
    private static final String LOG_TAG = DefaultNetworkClient.class.getName();
    private static final String JSON_CONTENT_TYPE = "application/json";

    private final JsonParser jsonParser;

    public DefaultNetworkClient(JsonParser jsonParser) {
        this.jsonParser = jsonParser;
    }

    /**
     * Synchronously performs an HTTP request and tries to parse the response content body into an
     * instance of the given class. If anything would go wrong or if any preconditions aren't
     * honored, then an exception will be thrown.
     *
     * @param url                The URL to terminate in.
     * @param method             The request method.
     * @param headers            Any optional key/value header pairs.
     * @param contentBody        Any optional data to send through the request. The data will be
     *                           sent as a JSON string. This method will fail with an exception if
     *                           the object can't be represented in JSON notation.
     * @param expectedResultType The class implementation to parse the given JSON into.
     * @param <T>                The type of the expected answer.
     * @return An object holding the requested data.
     * @throws NoConnectionException If a connection to the given URL couldn't be established
     * @throws RequestException      If the status code suggests a client or server side error.
     * @throws RuntimeException      If any other unexpected runtime error would occur.
     */
    @Override
    public <T> T request(String url, String method, Map<String, String> headers, Object contentBody, Class<T> expectedResultType) {
        HttpURLConnection urlConnection = null;

        try {
            urlConnection = establishConnection(url, method, headers);
            sendJsonContent(urlConnection, contentBody);
            validateResponse(urlConnection);
            String resultString = getJsonContent(urlConnection);
            return parseJson(resultString, expectedResultType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (urlConnection != null)
                urlConnection.disconnect();
        }
    }

    /**
     * Tries to establish a connection to the given url.
     *
     * @param url    The URL to connect to.
     * @param method The method of the connection.
     * @return A connection handle.
     * @throws NoConnectionException If couldn't connect.
     */
    private HttpURLConnection establishConnection(String url, String method, Map<String, String> headers) throws NoConnectionException {
        HttpURLConnection urlConnection = null;

        try {
            urlConnection = (HttpURLConnection) new URL(url).openConnection();
            urlConnection.setRequestMethod(method);

            if (headers != null)
                for (Map.Entry<String, String> entry : headers.entrySet())
                    urlConnection.addRequestProperty(entry.getKey(), entry.getValue());

            return urlConnection;
        } catch (IOException e) {
            if (urlConnection != null)
                urlConnection.disconnect();

            throw new NoConnectionException(e);
        }
    }

    /**
     * Writes an object, expressed as a JSON string, to the url connections output stream. This
     * method will forcefully set the content type of the url connection to application/json and set
     * the "do output" flag to true.
     *
     * @param urlConnection The url connection to write to.
     * @param bodyContent   The body content to write.
     * @throws IOException Would anything go wrong with the output.
     */
    private void sendJsonContent(HttpURLConnection urlConnection, Object bodyContent) throws IOException {
        if (bodyContent == null)
            return;

        String json = jsonParser.toJson(bodyContent);
        DataOutputStream dataOutputStream = null;
        try {
            urlConnection.addRequestProperty("Content-Type", JSON_CONTENT_TYPE);
            urlConnection.setDoOutput(true);
            dataOutputStream = new DataOutputStream(urlConnection.getOutputStream());
            dataOutputStream.writeBytes(json);
            dataOutputStream.flush();
        } finally {
            Utils.closeSilently(dataOutputStream);
        }
    }

    /**
     * Checks the status code of the last request made on the given connection and throws an
     * exception if it's in the HTTP client or server error range.
     *
     * @param urlConnection The connection to fetch the status code and error state from.
     * @throws IOException      If couldn't communicate properly through the url connection.
     * @throws RequestException If the status code suggests a client or server side error.
     * @throws RuntimeException If the provided content isn't stated to be of JSON type.
     */
    private void validateResponse(HttpURLConnection urlConnection) throws IOException, RequestException {
        int responseCode = urlConnection.getResponseCode();
        if (responseCode >= 400)
            throw new RequestException(responseCode, urlConnection.getResponseMessage());

        String contentType = urlConnection.getContentType();
        if (!JSON_CONTENT_TYPE.equalsIgnoreCase(contentType))
            throw new RuntimeException("Unexpected content type: " + contentType);
    }

    /**
     * Reads everything from the connection input stream and returns it as a string.
     *
     * @param urlConnection The url connection to read from.
     * @return The content stream as a string.
     * @throws IOException If anything would go wrong while reading from the url connection.
     */
    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    private String getJsonContent(HttpURLConnection urlConnection) throws IOException {
        String charsetName = urlConnection.getContentEncoding();
        String validatedCharsetName = Utils.notEmpty(charsetName) ? charsetName : "UTF-8";
        StringBuilder stringBuilder = new StringBuilder();
        String line;

        InputStream inputStream = urlConnection.getInputStream();
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream, validatedCharsetName);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        try {
            while ((line = bufferedReader.readLine()) != null)
                stringBuilder.append(line);
        } finally {
            Utils.closeSilently(inputStream);
            Utils.closeSilently(inputStreamReader);
            Utils.closeSilently(bufferedReader);
        }

        return stringBuilder.toString();
    }

    /**
     * Tries to create a new domain object from a json string.
     *
     * @param json The json string to parse.
     * @param type The class object of the corresponding domain object to create.
     * @param <T>  The generic type of domain object class.
     * @return A domain object representing the given json structure.
     * @throws RuntimeException Would the parsing fail for any reason.
     */
    @SuppressWarnings("unchecked") // Prevent Lint warning on String type bail out
    private <T> T parseJson(String json, Class<T> type) throws RuntimeException {
        if (Utils.isEmpty(json) || type == null || type == Void.class)
            return null;

        if (type == String.class)
            return (T) json;

        try {
            return jsonParser.fromJson(json, type);
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "Json parse exception: " + json, e);
            throw new RuntimeException(e);
        }
    }

}
