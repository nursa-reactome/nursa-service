package org.reactome.nursa.service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.collections4.iterators.TransformIterator;
import org.reactome.nursa.model.DataPoint;
import org.reactome.nursa.model.DataSet;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Fred Loney <loneyf@ohsu.edu>
 */
@RestController
public class DataSetController {

    static String MOCK_NAME =
            "Analysis of the mutant picornavirus RNA-dependent RNA polymerase" +
            " with disrupted primary and secondary RNA structure (RdRPΔrna)-dependent" +
            " transcriptome in human THP-1 monocytic leukemia cells";

    static String MOCK_DESCRIPTION =
            "Human THP-1 monocytic leukemia cells were transduced with a" +
            " lentiviral vector containing a mutant picornavirus RNA-dependent" +
            " RNA polymerase with disrupted primary and secondary RNA structure" +
            " (RdRPΔrna) or empty vector. RdRPΔrna contains pervasive, coding-neutral" +
            " point mutations in the RdRP cDNA to maximally disrupt primary and" +
            " secondary RNA structure.";
    
    @RequestMapping("/dataset")
    public DataSet getDataset(@RequestParam(value="doi") String doi)
            throws URISyntaxException, IOException {
         DataSet dataset = new DataSet();
        dataset.setDoi(doi);
        // TODO - get name and description from Nursa REST API.
        dataset.setName(MOCK_NAME);
        dataset.setDescription(MOCK_DESCRIPTION);
        // Get the data points in a separate REST call. 
        List<DataPoint> dataPoints = getDataPoints(doi);
        dataset.setDataPoints(dataPoints);
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
