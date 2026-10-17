package com.aquatech.sync;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoConfig {

    @Bean(destroyMethod = "close")
    public DynamoDbClient dynamoDbClient(
            @Value("${sync.dynamodb.endpoint}") String endpoint,
            @Value("${sync.dynamodb.region}") String region,
            @Value("${sync.dynamodb.access-key}") String accessKey,
            @Value("${sync.dynamodb.secret-key}") String secretKey) {

        return DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        accessKey, secretKey)))
                .overrideConfiguration(
                        ClientOverrideConfiguration.builder()
                                .apiCallAttemptTimeout(Duration.ofSeconds(5))
                                .apiCallTimeout(Duration.ofSeconds(20))
                                .build())
                .build();
    }
}