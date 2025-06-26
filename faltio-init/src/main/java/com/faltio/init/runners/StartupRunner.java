package com.faltio.init.runners;

import com.faltio.init.config.FaltioDeploymentProperties;
import com.faltio.init.models.InferenceServicePhase;
import com.faltio.init.services.KubernetesService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class StartupRunner implements CommandLineRunner {

    private final KubernetesService kubernetesService;
    private final FaltioDeploymentProperties props;

    @Override
    public void run(String... args) throws Exception {
        String uuid = props.getDeploymentName();

        try {
            kubernetesService.copyFileToPVC(
                    props.getDeploymentName(),
                    props.getModelName(),
                    props.getModelFilePath(),
                    props.getModelVersion(),
                    props.getStorageClass(),
                    props.isDefaultStorageClass(),
                    props.isCreateLocalPv(),
                    uuid
            );
        } catch (Exception e) {
            log.error("Failed to copy model to PVC: {}", e.getMessage(), e);
            System.exit(2); // model download or upload failure
        }

        try {
            kubernetesService.createInferenceService(
                    props.getDeploymentName(),
                    uuid,
                    "model-" + uuid
            );
        } catch (Exception e) {
            log.error("Failed to create InferenceService: {}", e.getMessage(), e);
            System.exit(3);
        }

        log.info("Waiting for InferenceService to be ready...");
        int timeoutSeconds = 120;
        int waited = 0;
        int step = 5;

        while (waited < timeoutSeconds) {
            InferenceServicePhase phase = kubernetesService.getInferenceServicePhase(props.getDeploymentName());
            log.info("InferenceService phase: {}", phase);

            if (phase == InferenceServicePhase.READY) {
                log.info("InferenceService is ready.");
                System.exit(0);
            }

            if (phase == InferenceServicePhase.FAILED) {
                log.error("InferenceService failed to become ready.");
                System.exit(4);
            }

            Thread.sleep(step * 1000);
            waited += step;
        }

        log.error("InferenceService did not become ready in time.");
        System.exit(5);
    }
}
