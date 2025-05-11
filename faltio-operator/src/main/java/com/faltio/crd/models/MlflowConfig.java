package com.faltio.crd.models;

import lombok.Data;

@Data
public class MlflowConfig {
    private String host;
    private Boolean ignoreTls;
    private String secretName;
}
