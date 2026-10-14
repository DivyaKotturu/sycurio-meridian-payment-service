package com.example.payments.config;

import org.springframework.context.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class HttpConfig {
    @Bean
    RestClient restClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2000);
        f.setReadTimeout(5000);
        return RestClient.builder().requestFactory(f).build();
    }
}
