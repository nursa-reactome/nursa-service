package org.reactome.nursa.dao;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

public class JsonHelper {

    /**
     * Parse JSON input into name-value map rows.
     * 
     * @param content the JSON input
     * @return the stream of {attribute: object} rows
     * @throws IOException
     * @throws JsonProcessingException
     */
    public static Stream<Map<String, Object>> parse(Reader content)
            throws IOException, JsonProcessingException {
        // Parse the JSON into an iterator over name-value map records.
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() { };
        ObjectReader reader = mapper.readerFor(typeRef);

        // Convert the iterator to a stream
        // (cf. https://stackoverflow.com/questions/24511052/how-to-convert-an-iterator-to-a-stream).
        MappingIterator<Map<String, Object>> iterator = reader.readValues(content);
        Iterable<Map<String, Object>> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

}
