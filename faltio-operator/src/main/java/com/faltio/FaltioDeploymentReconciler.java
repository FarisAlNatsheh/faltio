package com.faltio;

import com.faltio.crd.FaltioDeployment;
import com.faltio.crd.models.FaltioDeploymentStatus;
import com.faltio.services.KubernetesService;
import com.faltio.utils.JobPhase;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;


@ControllerConfiguration
public class FaltioDeploymentReconciler implements Reconciler<FaltioDeployment>, Cleaner<FaltioDeployment> {

    private static final Logger log = LoggerFactory.getLogger(FaltioDeploymentReconciler.class);

    private final KubernetesService kubernetesService;

    public FaltioDeploymentReconciler(KubernetesClient client) {
        this.kubernetesService = new KubernetesService(client);
    }

    @Override
    public UpdateControl<FaltioDeployment> reconcile(FaltioDeployment resource,
                                                     Context<FaltioDeployment> context) {


        log.info("Reconciling {}", resource.getMetadata().getName());

        if (resource.getStatus() == null) {
            resource.setStatus(new FaltioDeploymentStatus("Pending", "Initializing status"));
        }


        String namespace = resource.getMetadata().getNamespace();
        String jobName = "faltio-init-" + resource.getMetadata().getName();

        Job job = kubernetesService.getInitJob(namespace, jobName);


        if (job == null) {
            String currentPhase = resource.getStatus() != null ? resource.getStatus().getPhase() : null;

            if (!"Ready".equals(currentPhase) && !"Error".equals(currentPhase)) {
                log.info("Creating init job for {}", resource.getMetadata().getName());
                kubernetesService.createInitJob(namespace, jobName, resource);
                resource.getStatus().setPhase("Deploying");
                return UpdateControl.patchStatus(resource).rescheduleAfter(5, TimeUnit.SECONDS);
            } else {
                log.info("Job was cleaned up, but phase is terminal ({}), skipping job recreation", currentPhase);
                return UpdateControl.noUpdate();
            }
        }



        JobPhase phase = JobPhase.fromJob(job);

        return switch (phase) {
            case SUCCEEDED -> {
                resource.getStatus().setPhase("Ready");
                resource.getStatus().setMessage("");
                yield UpdateControl.patchStatus(resource);
            }
            case FAILED -> {
                resource.getStatus().setPhase("Error");

                Integer exitCode = kubernetesService.getJobContainerExitCode(namespace, jobName, "init-container");
                String message = switch (exitCode) {
                    case 2 -> "Model copy to PVC failed.";
                    case 3 -> "Failed to create InferenceService.";
                    case 4 -> "InferenceService entered failed state.";
                    case 5 -> "InferenceService readiness timed out.";
                    default -> "Unknown failure in init container.";
                };

                resource.getStatus().setMessage(message);
                log.warn("Job failed for {} with exit code {}: {}", resource.getMetadata().getName(), exitCode, message);

                yield UpdateControl.patchStatus(resource);
            }
            case PENDING, RUNNING -> {
                resource.getStatus().setPhase("Deploying");
                resource.getStatus().setMessage("Init job running");
                yield UpdateControl.patchStatus(resource).rescheduleAfter(10, TimeUnit.SECONDS);
            }
        };



    }

    @Override
    public DeleteControl cleanup(FaltioDeployment resource, Context<FaltioDeployment> context) throws Exception {
        String namespace = resource.getMetadata().getNamespace();
        String deploymentName = resource.getSpec().getDeploymentName();
        String jobName = "faltio-init-" + resource.getMetadata().getName();

        log.info("Cleaning up resources for {}", deploymentName);
        resource.setStatus(new FaltioDeploymentStatus("Terminating", ""));
        kubernetesService.deletePVC(namespace, deploymentName);
        kubernetesService.deletePV(deploymentName);
        kubernetesService.deleteInferenceService(namespace, deploymentName);
        kubernetesService.deleteInitJob(namespace, jobName);
        kubernetesService.deleteFileCopyPod(namespace, deploymentName);

        boolean pvcGone = kubernetesService.getPVC(namespace, "model-"+deploymentName) == null;
        boolean pvGone = kubernetesService.getPV("model-"+deploymentName+"-pv") == null;
        boolean inferenceServiceGone = !kubernetesService.inferenceServiceExists(namespace,deploymentName);
        boolean jobGone = kubernetesService.getInitJob(namespace, jobName) == null;
        boolean copyPodGone = kubernetesService.getFileCopyPod(namespace, deploymentName) == null;

        if (pvcGone && pvGone && inferenceServiceGone && jobGone && copyPodGone) {
            log.info("All resources for {} have been deleted", deploymentName);
            return DeleteControl.defaultDelete();
        } else {
            log.info("Waiting for resources to be deleted: PVC={}, PV={}, InferenceService={}, Job={}", !pvcGone, !pvGone, !inferenceServiceGone, !jobGone);
            return DeleteControl
                    .noFinalizerRemoval()
                    .rescheduleAfter(10, TimeUnit.SECONDS);
        }
    }
}
