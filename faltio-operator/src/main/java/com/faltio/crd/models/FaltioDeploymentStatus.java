package com.faltio.crd.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FaltioDeploymentStatus {
    private String phase;
    private String message;
}
