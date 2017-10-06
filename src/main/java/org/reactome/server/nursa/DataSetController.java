package org.reactome.server.nursa;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.collections4.iterators.TransformIterator;
import org.reactome.web.pwp.nursa.model.DataPoint;
import org.reactome.web.pwp.nursa.model.DataSet;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Fred Loney <loneyf@ohsu.edu>
 */
@RestController
public class DataSetController {

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
        Iterator<DataPoint> dpIter = new TransformIterator<Map<String, Object>, DataPoint>(rowIter,xfm);
        DataSet dataset = new DataSet();
        dataset.setDOI(doi);
        // TODO - get name and description from Nursa REST API.
        dataset.setName(doi);
        dataset.setDescription(doi);
        List<DataPoint> dataPoints = IteratorUtils.toList(dpIter);
        dataset.setDataPoints(dataPoints);
        return dataset;
    }
}
