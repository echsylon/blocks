package com.echsylon.blocks.network;

import com.google.gson.Gson;

/**
 * This class has default knowledge of how to parse JSON into Java objects and
 * Java objects into JSON. It's relying on Google Gson for doing this.
 */
public class DefaultJsonParser implements JsonParser {

    @Override
    public <T> T fromJson(String json, Class<T> expectedResultType) throws IllegalArgumentException {
        try {
            return new Gson().fromJson(json, expectedResultType);
        } catch (Exception e) {
            throw new IllegalArgumentException("Couldn't parse JSON: " + json, e);
        }
    }

    @Override
    public String toJson(Object object) throws IllegalArgumentException {
        try {
            return new Gson().toJson(object);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(
                    "Couldn't serialize object into JSON: %s", object != null ?
                            object.getClass().getName() :
                            "null"),
                    e);
        }
    }

}
