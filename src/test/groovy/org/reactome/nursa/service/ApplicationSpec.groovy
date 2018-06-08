package org.reactome.nursa.service

import org.junit.experimental.categories.Category
import spock.lang.Specification
import groovyx.net.http.RESTClient

/**
 * @author Fred Loney <loneyf@ohsu.edu>
 */
//
// Note: See the pom.xml file for why this is falsely categorized
// as a unit test for now.
//@Category(IntegrationTest.class)
@Category(UnitTest.class)
class ApplicationSpec extends Specification {
    def client = new RESTClient('http://localhost:8484/')

    def "fetches the unexpanded cached dataset"() {
        def (domain, dataset) = doi.split('/')
        def fixture = new File("src/test/fixtures/${dataset}.json")
        def cached = new File("/usr/local/reactomes/Reactome/production/" +
                              "nursa/datasets/${domain}/${dataset}.json")
        // Copy the fixture.
        cached.newWriter().withWriter { f -> f << fixture.text }
        expect:
            def resp = client.get(path: 'dataset', query: [doi: doi])
            assert resp.status == 200 : "Uncached dataset not fetched"
            assert resp.data.experiments.size() == count
        where:
              doi                 | count
            "10.1621/gTqItVnDEP"  |  1
    }

    def "fetches the expanded cached dataset"() {
        expect:
             // Fetch cached.
            def resp = client.get(path: 'dataset', query: [doi: doi])
            assert resp.status == 200 : "Cached dataset not fetched"
            assert resp.data.experiments.size() == count
        where:
              doi                 | count
            "10.1621/gTqItVnDEP"  |  1
    }

    def "searches datasets based on a term"() {
        expect:
            def resp = client.get(path: 'search', query: [term: term])
            assert resp.status == 200
            assert resp.data.datasets.size() == count
        where:
              term      | count
             "fulv"     |    7
    }
}
