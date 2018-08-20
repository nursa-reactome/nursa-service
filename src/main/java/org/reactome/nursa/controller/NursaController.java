package org.reactome.nursa.controller;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.reactome.nursa.model.DataPoint;
import org.reactome.nursa.model.DataSet;
import org.reactome.nursa.model.DataSetSearchResult;
import org.reactome.nursa.model.Experiment;
import org.reactome.nursa.controller.NursaException;
import org.reactome.nursa.dao.NursaRestClient;
import org.reactome.nursa.dao.NursaSolrClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Fred Loney <loneyf@ohsu.edu>
 */
@RestController
public class NursaController {

    private static final Logger logger = Logger.getLogger(NursaController.class);
 
    private static final String NURSA_CACHE_DIR =
            "/usr/local/reactomes/Reactome/production/nursa";

    private static final String DATASET_CACHE_DIR =
            NURSA_CACHE_DIR + "/datasets";
    
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
                                            .map(NursaController::asDataSet)
                                            .collect(Collectors.toList());
        searchResult.setDatasets(datasets);
        logger.info("Search on \"" + term + "\" matched " + numFound + " datasets.");
        
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
            List<Experiment> experiments = dataset.getExperiments();
            if (experiments != null && !experiments.isEmpty()) {
                return dataset;
            }
        } else {
            // TODO - seed the names and descriptions from a new
            // Nursa REST API endpoint.
            throw new NursaException("Dataset not found: " + doi);
        }

        // The dataset is not cached: get the data points in a
        // separate REST call.
        List<Experiment> experiments = getExperiments(doi);
        dataset.setExperiments(experiments);
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

    private List<Experiment> getExperiments(String doi) throws URISyntaxException, IOException {
        ArrayList<Experiment> experiments = new ArrayList<Experiment>();
        // Make the DataPoint and add to the appropriate the experiment.
        Consumer<Map<String, Object>> transform = new Consumer<Map<String, Object>>() {

            @Override
            public void accept(Map<String, Object> row) {
                    // The one-based experiment number.
                String expNbr = (String) row.get("experimentNumber");
                int expNdx = Integer.parseUnsignedInt(expNbr) - 1;
                if (experiments.size() <= expNdx) {
                    experiments.ensureCapacity(expNdx + 1);
                    for (int i=experiments.size(); i <= expNdx; i++) {
                        experiments.add(null);
                    }
                }
                Experiment experiment = experiments.get(expNdx);
                if (experiment == null) {
                    experiment = new Experiment();
                    experiment.setName((String) row.get("experimentName"));
                    experiment.setDataPoints(new ArrayList<DataPoint>());
                    experiments.set(expNdx, experiment);
                }
                DataPoint dataPoint = new DataPoint();
                dataPoint.setSymbol((String) row.get("symbol"));
                // Zero parses as an Integer.
                Number pValue = (Number) row.get("pValue");
                dataPoint.setPvalue(pValue.doubleValue());
                Number fc = (Number) row.get("foldChange");
                dataPoint.setFoldChange(fc.doubleValue());
                experiment.getDataPoints().add(dataPoint);
            }

        };
        // Iterate over each record returned by the REST call.
        NursaRestClient.getDataPoints(doi).forEachRemaining(transform);
        // Return the populated experiment list.
        return experiments;
    }

}
