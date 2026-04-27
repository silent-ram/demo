package com.example.datacollectorservice.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfluxDBConfig {

    private static final Logger log = LoggerFactory.getLogger(InfluxDBConfig.class);

    @Value("${influxdb.url}")
    private String url;

    @Value("${influxdb.token}")
    private String token;

    @Value("${influxdb.org}")
    private String org;

    @Value("${influxdb.bucket}")
    private String bucket;

    @Bean
    public InfluxDBClient influxDBClient() {
        log.info("Creating InfluxDBClient with URL: {}, Org: {}, Bucket: {}", url, org, bucket);
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
    }
}