package com.faltio.crd;

import com.faltio.crd.models.FaltioDeploymentSpec;
import com.faltio.crd.models.FaltioDeploymentStatus;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("faltio.io")
@Version("v1alpha1")
public class FaltioDeployment extends CustomResource<FaltioDeploymentSpec, FaltioDeploymentStatus> implements Namespaced {
}
