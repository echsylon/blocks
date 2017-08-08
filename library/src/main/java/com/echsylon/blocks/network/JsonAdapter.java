package com.echsylon.blocks.network;

import com.google.gson.JsonElement;

/**
 * This interface describes the feature set of a JSON adapter. JSON adapters are
 * expected to be used when a JSON structure needs custom parsing.
 */
public interface JsonAdapter {

    /**
     * Transforms the given JSON element into a corresponding Java object.
     *
     * @param jsonElement The JSON element to transform.
     * @return The corresponding Java object.
     */
    Object deserialize(JsonElement jsonElement);

}
