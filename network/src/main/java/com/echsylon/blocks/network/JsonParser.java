package com.echsylon.blocks.network;

/**
 * This interface describes the minimum required feature set of a JSON parser in the Blocks
 * infrastructure.
 */
public interface JsonParser {

    /**
     * Tries to map a JSON structure to a custom Java object instance.
     *
     * @param json     The Json string to parse.
     * @param classOfT The class definition of the resulting object.
     * @param <T>      The type of result.
     * @return An instance of the defined class, populated with corresponding data from the given
     * JSON string.
     * @throws IllegalArgumentException If the parsing fails for any reason.
     */
    <T> T fromJson(String json, Class<T> classOfT) throws IllegalArgumentException;

    /**
     * Tries to create a corresponding JSON string from the accessible fields in a Java object.
     *
     * @param object The object to represent as a JSON string.
     * @return The JSON string representation of the given object.
     * @throws IllegalArgumentException If the given object can't be represented as a JSON string
     *                                  for any reason.
     */
    String toJson(Object object) throws IllegalArgumentException;

}
