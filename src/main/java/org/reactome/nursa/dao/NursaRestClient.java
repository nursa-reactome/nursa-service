package org.reactome.nursa.dao;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

/**
 * @author Fred Loney <loneyf@ohsu.edu>
 */
@Component
public class NursaRestClient {
    
    @Value("${nursa.host}")
    private String host;
    
    @Value("${nursa.content.service.path}")
    private String servicePath;
    
    @Value("${nursa.gene.list.end.point}")
    private String endPoint;
    
    @Value("${nursa.api.key}")
    private String apiKey;
    
    public Iterator<Map<String, Object>> getDataPoints(String doi)
            throws URISyntaxException, IOException {
        Map<String, String> params = new HashMap<>();
        params.put("doi", doi);

        return getDocument(endPoint, params);
    }

    private Iterator<Map<String, Object>> getDocument(String endPoint, Map<String, String> params)
            throws URISyntaxException, IOException {
        // Make the REST URI.
        String path = servicePath + endPoint + "/query/";
        URIBuilder builder = new URIBuilder()
                .setScheme("https")
                .setHost(host)
                .setPath(path)
                .addParameter("apiKey", apiKey);
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
