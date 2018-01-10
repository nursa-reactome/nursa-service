package org.reactome.nursa.service

import org.junit.experimental.categories.Category
import spock.lang.Specification

/**
 * @author Fred Loney <loneyf@ohsu.edu>
 */
@Category(UnitTest.class)
class BinomialAnalysisRestClientSpec extends Specification {
    def "performs analysis"() {
        expect:
        BinomialAnalysisRestClient.analyse(symbols).get("pathways").size() == count

        where:
          symbols                          | count
        ["COL4A5", "IFI27"]                |   26
    }
}
