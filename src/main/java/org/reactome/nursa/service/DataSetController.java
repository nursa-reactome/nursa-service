package org.reactome.nursa.service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.collections4.iterators.TransformIterator;
import org.reactome.nursa.model.DataPoint;
import org.reactome.nursa.model.DataSet;
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
    
    @RequestMapping("/dataset")
    public DataSet getDataset(@RequestParam(value="doi") String doi)
            throws URISyntaxException, IOException {
        // Check the local file cache.
        String[] relPath = doi.split("/");
        String registrant = relPath[0];
        String objId = relPath[1];
        String dsFile = objId + ".json.gz";
        
        Path path = Paths.get(DATASET_CACHE_DIR, registrant, dsFile);
        DataSet dataset;
        if (Files.exists(path)) {
            try {
                InputStream fis = Files.newInputStream(path);
                GZIPInputStream gis = new GZIPInputStream(fis);
                ObjectMapper mapper = new ObjectMapper();
                dataset = mapper.readValue(gis, DataSet.class);
                gis.close();
                fis.close();
            } catch (Exception e) {
                String message = "Could not read cached Nursa dataset file " + path;
                throw new NursaException(message, e);
            }
            if (!dataset.getDataPoints().isEmpty()) {
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
            GZIPOutputStream gos = new GZIPOutputStream(fos);
            mapper.writeValue(gos, dataset);
            gos.close();
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
                dataPoint.setPvalue((double) row.get("pValue"));
                dataPoint.setFoldChange((double) row.get("foldChange"));
                return dataPoint;
            }
        };
        Iterator<DataPoint> dpIter = new TransformIterator<Map<String, Object>, DataPoint>(rowIter, xfm);
        List<DataPoint> dataPoints = IteratorUtils.toList(dpIter);
        return dataPoints;
    }
}
