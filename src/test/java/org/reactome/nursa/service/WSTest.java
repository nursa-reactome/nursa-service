package org.reactome.nursa.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.reactome.nursa.model.DataPoint;
import org.reactome.nursa.model.DataSet;
import org.reactome.nursa.model.Experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

public class WSTest {
    private final String HOST_URL = "localhost";
    private final int PORT = 8484;
    
    @Test
    public void testDataset() throws Exception {
        String doi = "10.1621/yAyJGMai3I";
        URI uri = new URIBuilder()
                .setScheme("http")
                .setHost(HOST_URL)
                .setPort(PORT)
                .setPath("/dataset")
                .setParameter("doi", doi)
                .build();
        HttpPut request = new HttpPut(uri);
        request.setHeader("Accept", "application/json");
        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response = client.execute(request);
        String content = null;
        try {
            HttpEntity entity = response.getEntity();
            content = EntityUtils.toString(entity, StandardCharsets.UTF_8);
        } finally {
            response.close();
        }
        ObjectMapper mapper = new ObjectMapper();
        DataSet dataset = mapper.readValue(content, DataSet.class);
        assertEquals("DOI incorrect", doi, dataset.getDoi());
        assertNotNull("Dataset description missing", dataset.getDescription());
        assertEquals("Experiment count incorrect", 4, dataset.getExperiments().size());
    }
    
    @Test
    public void testDataPoints() throws Exception {
        String doi = "10.1621/yAyJGMai3I";
        int expNbr = 1;
        URI uri = new URIBuilder()
                .setScheme("http")
                .setHost(HOST_URL)
                .setPort(PORT)
                .setPath("/datapoints")
                .setParameter("doi", doi)
                .setParameter("experimentNumber", Integer.toString(expNbr))
                .build();
        HttpPut request = new HttpPut(uri);
        request.setHeader("Accept", "application/json");
        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response = client.execute(request);
        String content = null;
        try {
            HttpEntity entity = response.getEntity();
            content = EntityUtils.toString(entity, StandardCharsets.UTF_8);
        } finally {
            response.close();
        }
        assertEquals("Status code not OK", 200, response.getStatusLine().getStatusCode());
        ObjectMapper mapper = new ObjectMapper();
        CollectionType javaType = mapper.getTypeFactory()
                .constructCollectionType(List.class, DataPoint.class);
        List<DataPoint> dataPoints = mapper.readValue(content, javaType);
        assertEquals("Data points count incorrect", 22, dataPoints.size());
    }

}
