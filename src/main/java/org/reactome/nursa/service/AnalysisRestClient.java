package org.reactome.nursa.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

/**
 * This AnalysisRestClient class is the REST interface to perform
 * Reactome analysis on the server in background when a Nursa
 * dataset is obtained from the Nursa REST service.
 * 
 * @author Fred Loney <loneyf@ohsu.edu>
 */
public class AnalysisRestClient {
    // Note - localhost/AnalysisService only works when there is
    // an Apache redirect. For testing, point to the production
    // Reactome server.
    // TODO - point to localhost/AnalysisService.
    //public static String HOST = "localhost";
    public static String HOST = "reactome.org";
    //public static String CONTENT_SERVICE_PATH = "";
    public static String CONTENT_SERVICE_PATH = "/AnalysisService";
    public static String END_POINT = "/identifiers";

    public static Map<String, Object> analyse(String[] symbols)
            throws URISyntaxException, IOException {
        String payload = String.join("\n", symbols);
        return getDocument(END_POINT, payload);
    }

    public static Map<String, Object> analyse(List<String> symbols)
            throws URISyntaxException, IOException {
        return analyse(symbols.toArray(new String[symbols.size()]));
    }

    private static Map<String, Object> getDocument(String endPoint, String payload)
            throws URISyntaxException, IOException {
        // Make the REST URI.
        String path = AnalysisRestClient.CONTENT_SERVICE_PATH + endPoint + "/projection/";
        URIBuilder builder = new URIBuilder()
                .setScheme("http")
                .setHost(HOST)
                .setPath(path);
        URI uri = builder.build();

        // Stream the REST result.
        InputStream content = Request.Post(uri)
                .bodyString(payload, ContentType.DEFAULT_TEXT)
                .execute()
                .returnContent()
                .asStream();

        // Parse the JSON into an iterator over name-value map records.
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() { };
        ObjectReader reader = mapper.readerFor(typeRef);

        return reader.readValue(content);
    }
}
