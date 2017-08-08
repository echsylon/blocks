package com.echsylon.blocks.network;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.reflect.TypeToken;

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
     * @param adapter The adapter implementation.
     */
    public void registerAdapter(final JsonAdapter adapter) {
        gsonBuilder.registerTypeAdapter(Object.class,
                (JsonDeserializer<Object>) (json, typeOfT, context) ->
                        adapter.deserialize(json));
    }

    /**
     * Creates a POJO from a JSON string.
     *
     * @param json The raw json to parse.
     * @param <T>  The generic type of the result.
     * @return The desired POJO.
     * @throws IllegalArgumentException If the JSON couldn't be parsed as the
     *                                  desired object for any reason.
     */
    @Override
    public <T> T fromJson(String json) throws IllegalArgumentException {
        try {
            return gsonBuilder
                    .create()
                    .fromJson(json, new TypeToken<T>() {}.getType());
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
