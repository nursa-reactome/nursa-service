package org.reactome.nursa.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

/**
 * @author Fred Loney <loneyf@ohsu.edu>
 */
public class NursaRestClient {
    public static String HOST = "www.nursa.org";
    public static String CONTENT_SERVICE_PATH = "/nursa/rest/api/1/";
    public static String GENE_LIST_END_POINT = "datapoints";
    public static String API_KEY = "78c33248-66f6-43d0-bc64-fad12cea324a";

    public static Iterator<Map<String, Object>> getDataPoints(String doi)
            throws URISyntaxException, IOException {
        Map<String, String> params = new HashMap<>();
        params.put("doi", doi);

        return getDocument(GENE_LIST_END_POINT, params);
    }

    private static Iterator<Map<String, Object>> getDocument(String endPoint, Map<String, String> params)
            throws URISyntaxException, IOException {
        // Make the REST URI.
        String path = NursaRestClient.CONTENT_SERVICE_PATH + endPoint + "/query/";
        URIBuilder builder = new URIBuilder()
                .setScheme("https")
                .setHost(HOST)
                .setPath(path)
                .addParameter("apiKey", NursaRestClient.API_KEY);
        params.forEach(builder::addParameter);
        URI uri = builder.build();

        // Stream the REST result.
        InputStream content = Request.Get(uri)
                .execute()
                .returnContent()
                .asStream();

        // Parse the JSON into an iterator over name-value map records.
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() { };
        ObjectReader reader = mapper.readerFor(typeRef);

        return reader.readValues(content);
    }
}
