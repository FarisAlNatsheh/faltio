#!/bin/bash
set -euo pipefail

echo "Applying Knative Serving CRDs..."
kubectl apply -f https://github.com/knative/serving/releases/download/knative-v1.18.0/serving-crds.yaml

echo "Applying Knative Serving Core..."
kubectl apply -f https://github.com/knative/serving/releases/download/knative-v1.18.0/serving-core.yaml

echo "Adding Helm repositories..."
helm repo add istio https://istio-release.storage.googleapis.com/charts
helm repo add jetstack https://charts.jetstack.io
helm repo add nvidia https://helm.ngc.nvidia.com/nvidia

helm repo update

echo "Installing Istio Base..."
helm upgrade --install istio-base istio/base -n istio-system --set defaultRevision=default --create-namespace
kubectl apply -f https://github.com/knative/net-istio/releases/download/knative-v1.18.0/istio.yaml
kubectl apply -f https://github.com/knative/net-istio/releases/download/knative-v1.18.0/net-istio.yaml
kubectl patch deployment istiod -n istio-system --type='merge' -p '{"spec":{"replicas":1}}' || true
kubectl patch deployment istio-ingressgateway -n istio-system --type='merge' -p '{"spec":{"replicas":1}}' || true



echo "Installing cert-manager..."
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --version v1.17.2 \
  --set crds.enabled=true \
  --wait

echo "Installing Nvidia Operator..."
helm upgrade --install gpu-operator nvidia/gpu-operator \
  --namespace gpu-operator \
  --create-namespace \
  --version=v25.3.0 \
  --wait

echo "Installing KServe CRDs..."
helm upgrade --install kserve-crd oci://ghcr.io/kserve/charts/kserve-crd \
  --version v0.15.0 \
  --wait

echo "Installing KServe..."
helm upgrade --install kserve oci://ghcr.io/kserve/charts/kserve \
  --version v0.15.0 \
  --wait

echo "Installing Faltio..."
helm install faltio ./helm/faltio-operator \
  --create-namespace \
  --namespace faltio-system

echo "All components installed successfully."

