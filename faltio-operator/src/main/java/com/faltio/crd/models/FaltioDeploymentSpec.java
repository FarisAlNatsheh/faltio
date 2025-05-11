package com.faltio.crd.models;

import lombok.Data;

@Data
public class FaltioDeploymentSpec {
    private String modelName;
    private String modelVersion;
    private String modelFilePath;
    private String deploymentName;
    private String runtime;
    private String storageSize;
    private String serviceAccount;
    private MlflowConfig mlflow;
}

