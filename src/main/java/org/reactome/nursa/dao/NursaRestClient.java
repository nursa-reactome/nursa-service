package org.reactome.nursa.dao;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.reactome.nursa.controller.NursaException;
import org.reactome.nursa.model.Experiment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author Fred Loney <loneyf@ohsu.edu>
 */
@Component
public class NursaRestClient {
    
    private static final int DATAPOINTS_COUNT_MAX = 5000;

    private static final String EMPTY_DATASET_ERROR_MSG = "No datapoints in the dataset: ";

    private static final String FETCH_HALTED_ERROR_MSG = "Fetching data points halted for dataset: ";

    private static final String REST_ERROR_MSG = "REST call unsuccessful: ";

    private static final String URI_ERROR_MSG = "Malformed URI for path: ";

    private static final String JSON_ERROR_MSG = "Parsing JSON unsuccessful for REST call: ";

    /**
     * The default required addedsince query parameter for a single dataset
     * is effectively the beginning of time to ensure that the dataset was
     * added after that date.
     */
    private static final String DEF_ADDED_SINCE = "20100101";

    @Value("${nursa.host}")
    private String host;
    
    @Value("${nursa.content.service.path}")
    private String servicePath;
    
    @Value("${nursa.datasets.end.point}")
    private String datasetsEndPoint;
    
    @Value("${nursa.datapoints.end.point}")
    private String datapointsEndPoint;
    
    @Value("${nursa.api.key}")
    private String apiKey;
    
    @Value("${nursa.api.omics.type}")
    private String omicsType;
    
    @Value("${nursa.api.query.type}")
    private String queryType;
    
    /**
     * Calls the SPP REST API to retrieve the given dataset meta-data.
     * 
     * <em>Note</em>: the dataset experiment {@link Experiment#getDataPoints()} 
     * value is null. Data points can be retrieved using the
     * {@link #getDataPoints(String)} method.
     * 
     * @param doi the dataset DOI
     * @return a singleton array containing the dataset
     * @throws URISyntaxException
     * @throws IOException
     */
    public Stream<Map<String, Object>> getDataSet(String doi) {
        Map<String, String> params = new HashMap<>();
        params.put("doi", doi);
        // Must have a default addedsince, even if it is meaningless.
        params.put("addedsince", DEF_ADDED_SINCE);

        return getDocument(datasetsEndPoint, params);
    }
    
    /**
     * Calls the SPP REST API to retrieve the dataset meta-data
     * of datasets added to SPP after the given date.
     * 
     * @see #getDataSet(String)
     * @param after the date cut-off
     * @return an array containing the datasets
     * @throws URISyntaxException
     * @throws IOException
     */
    public Stream<Map<String, Object>> getDataSets(Date after) {
        Map<String, String> params = new HashMap<>();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        String dateParam = format.format(after);    
        params.put("addedsince", dateParam);
        Stream<Map<String, Object>> document = getDocument(datasetsEndPoint, params);
        Stream.of(document);
        
        return document;
    }

    public Stream<Map<String, Object>> getDataPoints(String doi) {
        Map<String, String> params = new HashMap<>();
        params.put("queryValue", doi);
        params.put("omicsType", omicsType);
        params.put("queryType", queryType);
        // Don't apply the default 0.05 pvalue filter.
        params.put("significance", "1.0");
        params.put("countMax", Integer.toString(DATAPOINTS_COUNT_MAX));
        
        // It would be better to return a lazy-initialized stream
        // using Stream.iterate with the takeWhile stream operator,
        // but that requires Java 9. For now, fill a list and
        // return a stream on that list. A stream return value
        // leaves open the possibility of a more efficient
        // implementation in the future.
        List<Map<String, Object>> datapoints = new ArrayList<Map<String, Object>>();
        int startId = 0;
        while (true) {
            // Important - set the starting record parameter or we will
            // find ourselves in an infinite loop.
            params.put("startId", Integer.toString(startId));
            // Fetch some data points.
            List<Map<String, Object>> fetched =
                    getDocument(datapointsEndPoint, params).collect(Collectors.toList());
            // There must be at least one row returned.
            if (fetched.isEmpty()) {
                throw new NursaException(EMPTY_DATASET_ERROR_MSG + doi);
            }
            // Sort the fetched rows. Note that, contrary to the SPP
            // REST documentation as of 04/2019, the rows are not already
            // in id order.
            fetched.sort((r1, r2) -> Integer.compare(
                    ((Number) r1.get("id")).intValue(),
                    ((Number) r2.get("id")).intValue()));
            // If fewer records were returned than requested, then we are
            // done.
            if (fetched.size() < DATAPOINTS_COUNT_MAX) {
                datapoints.addAll(fetched);
                break;
            }
            // The last row fetched  will be redundantly retrieved in the
            // next fetch. We know that there is at least one row at this
            // point because of the size check above.
            Map<String, Object> lastRow = fetched.remove(fetched.size() - 1);
            // The last row id. Note that the id field is only used to
            // determine the starting row to fetch and is not retained.
            int lastId = ((Number) lastRow.get("id")).intValue();
            // Add all but the last fetched rows.
            datapoints.addAll(fetched);
            // Prime the next loop iteration.
            startId = lastId;
            // Give the SPP REST server a two-second rest.
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                // Bail if killed.
                throw new NursaException(FETCH_HALTED_ERROR_MSG + doi);
            }
        }
        
        // See comment above regarding returning a stream.
        return datapoints.stream();
    }

    private Stream<Map<String, Object>> getDocument(String endPoint, Map<String, String> params) {
        // Make the REST URI.
        String path = servicePath + endPoint;
        URIBuilder builder = new URIBuilder()
                .setScheme("https")
                .setHost(host)
                .setPath(path)
                .addParameter("apiKey", apiKey);
        params.forEach(builder::addParameter);
        URI uri;
        try {
            uri = builder.build();
        } catch (URISyntaxException e) {
            throw new NursaException(URI_ERROR_MSG + path, e);
        }

        // Stream the REST result.
        InputStream content;
        try {
            content = Request.Get(uri)
                    .execute()
                    .returnContent()
                    .asStream();
        } catch (IOException e) {
            throw new NursaException(REST_ERROR_MSG + uri, e);
        }

        Reader reader = new InputStreamReader(content);
        try {
            return JsonHelper.parse(reader);
        } catch (IOException e) {
            throw new NursaException(JSON_ERROR_MSG + uri, e);
        }
    }

}
