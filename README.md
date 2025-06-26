<p align="center">
  <img src="assets/faltio-logo-cropped.svg" alt="Faltio Logo" height="120"/>
</p>

<h1 align="center">Faltio Operator</h1>

<p align="center"><b>Kubernetes-native model deployment developer tool</b></p>

<p align="center">
  ⚠️ This project is a work-in-progress developer tool and is <strong>not production-ready</strong>.
</p>

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

---

## What is Faltio?

**Faltio** (pronounced *fal-tee-oh*) is a Kubernetes-native tool that simplifies deploying machine learning models using:
- **KServe** for serving models
- **MLflow** as a model registry
- A clean **CRD interface** for declarative deployments of Machine Learning models.

It's designed for **developers** who want to test, iterate, and prototype ML deployment on Kubernetes with minimal setup.

---

## Example

```yaml
apiVersion: faltio.io/v1alpha1
kind: FaltioDeployment
metadata:
  name: road-objects-detector
spec:
  modelName: yolo_model_onnx
  modelVersion: "7"
  modelFilePath: yolov8m.onnx
  deploymentName: road-objects-detector
  runtime: onnx
  storageSize: 1Gi
  serviceAccount: faltio-sa
  #storageClass: test
  testMode: true ## Creates a local PV for testing purposes, overrides storage class to an empty string

  mlflow:
    host: http://mlflow.example.com
    ignoreTls: true
    secretName: mlflow-credentials
```

## 🧪 Intended Use

This project was built as a **developer convenience tool** for internal use, experimentation, and iterative testing. Faltio is **NOT** a production-ready tool yet.

This tool currently only deploys ONNX models.