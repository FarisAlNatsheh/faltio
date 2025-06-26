package com.faltio.init.services;

import com.faltio.init.models.InferenceServicePhase;
import io.kubernetes.client.Copy;
import io.kubernetes.client.Exec;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.*;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

@NoArgsConstructor(force = true)
@Service
@Slf4j
public class KubernetesService {

    private final CoreV1Api api;
    private final String namespace;
    private final MlflowService mlflowService;

    @Autowired
    public KubernetesService(
            @Value("${kubernetes.namespace}") String namespace,
            ApiClient apiClient,
            MlflowService mlflowService
    ) {
        this.namespace = namespace;
        this.api = new CoreV1Api(apiClient);
        this.mlflowService = mlflowService;
    }


    public void createInferenceService(String serviceName, String uuid, String pvcName) throws ApiException {
        CustomObjectsApi api = new CustomObjectsApi();

        Map<String, Object> inferenceService = Map.of(
                "apiVersion", "serving.kserve.io/v1beta1",
                "kind", "InferenceService",
                "metadata", Map.of(
                        "name", serviceName,
                        "labels", Map.of(
                                "app.kubernetes.io/managed-by", "faltio",
                                "app.kubernetes.io/part-of", serviceName,
                                "app.kubernetes.io/component", "inference-service",
                                "app.kubernetes.io/instance", uuid,
                                "faltio.deployment-id", uuid
                        )
                ),
                "spec", Map.of(
                        "predictor", Map.of(
                                "serviceAccountName", "faltio-sa",
                                "model", Map.of(
                                        "protocolVersion", "v2",
                                        "modelFormat", Map.of("name", "onnx"),
                                        "storageUri", "pvc://"+pvcName,
                                        "resources", Map.of(
                                                "limits", Map.of("nvidia.com/gpu", "1", "memory", "4Gi"),
                                                "requests", Map.of("nvidia.com/gpu", "1", "memory", "512Mi")
                                        )
                                )
                        )
                )
        );

        boolean inferenceServiceExists = false;
        try {
            api.getNamespacedCustomObject(
                    "serving.kserve.io",
                    "v1beta1",
                    namespace,
                    "inferenceservices",
                    serviceName
            ).execute();
            inferenceServiceExists = true;
            log.info("InferenceService {} already exists", serviceName);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                api.createNamespacedCustomObject(
                        "serving.kserve.io",
                        "v1beta1",
                        namespace,
                        "inferenceservices",
                        inferenceService
                ).execute();
                log.info("Inference service {} created", serviceName);
            }
        }

    }


    public void copyFileToPVC(String deploymentName, String modelName, String modelFilePath, String modelVersion, String storageClass, boolean defaultStorageClass, boolean createLocalPv, String uuid) throws Exception {
        String pvcName = "model-"+uuid;
        String pvName = pvcName + "-pv";
        createPVandPVC(deploymentName,uuid,pvName, pvcName, storageClass, defaultStorageClass, createLocalPv);

        String podName = "file-copy-pod-"+uuid;
        File file = mlflowService.getArtifact(modelName, modelFilePath, modelVersion);
        byte[] fileBytes;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = fis.read(bytes);
            if (read != bytes.length) throw new IOException("Incomplete read");
            fileBytes = bytes;
        }

        try {
            createHelperPod(deploymentName,uuid, podName, pvcName);
            waitForPodReady(podName);
            uploadFileToPod(podName, fileBytes, "/pv/test-model/1/model.onnx");
            log.info("File copied successfully to PVC");
        } finally {
            deleteHelperPod(podName);
        }
    }




    private void createPVandPVC(String serviceName, String uuid, String pvName, String pvcName, String storageClass, boolean defaultClass, boolean createLocalPv) throws ApiException {
        if(createLocalPv) {
            try {
                api.readPersistentVolume(pvName).execute();
                log.warn("PV already exists: {}", pvName);
            } catch (ApiException e) {
                if (e.getCode() == 404) {
                    V1PersistentVolume pv = new V1PersistentVolume()
                            .metadata(new V1ObjectMeta().name(pvName).labels(
                                            Map.of(
                                                    "app.kubernetes.io/managed-by", "faltio",
                                                    "app.kubernetes.io/part-of", serviceName,
                                                    "app.kubernetes.io/component", "model-storage-backend",
                                                    "app.kubernetes.io/instance", uuid,
                                                    "faltio.deployment-id", uuid
                                            )
                                    )
                            )
                            .spec(new V1PersistentVolumeSpec()
                                    .addAccessModesItem("ReadWriteOnce")
                                    .capacity(Collections.singletonMap("storage", Quantity.fromString("1Gi")))
                                    .persistentVolumeReclaimPolicy("Retain")
                                    .hostPath(new V1HostPathVolumeSource().path("/tmp/" + pvName))
                            );
                    api.createPersistentVolume(pv).execute();
                    log.info("PV {} Created", pvName);
                }
            }
        }

        try {
            api.readNamespacedPersistentVolumeClaim(pvcName, namespace).execute();
            log.warn("PVC already exists: {}", pvcName);
        }
        catch (ApiException e){
            V1PersistentVolumeClaimSpec spec = new V1PersistentVolumeClaimSpec()
                    .accessModes(Collections.singletonList("ReadWriteOnce"))
                    .resources(new V1VolumeResourceRequirements()
                                .requests(Collections.singletonMap("storage", Quantity.fromString("1Gi"))))
                                .volumeName(pvName);

            if(!defaultClass)
                spec = spec.storageClassName(storageClass);


            if(e.getCode() == 404){
                V1PersistentVolumeClaim pvc = new V1PersistentVolumeClaim()
                        .metadata(new V1ObjectMeta().name(pvcName).namespace(namespace).labels(
                                Map.of(
                                        "app.kubernetes.io/managed-by", "faltio",
                                        "app.kubernetes.io/part-of", serviceName,
                                        "app.kubernetes.io/component", "model-storage-pvc",
                                        "app.kubernetes.io/instance", uuid,
                                        "faltio.deployment-id", uuid
                                )
                        ))
                        .spec(spec);
                api.createNamespacedPersistentVolumeClaim(namespace, pvc).execute();
                log.info("PVC {} Created", pvcName);
            }
        }


    }

    private void createHelperPod(String serviceName, String uuid,String podName, String pvcName) throws ApiException {
        V1Pod pod = new V1Pod()
                .metadata(new V1ObjectMeta()
                        .name(podName)
                        .namespace(namespace).labels(
                                Map.of(
                                        "app.kubernetes.io/managed-by", "faltio",
                                        "app.kubernetes.io/part-of", serviceName,
                                        "app.kubernetes.io/component", "helper-pod",
                                        "app.kubernetes.io/instance", uuid,
                                        "faltio.deployment-id", uuid
                                )
                        ))
                .spec(new V1PodSpec().overhead(null)
                        .containers(Collections.singletonList(
                                new V1Container()
                                        .name("copy-container")
                                        .image("busybox")
                                        .command(Collections.singletonList("sleep"))
                                        .args(Collections.singletonList("3600"))
                                        .volumeMounts(Collections.singletonList(
                                                new V1VolumeMount()
                                                        .name("target-volume")
                                                        .mountPath("/pv")
                                        ))
                        ))
                        .restartPolicy("Never")
                        .volumes(Collections.singletonList(
                                new V1Volume()
                                        .name("target-volume")
                                        .persistentVolumeClaim(
                                                new V1PersistentVolumeClaimVolumeSource()
                                                        .claimName(pvcName)
                                        )
                        ))
                );

        api.createNamespacedPod(namespace, pod).execute();
        log.info("Helper pod created.");
    }

    private void waitForPodReady(String podName) throws Exception {
        for (int i = 0; i < 30; i++) {
            V1Pod pod = api.readNamespacedPod(podName, namespace).execute();
            if (pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase())) {
                log.info("Helper pod is running.");
                return;
            }
            Thread.sleep(2000);
        }
        throw new RuntimeException("Pod did not become ready in time.");
    }

    private void uploadFileToPod(String podName, byte[] localFile, String remoteFullPathInPod) throws Exception {

        Exec exec = new Exec();

        String[] command = new String[]{
                "sh", "-c", "mkdir -p $(dirname \"" + remoteFullPathInPod + "\")"
        };

        final Process proc = exec.exec(namespace, podName, command, "copy-container", true,false);
        proc.waitFor();
        proc.destroy();


        Copy copy = new Copy();
        copy.copyFileToPod(namespace, podName, "copy-container", localFile, Path.of(remoteFullPathInPod));

    }

    private void deleteHelperPod(String podName) {
        try {
            api.deleteNamespacedPod(podName, namespace).execute();
        } catch (ApiException e) {
            log.error("Failed to delete pod  {}" , podName);
        }
        log.info("Helper pod deleted.");
    }

    public InferenceServicePhase getInferenceServicePhase(String name) {
        try {
            CustomObjectsApi customApi = new CustomObjectsApi();
            Map response = (Map) customApi.getNamespacedCustomObjectStatus(
                    "serving.kserve.io",
                    "v1beta1",
                    namespace,
                    "inferenceservices",
                    name
            ).execute();

            Map status = (Map) response.get("status");
            if (status == null || !status.containsKey("conditions")) return InferenceServicePhase.WAITING;

            List<Map<String, String>> conditions = (List<Map<String, String>>) status.get("conditions");

            for (Map<String, String> cond : conditions) {
                if ("Ready".equals(cond.get("type"))) {
                    String value = cond.get("status");
                    if ("True".equals(value)) return InferenceServicePhase.READY;
                    if ("False".equals(value)) return InferenceServicePhase.FAILED;
                }
            }
            return InferenceServicePhase.WAITING;
        } catch (Exception e) {
            log.error("Error checking InferenceService status", e);
            return InferenceServicePhase.FAILED;
        }
    }




}
