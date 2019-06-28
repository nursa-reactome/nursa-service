package org.reactome.nursa.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import org.reactome.nursa.model.DataSet;
import org.reactome.nursa.model.DisplayableDataPoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

/**
 * NursaControllerTest tests the Nursa service REST API.
 *
 * <em>Note</em>: this test suite requires the Nursa service to be running
 * locally.
 *
 * @author Fred Loney <loneyf@ohsu.edu>
 */
public class NursaControllerTest {
    private static final String NO_REACTOME_DATA_POINTS_MSG = "No data point genes are in Reactome";
    private final String HOST_URL = "localhost";
    private final int PORT = 8484;
    
    @Test
    public void testDataset() throws Exception {
        String doi = "10.1621/yAyJGMai3I";
        DataSet dataset = testDataset(doi, true);
        assertEquals("Refreshed DOI incorrect", doi, dataset.getDoi());
        assertNotNull("Refreshed ataset name missing", dataset.getName());
        assertEquals("Refreshed experiment count incorrect", 4, dataset.getExperiments().size());
        // Retry from cache.
        dataset = testDataset(doi, false);
        assertEquals("Cached DOI incorrect", doi, dataset.getDoi());
        assertNotNull("Cached ataset name missing", dataset.getName());
        assertEquals("Cached experiment count incorrect", 4, dataset.getExperiments().size());
    }

    private DataSet testDataset(String doi, Boolean refresh) throws Exception {
        URIBuilder builder = new URIBuilder()
                .setScheme("http")
                .setHost(HOST_URL)
                .setPort(PORT)
                .setPath("/dataset")
                .setParameter("doi", doi);
        if (refresh != null) {
            builder.setParameter("refresh", refresh.toString());
        }
        URI uri = builder.build();
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
        
        return dataset;
    }
    
    // This is potentially an expensive test to run, since the
    // count parameter is not yet supported by the REST API,
    // so several datasets are fetched.
    //@Test
    public void testDatasets() throws Exception {
        String after = "20190301";
        URI uri = new URIBuilder()
                .setScheme("http")
                .setHost(HOST_URL)
                .setPort(PORT)
                .setPath("/datasets")
                .setParameter("addedsince", after)
                .setParameter("count", "1")
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
        CollectionType javaType = mapper.getTypeFactory()
                .constructCollectionType(List.class, DataSet.class);
        List<DataSet> datasets = mapper.readValue(content, javaType);
        assertEquals("Dataset incorrect", 1, datasets.size());
        DataSet dataset = datasets.get(0);
        assertEquals("Dataset DOI incorrect", "10.1621/rBA9gFbnje", dataset.getDoi());
        assertEquals("Experiment count incorrect", 4, dataset.getExperiments().size());
    }
    
    @Test
    public void testDataPoints() throws Exception {
        String doi = "10.1621/yAyJGMai3I";
        int experimentId = 818463;
        URI uri = new URIBuilder()
                .setScheme("http")
                .setHost(HOST_URL)
                .setPort(PORT)
                .setPath("/datapoints")
                .setParameter("doi", doi)
                .setParameter("experimentId", Integer.toString(experimentId))
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
                .constructCollectionType(List.class, DisplayableDataPoint.class);
        List<DisplayableDataPoint> dataPoints = mapper.readValue(content, javaType);
        assertEquals("Data points count incorrect", 23066, dataPoints.size());
        // At least one gene should be in Reactome.
        boolean hasReactomeGene = dataPoints.stream()
                .anyMatch(DisplayableDataPoint::isReactome);
        assertTrue(NO_REACTOME_DATA_POINTS_MSG, hasReactomeGene);
    }

}
