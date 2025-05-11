package com.faltio.init.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "mlflow")
public class MlflowProperties {
    private String host;
    private String username;
    private String password;
    private boolean ignoreTls;
}
