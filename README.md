Reactome Nursa Service
======================
The Reactome Nursa service provides the following REST end points:

* _search_ - Searches for a term in the dataset DOI, name and description

* _dataset_ - Fetches dataset content

Installation
------------
1. Clone this Git repository.

2. Install [Maven](http://maven.apache.org/install.html).

3. Install and configure Solr as described in the Reactome
   [search-indexer](https://github.com/reactome/search-indexer) repository.

4. Obtain a Nursa API key from nursa.org.
 
5. Define`$HOME/.m2/settings.xml` Maven profiles that set the access
   authorization properties
   <a name="solr-profile-note-link" href="#user-content-solr-profile-note"><sup>1</sup></a>,
   e.g.:

        <profile>
            <id>solr</id>
            <properties>
                <!-- Solr authorization -->
                <solr.user>solr</solr.user>
                <solr.password>my-solr-password</solr.password>
            </properties>
        </profile>
        <profile>
            <id>nursa</id>
            <properties>
                <!-- The Nursa Solr core host name. -->
                <solr.host>http://localhost:8983/solr/nursa</solr.host>
                <!-- The Nursa REST API access key. -->
                <nursa.api.key>my-api-key</nursa.api.key>
            </properties>
        </profile>

5. Build the `.war` file:

        mvn clean package -U -P solr,nursa

6. Copy the `.war` file to tomcat:

        cp target/Nursa.war $TOMCAT_HOME/webapps
   
   where `$TOMCAT_HOME` is the tomcat deployment location.

7. The REST service can then be called by the
   [Nursa Reactome Portal](https://github.com/nursa-reactome/browser).

8. Alternatively, the Nursa Reactome REST service can be started locally
   in an embedded server with the Maven `tomcat7:run` goal: 

        mvn tomcat7:run

Notes
-----
<a name="solr-profile-note"><sup>1</sup></a>
<small>
   The `solr` profile can be pulled from the Reactome profile
   defined for [data-content](https://github.com/reactome/data-content)
   and reused to build that project as well.
   [&hookleftarrow;](#user-content-solr-profile-note-link)
</small>
