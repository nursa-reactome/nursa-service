package org.reactome.nursa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
@Configuration
@PropertySource("classpath:application.properties")
public class AppConfig {

    // This bean is necessary to resolve property place-holders per
    // https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/PropertySource.html
    //  and
    // https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/Bean.html
    // Creating this bean effects place-holder substitution in any scanned component.
    // The bean must be static to ensure that it is created prior to scanned components.
    // Deleting this bean disables property value substitution resulting in an error.
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
       return new PropertySourcesPlaceholderConfigurer();
    }
    
}