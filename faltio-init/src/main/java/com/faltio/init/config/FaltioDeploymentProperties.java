package com.faltio.init.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Data
@Validated
@ConfigurationProperties(prefix = "faltio")
public class FaltioDeploymentProperties {
    private String modelName;
    private String modelVersion;
    private String modelFilePath;
    private String deploymentName;
}
