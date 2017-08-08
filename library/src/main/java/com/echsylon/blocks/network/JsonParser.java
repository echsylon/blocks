package com.echsylon.blocks.network;

/**
 * This interface describes the minimum required feature set of a JSON parser.
 */
public interface JsonParser {

    /**
     * Tries to map a JSON structure to a custom Java object instance.
     *
     * @param json The raw json to parse.
     * @param <T>  The type of the result class instance.
     * @return An instance of the expected result class, populated with
     * corresponding data from the raw data object.
     * @throws IllegalArgumentException If the parsing fails for any reason.
     */
    <T> T fromJson(String json) throws IllegalArgumentException;

    /**
     * Tries to create a corresponding JSON string from the accessible fields in
     * a Java object.
     *
     * @param object The object to serialize into a JSON string.
     * @return The Json representation of the given data object.
     * @throws IllegalArgumentException If the given object can't be represented
     *                                  as a JSON string for any reason.
     */
    String toJson(Object object) throws IllegalArgumentException;

}
