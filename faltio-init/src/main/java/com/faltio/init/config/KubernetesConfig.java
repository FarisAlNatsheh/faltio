package com.faltio.init.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;

@Configuration
public class KubernetesConfig {

    @Bean
    public ApiClient apiClient() throws IOException {
        ApiClient client = Config.defaultClient();
        io.kubernetes.client.openapi.Configuration.getDefaultApiClient().setReadTimeout(0);
        io.kubernetes.client.openapi.Configuration.getDefaultApiClient().setWriteTimeout(0);

        client.setHttpClient(
                client.getHttpClient().newBuilder()
                        .readTimeout(Duration.ofMinutes(10))
                        .writeTimeout(Duration.ofMinutes(10))
                        .connectTimeout(Duration.ofMinutes(5))
                        .build()
        );
        io.kubernetes.client.openapi.Configuration.setDefaultApiClient(client);
        return client;
    }
}
