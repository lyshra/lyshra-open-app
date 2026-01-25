package com.lyshra.open.app.designer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Web configuration for the workflow designer API.
 */
@Configuration
@EnableWebFlux
public class WebConfig implements WebFluxConfigurer {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        ObjectMapper mapper = objectMapper();
        configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper));
        configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper));
        configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024);
    }
}
