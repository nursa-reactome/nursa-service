package org.reactome.nursa.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.collections4.iterators.TransformIterator;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.reactome.nursa.model.DataPoint;
import org.reactome.nursa.model.DataSet;
import org.reactome.nursa.model.DataSetSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Fred Loney <loneyf@ohsu.edu>
 */
@RestController
public class DataSetController {

    private static final String NURSA_CACHE_DIR = "/usr/local/reactomes/Reactome/production/nursa";

    private static final String DATASET_CACHE_DIR = NURSA_CACHE_DIR + "/datasets";
    
    Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private NursaSolrClient solrClient;
 
    /**
     * Searches for the given term in the dataset doi, name and description.
     * 
     * @param term the search term
     * @return the JSON {doi, name} matches
     * @throws URISyntaxException if the Nursa REST API uri is malformed
     * @throws IOException if the Nursa REST server could not be accessed
     */
    @RequestMapping("/search")
    public DataSetSearchResult search(@RequestParam(value="term") String term,
                                      @RequestParam(value="start") Optional<Integer> start,
                                      @RequestParam(value="size") Optional<Integer> size) {
        QueryResponse response = solrClient.search(term, start, size);
        SolrDocumentList solrResults = response.getResults();
        DataSetSearchResult searchResult = new DataSetSearchResult();
        long numFound = solrResults.getNumFound();
        searchResult.setNumFound((int) numFound);
        List<DataSet> datasets = solrResults.stream()
                                            .map(DataSetController::asDataSet)
                                            .collect(Collectors.toList());
        searchResult.setDatasets(datasets);
        log.info("Search on \"" + term + "\" matched " + numFound + " datasets.");
        
        return searchResult;
    }
    
    private static DataSet asDataSet(SolrDocument doc) {
        DataSet dataset = new DataSet();
        dataset.setDoi((String) doc.getFieldValue("doi"));
        dataset.setName((String) doc.getFieldValue("name"));
        dataset.setDescription((String) doc.getFieldValue("description"));
        
        return dataset;
    }

    /**
     * Fetches the dataset content for the given dataset identifier.
     * 
     * @param doi the dataset identifier
     * @return the JSON {doi, datapoints} dataset object
     * @throws URISyntaxException if the Nursa REST API uri is malformed
     * @throws IOException if the Nursa REST server could not be accessed
     */
    @RequestMapping("/dataset")
    public DataSet getDataset(@RequestParam(value="doi") String doi)
            throws URISyntaxException, IOException {
        // Check the local file cache.
        String[] relPath = doi.split("/");
        String registrant = relPath[0];
        String objId = relPath[1];
        String dsFile = objId + ".json";
        
        Path path = Paths.get(DATASET_CACHE_DIR, registrant, dsFile);
        DataSet dataset;
        if (Files.exists(path)) {
            try {
                InputStream fis = Files.newInputStream(path);
                ObjectMapper mapper = new ObjectMapper();
                dataset = mapper.readValue(fis, DataSet.class);
                fis.close();
            } catch (Exception e) {
                String message = "Could not read cached Nursa dataset file " + path;
                throw new NursaException(message, e);
            }
            List<DataPoint> dataPoints = dataset.getDataPoints();
            if (dataPoints != null && !dataPoints.isEmpty()) {
                return dataset;
            }
        } else {
            // TODO - seed the names and descriptions from a new Nursa REST API endpoint.
            throw new NursaException("Dataset not found: " + doi);
        }

        // Get the data points in a separate REST call.
        List<DataPoint> dataPoints = getDataPoints(doi);
        dataset.setDataPoints(dataPoints);
        // Cache the dataset.
        File file = path.toFile();
        File dir = file.getParentFile();
        if (!dir.exists()) {
            try {
                dir.mkdirs();
            }
            catch (SecurityException e) {
                String message = "Could not create Nursa dataset cache directory " + dir;
                throw new NursaException(message, e);
            }
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            OutputStream fos = Files.newOutputStream(path);
            mapper.writeValue(fos, dataset);
            fos.close();
        } catch (Exception e) {
            String message = "Nursa dataset could not be cached in " + file;
            throw new NursaException(message, e);
        }
        
        return dataset;
    }

    private List<DataPoint> getDataPoints(String doi) throws URISyntaxException, IOException {
        Iterator<Map<String, Object>> rowIter = NursaRestClient.getDataPoints(doi);
        Transformer<Map<String, Object>, DataPoint> xfm = new Transformer<Map<String, Object>, DataPoint>() {
            @Override
            public DataPoint transform(Map<String, Object> row) {
                DataPoint dataPoint = new DataPoint();
                dataPoint.setSymbol((String) row.get("symbol"));
                // Zero parses as an Integer.
                Number pValue = (Number) row.get("pValue");
                dataPoint.setPvalue(pValue.doubleValue());
                Number fc = (Number) row.get("foldChange");
                dataPoint.setFoldChange(fc.doubleValue());
                return dataPoint;
            }
        };
        Iterator<DataPoint> dpIter = new TransformIterator<Map<String, Object>, DataPoint>(rowIter, xfm);
        List<DataPoint> dataPoints = IteratorUtils.toList(dpIter);
        return dataPoints;
    }
}
