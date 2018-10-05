package org.reactome.nursa.controller;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
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

    private static final String EXPERIMENTS_DIR_NAME = "experiments";

    private static final Logger logger =
            Logger.getLogger(NursaController.class);
 
    private static final String NURSA_CACHE_DIR =
            "/usr/local/reactomes/Reactome/production/nursa";

    private static final String DATASET_CACHE_DIR =
            NURSA_CACHE_DIR + "/datasets";
    
    @Autowired
    private NursaSolrClient solrClient;
    
    @Autowired
    private NursaRestClient nursaClient;
 
    /**
     * Searches for the given term in the dataset doi, name and description.
     * 
     * @param term the search term
     * @return the JSON {doi, name} matches
     * @throws URISyntaxException if the Nursa REST API uri is malformed
     * @throws IOException if the Nursa REST server could not be accessed
     */
    @RequestMapping("/search")
    public DataSetSearchResult search(
            @RequestParam(value="term") String term,
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
        
        Path path = Paths.get(DATASET_CACHE_DIR, registrant, objId, dsFile);
        File file = path.toFile();
        DataSet dataset;
        if (file.exists()) {
            FileReader reader = new FileReader(file);
            ObjectMapper mapper = new ObjectMapper();
            try {
                dataset = mapper.readValue(reader, DataSet.class);
            } catch (Exception e) {
                String message =
                        "Could not read the cached Nursa dataset file: " + file;
                throw new NursaException(message, e);
            } finally {
                reader.close();
            }
            return dataset;
        } else {
            // TODO - seed the names and descriptions from a new
            // Nursa REST API endpoint.
            throw new NursaException("Dataset not found: " + doi);
        }
    }

    /**
     * Fetches the dataset content for the given dataset identifier.
     * 
     * @param doi the dataset identifier
     * @return the JSON {doi, datapoints} dataset object
     * @throws URISyntaxException if the Nursa REST API uri is malformed
     * @throws IOException if the Nursa REST server could not be accessed
     */
    @RequestMapping("/datapoints")
    public List<DataPoint> getDataPoints(
            @RequestParam(value="doi") String doi,
            @RequestParam(value="experimentNumber") int expNbr)
            throws URISyntaxException, IOException {
        // Check the local file cache.
        String[] relPath = doi.split("/");
        String registrant = relPath[0];
        String objId = relPath[1];
        String expFileName = expNbr + ".json";
        
        Path expsPath = Paths.get(DATASET_CACHE_DIR, registrant, objId,
                EXPERIMENTS_DIR_NAME);
        Path expPath = Paths.get(expsPath.toString(), expFileName);
        File file = expPath.toFile();
        if (file.exists()) {
            Experiment experiment;
            FileReader reader = new FileReader(file);
            ObjectMapper mapper = new ObjectMapper();
            try {
                experiment = mapper.readValue(reader, Experiment.class);
            } catch (Exception e) {
                String message =
                        "Could not read cached Nursa experiment file: " + file;
                throw new NursaException(message, e);
            } finally {
                reader.close();
            }
            return experiment.getDataPoints();
        } else {
            // The data points are not cached: fetch and cache the data points.
            List<Experiment> experiments = getExperiments(doi, expsPath);
            Experiment experiment = experiments.get(expNbr - 1);
            return experiment.getDataPoints();
        }
    }

    /**
     * Fetches the given DOI data points, groups them by experiment,
     * and caches the experiments.
     * 
     * @param doi the experiment DOI
     * @param path the DOI experiments cache directory path
     * @return the fetched experiments
     * @throws URISyntaxException
     * @throws IOException
     */
    private List<Experiment> getExperiments(String doi, Path path)
            throws URISyntaxException, IOException {
        List<Experiment> experiments = fetchExperiments(doi);
        // Make the parent directory, if necessary.
        File dir = path.toFile();
        if (!dir.exists()) {
            try {
                dir.mkdirs();
            }
            catch (SecurityException e) {
                String message = "Could not create Nursa experiment cache directory " + dir;
                throw new NursaException(message, e);
            }
        }
        // Cache the experiments.
        for (int i = 0; i < experiments.size(); i++) {
            Experiment experiment = experiments.get(i);
            ObjectMapper mapper = new ObjectMapper();
            String expFileName = Integer.toString(i + 1);
            File file = new File(dir, expFileName);
            try {
                FileWriter writer = new FileWriter(file);
                mapper.writeValue(writer, experiment);
                writer.close();
            } catch (Exception e) {
                String message = "Nursa experiment could not be cached in " + file;
                throw new NursaException(message, e);
            }
        }
        
        return experiments;
    }

    private List<Experiment> fetchExperiments(String doi)
            throws URISyntaxException, IOException {
        ArrayList<Experiment> experiments = new ArrayList<Experiment>();
        // Add each data point to the appropriate experiment.
        // The experiments are made on demand.
        Consumer<Map<String, Object>> transform =
                new Consumer<Map<String, Object>>() {

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
        nursaClient.getDataPoints(doi).forEachRemaining(transform);

        // Return the populated experiment list.
        return experiments;
    }

}
