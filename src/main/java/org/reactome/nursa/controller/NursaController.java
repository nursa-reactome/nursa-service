package org.reactome.nursa.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.reactome.nursa.model.DataPoint;
import org.reactome.nursa.model.DataSet;
import org.reactome.nursa.model.DataSetSearchResult;
import org.reactome.nursa.model.Experiment;
import org.reactome.nursa.model.DisplayableDataPoint;
import org.reactome.nursa.controller.NursaException;
import org.reactome.nursa.dao.NursaRestClient;
import org.reactome.nursa.dao.NursaSolrClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Fred Loney <loneyf@ohsu.edu>
 */
@RestController
public class NursaController {

    private static final String CACHE_FILE_READ_ERROR_MSG = "Could not read Nursa cache file: ";

    private static final String INTERNAL_EXP_ID_ERROR_MSG = "Data point internal experiment id not found in ";

    private static final String EXPERIMENT_CACHE_ERROR_MSG = "Nursa experiment could not be cached in ";

    private static final String CACHE_DIRECTORY_ERROR_MSG = "Could not create Nursa experiment cache directory ";

    private static final String CACHE_FILE_NOT_FOUND_MSG = "Nursa cache file not found: ";

    private static final String DATASETS_DIR = "datasets";

    private static final String EXPERIMENTS_DIR_NAME = "experiments";
 
    @Value("${nursa.cache.dir}")
    private String NURSA_CACHE_DIR;

    private static final Logger logger = Logger.getLogger(NursaController.class);
    
    private static final String[] SUPPORTED_SPECIES = {
            "Human"
    };
    
    @Autowired
    private NursaSolrClient solrClient;
    
    @Autowired
    private NursaRestClient nursaClient;

    @Autowired
    private Environment env;
    
    // Configuration
    private String gmtResource;
    
    // Cached gene symbols
    private Set<String> symbols;
 
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
     * Fetches the dataset for the given dataset.
     * 
     * @param doi the dataset DOI identifier
     * @param refresh flag indicating whether to refetch the
     *      dataset from SPP
     * @return the dataset object
     * @throws IOException if the cached dataset file could not be read
     */
    @RequestMapping("/dataset")
    public DataSet getDataset(
            @RequestParam(value="doi") String doi,
            @RequestParam(value="refresh", defaultValue = "false") boolean refresh) {
        DataSet dataset;
        ObjectMapper mapper = new ObjectMapper();
        File file = getDatasetFile(doi);
        // Check the local file cache.
        if (!refresh && file.exists()) {
            FileReader reader;
            try {
                reader = new FileReader(file);
            } catch (FileNotFoundException e) {
                // Annoying impossible exception.
                String message = "Cached dataset file not found: " + file;
                throw new NursaException(message, e);
            }
            try {
                dataset = mapper.readValue(reader, DataSet.class);
            } catch (Exception e) {
                String message =
                        "Could not read the cached dataset file: " + file;
                throw new NursaException(message, e);
            } finally {
                try {
                    reader.close();
                } catch (IOException e) {
                    String message =
                            "Could not close the cached dataset file: " + file;
                    throw new NursaException(message, e);
                }
            }
        } else {
            dataset = fetchDataSet(doi);
        }
        
        return dataset;
    }

    /**
     * Fetches the datasets for the given date cut-off.
     * 
     * @param addedSince the date cut-off
     * @return the dataset objects
     */
    @RequestMapping("/datasets")
    public Collection<DataSet> getDatasets(@RequestParam(value="addedsince") Date addedSince) {
        return fetchDataSets(addedSince);
    }

    /**
     * Rebuilds the dataset cache.
     * 
     * <Em>Caution</em>: this utility clobbers the existing cache.
     */
    @RequestMapping("/refresh")
    public void refresh() {
        File datasetsDir = new File(NURSA_CACHE_DIR, DATASETS_DIR);
        File authorityDir = Stream.of(datasetsDir.listFiles())
            .filter(file -> file.getName().matches("\\d+\\.\\d+"))
            .findFirst().orElse(null);
        if (authorityDir == null) {
            throw new NursaException("Dataset cache missing DOI authority directory");
        }
        String authority = authorityDir.getName();
        Stream.of(authorityDir.listFiles())
            .filter(File::isDirectory)
            .map(File::getName)
            .map(datasetName -> authority + "/" + datasetName)
            .forEach(doi -> {
                try {
                    getDataset(doi, true);
                } catch (Exception e) {
                    System.err.println("Refresh unsuccessful for dataset: " + doi);
                }
            });
    }

    /**
     * Fetches the data points for the given experiment.
     * 
     * @param doi the dataset DOI identifier
     * @param experimentId the experiment identifier
     * @return the {@link DisplayableDataPoint} data points list
     * @throws URISyntaxException if the Nursa REST API uri is malformed
     * @throws IOException if the Nursa REST server could not be accessed
     */
    @RequestMapping("/datapoints")
    public List<DisplayableDataPoint> getDataPoints(
            @RequestParam(value="doi") String doi,
            @RequestParam(value="experimentId") int experimentId)
            throws URISyntaxException, IOException {
        // Check the local file cache.
        String[] relPath = doi.split("/");
        String registrant = relPath[0];
        String objId = relPath[1];
        String expFileName = experimentId + ".json";
        
        Path expPath = Paths.get(NURSA_CACHE_DIR, DATASETS_DIR, registrant, objId,
                EXPERIMENTS_DIR_NAME, expFileName);
        File file = expPath.toFile();
        if (!file.exists()) {
            // Should never occur: the experiment file is populated with
            // data points when the the dataset is cached.
            throw new NursaException(CACHE_FILE_NOT_FOUND_MSG + file);
        }
        
        // Load the data points file.
        FileReader reader = new FileReader(file);
        ObjectMapper mapper = new ObjectMapper();
        List<DataPoint> dataPoints;
        try {
            dataPoints = mapper.readValue(reader, new TypeReference<List<DataPoint>>(){});
        } catch (Exception e) {
            String message = CACHE_FILE_READ_ERROR_MSG + file;
            throw new NursaException(message, e);
        } finally {
            reader.close();
        }
        
        int reactomeCnt = 0;
        List<DisplayableDataPoint> displayable =
                new ArrayList<DisplayableDataPoint>(dataPoints.size());
        for (DataPoint dataPoint: dataPoints) {
            String symbol = dataPoint.getSymbol();
            boolean isReactome = isReactomeSymbol(symbol);
            if (isReactome) {
                reactomeCnt++;
            }
            DisplayableDataPoint wrapped =
                    new DisplayableDataPoint(dataPoint, isReactome);
            displayable.add(wrapped);
        }
        logger.info("Dataset " + doi + " experiment " + experimentId +
                " loaded with " + dataPoints.size() +
                " data points, of which " + reactomeCnt +
                " are in Reactome.");
        
        return displayable;
    }

    private boolean isReactomeSymbol(String symbol) {
        if (symbols == null) {
            String resource = getGmtResource();
            InputStream input = getClass().getClassLoader().getResourceAsStream(resource);
            BufferedReader buffer = new BufferedReader(new InputStreamReader(input));
            symbols = buffer.lines()
                    .flatMap(NursaController::parseGeneSymbols)
                    .collect(Collectors.toSet());
            logger.info("Loaded " + symbols.size() + " gene symbols from " + resource + ".");
        }
        
        return symbols.contains(symbol);
    }
    
    private String getGmtResource() {
        if (gmtResource == null) {
            gmtResource = env.getProperty("gmtResource");
            if (gmtResource == null)
                throw new IllegalStateException("gmtResource property has not been set");
            logger.info("Set gmtResource as: " + gmtResource);
        }
        
        return gmtResource;
    }
    
    private static Stream<String> parseGeneSymbols(String line) {
        // The symbols follow the first two fields.
        return Stream.of(line.split("\\t")).skip(2);
    }

    private void cacheDataSet(DataSet dataset, Map<Integer, List<DataPoint>> dataPoints) {
        String doi = dataset.getDoi();
        File doiDir = getDatasetDirectory(doi);
        String dsFile = doiDir.getName() + ".json";
        File file = new File(doiDir, dsFile);
        ObjectMapper mapper = new ObjectMapper();
        try {
            FileWriter writer = new FileWriter(file);
            try {
                mapper.writeValue(writer, dataset);
            } finally {
                writer.close();
            }
        } catch (Exception e) {
            String message =
                    "Could not write the cached dataset file: " + file;
            throw new NursaException(message, e);
        }
        cacheDataPoints(doiDir, dataPoints);
    }

    private void cacheDataPoints(File doiDir, Map<Integer, List<DataPoint>> expDataPointMap) {
        File expsDir = new File(doiDir, "experiments");
        if (!expsDir.exists()) {
            try {
                expsDir.mkdirs();
            }
            catch (SecurityException e) {
                String message = CACHE_DIRECTORY_ERROR_MSG + expsDir;
                throw new NursaException(message, e);
            }
        }
        // Write the data point files.
        for (Entry<Integer, List<DataPoint>> entry: expDataPointMap.entrySet()) {
            Integer expId = entry.getKey();
            List<DataPoint> dataPoints = entry.getValue();
            File file = new File(expsDir, expId.toString() + ".json");
            ObjectMapper mapper = new ObjectMapper();
            try {
                FileWriter writer = new FileWriter(file);
                mapper.writeValue(writer, dataPoints);
                writer.close();
            } catch (Exception e) {
                String message = EXPERIMENT_CACHE_ERROR_MSG + file;
                throw new NursaException(message, e);
            }
        }
    }

    private File getDatasetFile(String doi) {
        File dir = getDatasetDirectory(doi);
        String dsFile = dir.getName() + ".json";
        return new File(dir, dsFile);
    }

    private File getDatasetDirectory(String doi) {
        String[] relPath = doi.split("/");
        String registrant = relPath[0];
        String objId = relPath[1];
        return Paths.get(NURSA_CACHE_DIR, DATASETS_DIR, registrant, objId).toFile();
    }

    private DataSet fetchDataSet(String doi) {
        // The {internal experiment id: experiment id} works around the
        // following SPP REST API bug:
        // * contrary to the SPP REST API documentation as of 04/2019,
        //   the data point record experimentId value is the experiment
        //   internalExperimentId, not the experimentId.
        Map<String, Integer> expIdMap = new HashMap<String, Integer>();
        DataSet dataset = nursaClient.getDataSet(doi)
                        .map(row -> parseDataSetRow(row, expIdMap))
                        .findFirst()
                        .orElse(null);
        // Cache the dataset.
        if (dataset != null) {
            // Fetch the data points.
            Map<Integer, List<DataPoint>> dataPoints = fetchDataPoints(dataset, expIdMap);
            // Cache the dataset.
            cacheDataSet(dataset, dataPoints);
        }
        
        return dataset;
    }

    private List<DataSet> fetchDataSets(Date addedSince) {
        Map<String, Integer> expIdMap = new HashMap<String, Integer>();
        // Iterate over each record returned by the REST call.
        List<DataSet> datasets = nursaClient.getDataSets(addedSince)
                        .map(row -> parseDataSetRow(row, expIdMap))
                        .filter(NursaController::isSupportedSpecies)
                        .collect(Collectors.toList());
        for (DataSet dataset: datasets) {
            // Fetch the data points.
            Map<Integer, List<DataPoint>> dataPoints = fetchDataPoints(dataset, expIdMap);
            // Cache the dataset.
            cacheDataSet(dataset, dataPoints);
        }
        
        return datasets;
    }

    private Map<Integer, List<DataPoint>> fetchDataPoints(DataSet dataset, Map<String, Integer> expIdMap) {
        Map<Integer, List<DataPoint>> expDataPointsMap =
                new HashMap<Integer, List<DataPoint>>();
        // Partition each data point by experiment id.
        nursaClient.getDataPoints(dataset.getDoi()).forEach(row -> {
            String internalExpId = (String) row.get("experimentId");
            Integer expId = expIdMap.get(internalExpId);
            if (expId == null) {
                String msg = INTERNAL_EXP_ID_ERROR_MSG + "dataset: " +
                        dataset.getDoi() + "; experiment internal id: " + internalExpId;
                throw new NursaException(msg);
            }
            List<DataPoint> expDataPoints = expDataPointsMap.get(expId);
            if (expDataPoints == null) {
                expDataPoints = new ArrayList<DataPoint>();
                expDataPointsMap.put(expId, expDataPoints);
            }
            DataPoint datapoint = parseDataPointRow(row);
            expDataPoints.add(datapoint);
        });
        
        return expDataPointsMap;
    }

    private static boolean isSupportedSpecies(DataSet dataset) {
        return dataset.getExperiments().stream()
                .map(Experiment::getSpecies)
                .allMatch(NursaController::isSupportedSpecies);
    }

    private static boolean isSupportedSpecies(String species) {
        return Stream.of(SUPPORTED_SPECIES)
                .anyMatch(supported ->  supported.equals(species));
    }

    private static DataSet parseDataSetRow(Map<String, Object> row, Map<String, Integer> expIdMap) {
        DataSet dataset = new DataSet();
        String doi = (String) row.get("doi");
        if (doi == null) {
            throw new NursaException("Missing doi field for dataset row");
        }
        dataset.setDoi(doi);
        String name = (String) row.get("name");
        if (name == null) {
            throw new NursaException("Missing name field for dataset row " + doi);
        }
        dataset.setName(name);
        String description = (String) row.get("description");
        // Description is missing from record as of 04/2019.
        if (description != null) {
            dataset.setDescription(description);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> expRows = (List<Map<String, Object>>) row.get("experiments");
        // Parse the experiments JSON into an iterator over name-value map records.
        List<Experiment> experiments;
        experiments = expRows.stream()
            .map(expRow -> parseExperimentRow(expRow, expIdMap))
            .collect(Collectors.toList());
        dataset.setExperiments(experiments);

        return dataset;
    }

    private static Experiment parseExperimentRow(Map<String, Object> row, Map<String, Integer> expIdMap) {
        Experiment experiment = new Experiment();
        Integer id = (Integer) row.get("experimentId");
        String internalId = (String) row.get("internalExperimentId");
        experiment.setId(id);
        String name = (String) row.get("name");
        experiment.setName(name);
        String description = (String) row.get("description");
        experiment.setDescription(description);
        String species = (String) row.get("species");
        experiment.setSpecies(species);
        expIdMap.put(internalId, id);
        
        return experiment;
    }

    private static DataPoint parseDataPointRow(Map<String, Object> row) {
        DataPoint datapoint = new DataPoint();
        String symbol = (String) row.get("symbol");
        datapoint.setSymbol(symbol);
        // Zero parses as an Integer.
        Number pValue = (Number) row.get("pvalue");
        datapoint.setPvalue(pValue.doubleValue());
        Number fc = (Number) row.get("foldChange");
        datapoint.setFoldChange(fc.doubleValue());
        
        return datapoint;
    }

}
