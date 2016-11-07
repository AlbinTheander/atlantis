package com.echsylon.atlantis.internal.json;

import com.echsylon.atlantis.Configuration;
import com.echsylon.atlantis.Request;
import com.echsylon.atlantis.Response;
import com.echsylon.atlantis.internal.Utils;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * This class provides a set of custom JSON deserializers.
 */
class Serializers {

    /**
     * Returns a new JSON deserializer, specialized for {@link Configuration} objects.
     *
     * @return The deserializer to parse {@code Configuration} JSON with.
     */
    static JsonDeserializer<Configuration> newConfigurationDeserializer() {
        return (json, typeOfT, context) -> deserializeConfiguration(json, context);
    }

    /**
     * Returns a new JSON serializer, specialized for {@link Configuration} objects.
     *
     * @return The serializer to serialize {@code Configuration} objects with.
     */
    static JsonSerializer<Configuration> newConfigurationSerializer() {
        return (object, typeOfObject, context) -> serializeConfiguration(object, context);
    }

    /**
     * Returns a new JSON deserializer, specialized for {@link Request} objects.
     *
     * @return The deserializer to parse {@code Request} JSON with.
     */
    static JsonDeserializer<Request> newRequestDeserializer() {
        return (json, typeOfT, context) -> deserializeRequest(json, context);
    }

    /**
     * Returns a new JSON serializer, specialized for {@link Request} objects.
     *
     * @return The serializer to serialize {@code Request} objects with.
     */
    static JsonSerializer<Request> newRequestSerializer() {
        return (object, typeOfObject, context) -> serializeRequest(object, context);
    }

    /**
     * Returns a new JSON deserializer, specialized for {@link Response} objects.
     *
     * @return The deserializer to parse {@code Response} objects with.
     */
    static JsonDeserializer<Response> newResponseDeserializer() {
        return (json, typeOfT, context) -> deserializeResponse(json, context);
    }

    /**
     * Returns a new JSON serializer, specialized for {@link Response} objects.
     *
     * @return The serializer to serialize {@code Response} objects with.
     */
    static JsonSerializer<Response> newResponseSerializer() {
        return (object, typeOfObject, context) -> serializeResponse(object, context);
    }

    // De-serializes a configuration JSON object, instantiating any request filter classes.
    private static Configuration deserializeConfiguration(JsonElement json, JsonDeserializationContext context) {
        // Remove the 'requestFilter' attribute as it's a string in the JSON. The Configuration
        // object expects it to be a Java object. We'll later parse the removed string (which just
        // happens to be the name of the class to instantiate) and set the object field manually.
        JsonObject jsonObject = json.getAsJsonObject();
        String filterClassName = jsonObject.has("requestFilter") ?
                jsonObject.remove("requestFilter").getAsString() :
                null;

        // Create the Configuration object from the JSON.
        Configuration.Builder configuration = context.deserialize(jsonObject, Configuration.Builder.class);

        // If there was no 'requestFilter' attribute in the JSON, we're done.
        if (Utils.isEmpty(filterClassName))
            return configuration;

        // Otherwise we'll need to instantiate a suitable class and assign it to the corresponding
        // field on the Configuration object.
        try {
            Request.Filter filter = (Request.Filter) Class.forName(filterClassName).newInstance();
            return configuration.withRequestFilter(filter);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    // Serializes a configuration object, serializing any request filter to its class name.
    private static JsonElement serializeConfiguration(Configuration configuration, JsonSerializationContext context) {
        JsonElement jsonElement = context.serialize(configuration);
        if (jsonElement == null)
            return null;

        JsonObject jsonObject = jsonElement.getAsJsonObject();
        jsonObject.remove("requestFilter");

        // If the configuration object has a request filter (which it's expected to have), then
        // replace the complex object with the corresponding class name in the configuration JSON
        // object.
        Request.Filter filter = configuration.requestFilter();
        if (filter != null)
            jsonObject.addProperty("requestFilter", filter.getClass().getName());

        return jsonObject;
    }

    // De-serializes a response JSON object, preparing any header attributes.
    private static Response deserializeResponse(JsonElement json, JsonDeserializationContext context) {
        JsonObject jsonObject = prepareHeaders(json);
        return context.deserialize(jsonObject, Response.Builder.class);
    }

    // Serializes a response object to a JSON string.
    private static JsonElement serializeResponse(Response response, JsonSerializationContext context) {
        return context.serialize(response);
    }

    // De-serializes a request JSON object, preparing any header attributes as well as instantiating
    // any response filter classes.
    private static Request deserializeRequest(JsonElement json, JsonDeserializationContext context) {
        // Remove the 'responseFilter' attribute as it's a string in the JSON. The Configuration
        // object expects it to be a Java object. We'll later parse the removed string (which just
        // happens to be the name of the class to instantiate) and set the object field manually.
        JsonObject jsonObject = prepareHeaders(json);
        String filterClassName = jsonObject.has("responseFilter") ?
                jsonObject.remove("responseFilter").getAsString() :
                null;

        // Create the Request object from the JSON.
        Request.Builder request = context.deserialize(jsonObject, Request.Builder.class);

        // If there was no 'responseFilter' attribute in the JSON, we're done.
        if (Utils.isEmpty(filterClassName))
            return request;

        // Otherwise we'll need to instantiate a suitable class and assign it to the corresponding
        // field on the Request object.
        try {
            Response.Filter filter = (Response.Filter) Class.forName(filterClassName).newInstance();
            return request.withResponseFilter(filter);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    // Serializes a request object, serializing any response filter to its class name.
    private static JsonElement serializeRequest(Request request, JsonSerializationContext context) {
        JsonElement jsonElement = context.serialize(request);
        if (jsonElement == null)
            return null;

        JsonObject jsonObject = jsonElement.getAsJsonObject();
        jsonObject.remove("responseFilter");

        // If the request object has a response filter (which it's expected to have), then
        // replace the complex object with the corresponding class name in the request JSON object.
        Response.Filter filter = request.responseFilter();
        if (filter != null) {
            jsonObject.addProperty("responseFilter", filter.getClass().getName());
        }

        return jsonObject;
    }

    // Ensures any header attribute in the JSON object is formatted properly as a dictionary.
    // Headers can appear as '\n' separated strings (keys and values separated by ':'), or as an
    // array of objects with "key" and "value" attributes, or as a dictionary. This method will
    // transform the two former patterns to the latter.
    private static JsonObject prepareHeaders(JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        JsonElement headers = jsonObject.get("headers");

        if (headers != null) {
            if (headers.isJsonPrimitive()) {
                String headerString = headers.getAsString();
                JsonElement headersJsonElement = splitHeaders(headerString);
                jsonObject.add("headers", headersJsonElement);
            } else if (headers.isJsonArray()) {
                JsonArray headerArray = headers.getAsJsonArray();
                JsonElement headersJsonElement = transformHeaders(headerArray);
                jsonObject.add("headers", headersJsonElement);
            }
        }

        return jsonObject;
    }

    // Splits a header string into a JSON dictionary.
    private static JsonElement splitHeaders(String headerString) {
        JsonObject jsonObject = new JsonObject();
        String[] splitHeaders = headerString.split("\n");

        for (String header : splitHeaders) {
            int firstIndex = header.indexOf(':');
            if (firstIndex != -1) {
                String key = header.substring(0, firstIndex).trim();
                String value = header.substring(firstIndex + 1).trim();
                if (Utils.notEmpty(key) && Utils.notEmpty(value))
                    jsonObject.addProperty(key, value);
            }
        }

        return jsonObject;
    }

    // Transforms an array of header objects into a JSON dictionary
    private static JsonElement transformHeaders(JsonArray headerArray) {
        JsonObject jsonObject = new JsonObject();

        for (JsonElement header : headerArray) {
            if (header.isJsonObject()) {
                JsonObject o = header.getAsJsonObject();
                if (o.has("key") && o.has("value")) {
                    String key = o.get("key").getAsString();
                    String value = o.get("value").getAsString();
                    if (Utils.notEmpty(key) && Utils.notEmpty(value))
                        jsonObject.addProperty(key, value);
                }
            }
        }

        return jsonObject;
    }

}