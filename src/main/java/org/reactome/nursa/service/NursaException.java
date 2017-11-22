package org.reactome.nursa.service;

public class NursaException extends RuntimeException {

    private static final long serialVersionUID = -8582807194984302915L;

    public NursaException(String message) {
        super(message);
    }

    public NursaException(String message, Exception cause) {
        super(message, cause);
    }

}
