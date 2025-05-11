package com.faltio.init.services;


import lombok.AllArgsConstructor;
import org.mlflow.api.proto.ModelRegistry.ModelVersion;
import org.mlflow.api.proto.ModelRegistry.RegisteredModel;
import org.mlflow.api.proto.Service.RunData;
import org.mlflow.api.proto.Service.Run;
import org.mlflow.api.proto.Service.Metric;

import org.mlflow.tracking.MlflowClient;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class MlflowService {

    private final MlflowClient client;

    public File getArtifact(String modelName, String filePath, String modelVersion){

        RegisteredModel model = client.getRegisteredModel(modelName);
        String latestVersion = model.getLatestVersions(0).getVersion();
        if(Integer.parseInt(latestVersion) < Integer.parseInt(modelVersion))
            return null;
        ModelVersion latestModel = client.getModelVersion(modelName, modelVersion);
        String runId = latestModel.getRunId();

        return client.downloadArtifacts(runId, filePath);

    }


    public Map<String, Double> getMetrics(String modelName, String modelVersion){
        RegisteredModel model = client.getRegisteredModel(modelName);
        String latestVersion = model.getLatestVersions(0).getVersion();
        if(Integer.parseInt(latestVersion) < Integer.parseInt(modelVersion))
            return null;
        ModelVersion latestModel = client.getModelVersion(modelName, modelVersion);

        String runId = latestModel.getRunId();
        Run run = client.getRun(runId);
        RunData data = run.getData();

        return data.getMetricsList().stream()
                .collect(Collectors.toMap(
                        Metric::getKey,
                        Metric::getValue,
                        (existing, replacement) -> replacement
                ));

    }

}
