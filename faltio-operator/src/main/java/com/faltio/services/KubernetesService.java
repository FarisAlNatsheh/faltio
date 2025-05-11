package com.faltio.services;

import com.faltio.crd.FaltioDeployment;
import com.faltio.crd.models.FaltioDeploymentSpec;
import com.faltio.crd.InferenceService;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;


@AllArgsConstructor
public class KubernetesService {

    private final KubernetesClient client;
    private static final Logger log = LoggerFactory.getLogger(KubernetesService.class);
    private String decode(String base64Value) {
        return new String(java.util.Base64.getDecoder().decode(base64Value));
    }
    public Job getInitJob(String namespace, String jobName) {
        return client.batch()
                .v1()
                .jobs()
                .inNamespace(namespace)
                .withName(jobName)
                .get();
    }


    public Job createInitJob(String namespace, String jobName, FaltioDeployment resource) {
        FaltioDeploymentSpec spec = resource.getSpec();
        String serviceAccount = spec.getServiceAccount() != null ? spec.getServiceAccount() : "faltio-sa";

        Secret secret = client.secrets()
                .inNamespace(namespace)
                .withName(spec.getMlflow().getSecretName())
                .get();

        if (secret == null || secret.getData() == null) {
            throw new IllegalStateException("Secret " + spec.getMlflow().getSecretName() + " not found or invalid");
        }

        String username = decode(secret.getData().get("username"));
        String password = decode(secret.getData().get("password"));


        Job job = new JobBuilder()
                .withNewMetadata()
                .withName(jobName)
                .endMetadata()
                .withNewSpec()
                .withTtlSecondsAfterFinished(300)
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels("job-name", jobName)
                .addToLabels("app.kubernetes.io/managed-by", "faltio")
                .addToLabels("faltio-deployment", spec.getDeploymentName())
                .addToLabels("app.kubernetes.io/part-of", jobName)
                .addToLabels("app.kubernetes.io/component","init-job")
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .withServiceAccountName(serviceAccount)
                .addNewContainer()
                .withName("init-container")
                .withImage("docker.io/farisalnatsheh/faltio-deployment-init")
                .withImagePullPolicy("Always")
                .withArgs(
                        "--spring.application.name=kube-ml",
                        "--mlflow.host=" + spec.getMlflow().getHost(),
                        "--mlflow.username=" + username,
                        "--mlflow.password=" + password,
                        "--mlflow.ignore-tls=" + spec.getMlflow().getIgnoreTls(),
                        "--kubernetes.namespace=" + namespace,
                        "--faltio.model-name=" + spec.getModelName(),
                        "--faltio.model-version=" + spec.getModelVersion(),
                        "--faltio.model-file-path=" + spec.getModelFilePath(),
                        "--faltio.deployment-name=" + spec.getDeploymentName()
                )
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        client.batch().v1().jobs().inNamespace(namespace).create(job);
        log.info("Init job created");
        return job;
    }

    public void deletePVC(String namespace, String name) {
        client.persistentVolumeClaims()
                .inNamespace(namespace)
                .withName("model-"+name)
                .delete();
        log.info("PVC {} deleted", name);
    }

    public void deletePV(String name) {
        client.persistentVolumes()
                .withName("model-"+name+"-pv")
                .delete();
        log.info("PV {} deleted", name);
    }

    public void deleteInferenceService(String namespace, String name) {
        client.resources(InferenceService.class)
                .inNamespace(namespace)
                .withName(name)
                .withPropagationPolicy(DeletionPropagation.FOREGROUND)
                .delete();
        log.info("InferenceService {} deleted", name);
    }

    public PersistentVolumeClaim getPVC(String namespace, String name) {
        return client.persistentVolumeClaims().inNamespace(namespace).withName(name).get();
    }

    public PersistentVolume getPV(String name) {
        return client.persistentVolumes().withName(name).get();
    }
    public boolean inferenceServiceExists(String namespace, String name) {
        ResourceDefinitionContext context = new ResourceDefinitionContext.Builder()
                .withGroup("serving.kserve.io")
                .withVersion("v1beta1")
                .withPlural("inferenceservices")
                .withKind("InferenceService")
                .withNamespaced(true)
                .build();

        try {
            return client.genericKubernetesResources(context)
                    .inNamespace(namespace)
                    .withName(name)
                    .get() != null;
        } catch (Exception e) {
            log.warn("Error checking InferenceService existence in namespace '{}', name '{}': {}", namespace, name, e.getMessage());
            return false;
        }
    }

    public void deleteFileCopyPod(String namespace, String deploymentName) {
        String podName = "file-copy-pod-" + deploymentName;
        client.pods()
                .inNamespace(namespace)
                .withName(podName)
                .delete();
        log.info("File copy pod {} deleted", podName);
    }
    public Pod getFileCopyPod(String namespace, String deploymentName) {
        String podName = "file-copy-pod-" + deploymentName;
        return client.pods()
                .inNamespace(namespace)
                .withName(podName)
                .get();
    }


    public Integer getJobContainerExitCode(String namespace, String jobName, String containerName) {
        List<Pod> pods = client.pods().inNamespace(namespace).withLabel("job-name", jobName).list().getItems();

        for (Pod pod : pods) {
            if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
                return pod.getStatus().getContainerStatuses().stream()
                        .filter(cs -> cs.getName().equals(containerName))
                        .map(cs -> cs.getState().getTerminated() != null ? cs.getState().getTerminated().getExitCode() : null)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            }
        }

        return null;
    }
    public void deleteInitJob(String namespace, String jobName) {
        client.batch()
                .v1()
                .jobs()
                .inNamespace(namespace)
                .withName(jobName)
                .withPropagationPolicy(DeletionPropagation.FOREGROUND)
                .delete();
        log.info("Job {} deleted", jobName);
    }
}
