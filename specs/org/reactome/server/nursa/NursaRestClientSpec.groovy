package org.reactome.server.nursa

import spock.lang.Specification


/**
 * @author Fred Loney <loneyf@ohsu.edu>
 */
class NursaRestClientSpec extends Specification {
    def "fetches data points"() {
        expect:
        NursaRestClient.getDataPoints(doi).size() == count

        where:
          doi                 | count
        "10.1621/v6muZTvtU6"  |    4
        "10.1621/Y5uY9kSu0E"  |   24
    }
}
