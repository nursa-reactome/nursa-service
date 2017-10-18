package org.reactome.server.nursa;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.collections4.iterators.TransformIterator;
import org.reactome.web.pwp.nursa.model.DataPoint;
import org.reactome.web.pwp.nursa.model.DataSet;
import org.reactome.web.pwp.nursa.model.DataSetPathway;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
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
    public DataSet dataset(@RequestParam(value="doi") String doi)
            throws URISyntaxException, IOException {
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
        DataSet dataset = new DataSet();
        // TODO - get name and description from Nursa REST API.
        dataset.setDoi(doi);
        dataset.setName(MOCK_NAME);
        dataset.setDescription(MOCK_DESCRIPTION);
        List<DataPoint> dataPoints = IteratorUtils.toList(dpIter);
        dataset.setDataPoints(dataPoints);
        List<String> symbols = dataPoints.stream().map(DataPoint::getSymbol).collect(Collectors.toList());
        List<DataSetPathway> pathways = this.analyse(symbols);
        dataset.setPathways(pathways);
        return dataset;
    }
    
    @RequestMapping(value = "/analyse", method = RequestMethod.POST)
    public @ResponseBody List<DataSetPathway> analyse(@RequestBody List<String> symbols)
            throws URISyntaxException, IOException {
        Map<String, Object> analysisResult = AnalysisRestClient.analyse(symbols);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pathwaysResult = (List<Map<String, Object>>) analysisResult.get("pathways");
        Transformer<Map<String, Object>, DataSetPathway> xfm =
                new Transformer<Map<String, Object>, DataSetPathway>() {
            @Override
            public DataSetPathway transform(Map<String, Object> row) {
                DataSetPathway pathway = new DataSetPathway();
                pathway.setName((String)row.get("name"));
                @SuppressWarnings("unchecked")
                Map<String, Object> entities = (Map<String, Object>) row.get("entities");
                pathway.setPvalue((double) entities.get("pValue"));
                pathway.setFdr((double) entities.get("fdr"));
                // TODO - where do I get the regulation type?
                pathway.setRegulationType(DataSetPathway.RegulationType.UP);
                return pathway;
            }
        };
        Iterator<DataSetPathway> pwIter =
                new TransformIterator<Map<String, Object>, DataSetPathway>(pathwaysResult.iterator(), xfm);
        return IteratorUtils.toList(pwIter);
    }
}