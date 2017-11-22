package org.reactome.nursa.service

import spock.lang.Specification
import groovyx.net.http.RESTClient

/**
 * @author Fred Loney <loneyf@ohsu.edu>
 */
class ApplicationSpec extends Specification {
    def client = new RESTClient('http://localhost:8484/')
    def "fetches the dataset"() {
        expect:
            def resp = client.get(path: 'dataset', query: [doi: doi])
            assert resp.status == 200
            assert resp.data.dataPoints.size() == count
        where:
              doi                 | count
            "10.1621/gTqItVnDEP"  |  255
    }
}
