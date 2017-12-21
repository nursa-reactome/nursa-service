package org.reactome.nursa.service;

import org.apache.solr.client.solrj.SolrQuery;

public class NursaSolrException extends RuntimeException {
    private static final long serialVersionUID = 2873494091371445794L;

    public NursaSolrException(SolrQuery query, Throwable cause) {
        super("Solr exception occurred with query: " + query, cause);
    }

    public NursaSolrException(String message) {
        super(message);
    }
}
