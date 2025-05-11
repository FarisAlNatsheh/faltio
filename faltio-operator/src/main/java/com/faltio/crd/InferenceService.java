package com.faltio.crd;


import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import io.fabric8.kubernetes.api.model.Namespaced;

@Group("serving.kserve.io")
@Version("v1beta1")
public class InferenceService extends CustomResource<Void, Void> implements Namespaced {
}
