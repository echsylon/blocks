package com.echsylon.blocks.network;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * This class has default knowledge of how to parse JSON into Java objects and
 * Java objects into JSON. It's relying heavily on Google Gson for doing this.
 */
public class DefaultJsonParser implements JsonParser {

    private final GsonBuilder gsonBuilder;


    public DefaultJsonParser() {
        gsonBuilder = new GsonBuilder();
    }

    /**
     * Injects a custom JSON to POJO adapter for a certain data type.
     *
     * @param type         The default type of the POJO to insert the custom
     *                     adapter for.
     * @param deserializer The adapter implementation.
     * @return This parser implementation, allowing chaining of method calls.
     */
    public DefaultJsonParser registerAdapter(Class type, JsonDeserializer deserializer) {
        gsonBuilder.registerTypeAdapter(type, deserializer);
        return this;
    }

    /**
     * Injects a custom POJO to JSON adapter for a certain data type.
     *
     * @param type       The default type of the POJO to insert the custom
     *                   adapter for.
     * @param serializer The adapter implementation.
     * @return This parser implementation, allowing chaining of method calls.
     */
    public DefaultJsonParser registerAdapter(Class type, JsonSerializer serializer) {
        gsonBuilder.registerTypeAdapter(type, serializer);
        return this;
    }

    /**
     * Creates a POJO from a JSON string.
     *
     * @param json               The raw json to parse.
     * @param expectedResultType The class definition of the resulting object.
     * @param <T>                The generic type of the result.
     * @return The desired POJO.
     * @throws IllegalArgumentException If the JSON couldn't be parsed as the
     *                                  desired object for any reason.
     */
    @Override
    public <T> T fromJson(String json, Class<T> expectedResultType) throws IllegalArgumentException {
        try {
            return gsonBuilder
                    .create()
                    .fromJson(json, expectedResultType);
        } catch (Exception e) {
            throw new IllegalArgumentException("Couldn't parse JSON: " + json, e);
        }
    }

    /**
     * Creates a POJO from a JSON string
     *
     * @param json               The raw json to parse.
     * @param expectedResultType The type definition of the resulting object.
     * @param <T>                The generic type of the result.
     * @return The desired POJO.
     * @throws IllegalArgumentException If the JSON couldn't be parsed as the
     *                                  desired object for any reason.
     */
    public <T> T fromJson(String json, Type expectedResultType) throws IllegalArgumentException {
        try {
            return gsonBuilder
                    .create()
                    .fromJson(json, expectedResultType);
        } catch (Exception e) {
            throw new IllegalArgumentException("Couldn't parse JSON: " + json, e);
        }
    }

    /**
     * Creates a JSON string from a POJO.
     *
     * @param object The object to serialize into a JSON string.
     * @return A JSON representation of the given POJO.
     * @throws IllegalArgumentException If the POJO couldn't be expressed as a
     *                                  JSON string for any reason.
     */
    @Override
    public String toJson(Object object) throws IllegalArgumentException {
        try {
            return gsonBuilder
                    .create()
                    .toJson(object);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(
                    "Couldn't serialize object into JSON: %s", object != null ?
                            object.getClass().getName() :
                            "null"),
                    e);
        }
    }

}
