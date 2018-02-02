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

4. Define the following Maven profiles in
   `.m2/settings.xml`<a name="solr-profile-note-link" href="#user-content-solr-profile-note"><sup>1</sup></a>:

        <profile>
            <id>solr</id>
            <properties>
                <!-- Solr authorization -->
                <solr.user>solr</solr.user>
                <solr.password>solr</solr.password>
            </properties>
        </profile>
        <profile>
            <id>nursa</id>
            <properties>
                <!-- Solr host -->
                <solr.host>http://localhost:8983/solr/reactome</solr.host>
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
   in an embedded server with the Maven `spring-boot:run` goal: 

        mvn spring-boot:run

Notes
-----
<a name="solr-profile-note"><sup>1</sup></a>
<small>
   The `solr` profile can be pulled from the Reactome profile
   defined for [data-content](https://github.com/reactome/data-content)
   and reused to build that project as well.
   [&hookleftarrow;](#user-content-solr-profile-note-link)
</small>
