package org.reactome.nursa.service

import spock.lang.Specification


/**
 * @author Fred Loney <loneyf@ohsu.edu>
 */
class BinomialAnalysisRestClientSpec extends Specification {
    def "performs analysis"() {
        expect:
        BinomialAnalysisRestClient.analyse(symbols).get("pathways").size() == count

        where:
          symbols                          | count
        ["COL4A5", "IFI27"]                |   26
    }
}
