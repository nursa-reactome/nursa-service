package org.reactome.nursa.dao;

import java.io.IOException;
import java.util.Optional;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.reactome.nursa.controller.PreemptiveAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


/**
 * The Solr interaction facade.
 * 
 * @author Fred Loney <loneyf@ohsu.edu>
 */
@Component
public class NursaSolrClient {

    private final HttpSolrClient solrClient;

    @Autowired
    public NursaSolrClient(@Value("${solr.host}") String host,
                           @Value("${solr.user}") String user,
                           @Value("${solr.password}") String password) {
        HttpSolrClient.Builder builder = new HttpSolrClient.Builder(host);
        if (user == null || user.isEmpty() || password == null || password.isEmpty()) {
            solrClient = builder.build();
        } else {
            PreemptiveAuthInterceptor interceptor = new PreemptiveAuthInterceptor();
            HttpClientBuilder httpBuilder = HttpClientBuilder.create().addInterceptorFirst(interceptor);
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user, password);
            credentialsProvider.setCredentials(AuthScope.ANY, credentials);
            HttpClient client = httpBuilder.setDefaultCredentialsProvider(credentialsProvider).build();
            solrClient = builder.withHttpClient(client).build();
        }
    }
    
    /**
     * Issues a Solr query.
     * 
     * @param query the query
     * @return the response
     * @throws NursaSolrException if there is a search error
     */
    public QueryResponse search(SolrQuery query) {
        try {
            return solrClient.query(query);
        } catch (SolrServerException | IOException e) {
            throw new NursaSolrException(query, e);
        }
    }

    /**
     * Convenience method that builds a query on the given term.
     * 
     * @param term the search term
     * @param start the index of the first row to fetch (default 0)
     * @param size the number of rows to fetch (default all)
     * @return the {@link #search(SolrQuery)} result
     */
    public QueryResponse search(String term, Optional<Integer> start, Optional<Integer> size) {
        // The query is on either the catch-all name/description text type
        // or the special doi field.
        String q = "doi:" + term + " OR " + term;
        SolrQuery query = new SolrQuery(q);
        if (start.isPresent()) {
            query.setStart(start.get());
        }
        if (size.isPresent()) {
            query.setRows(size.get());
        }
        return search(query);
    }

}
