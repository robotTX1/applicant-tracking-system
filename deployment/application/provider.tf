terraform {
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "9.2.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "3.2.1"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "3.3.0"
    }
  }
}

provider "oci" {
  ignore_defined_tags = ["Oracle-Tags.CreatedBy", "Oracle-Tags.CreatedOn"]
}

data "oci_containerengine_cluster" "main" {
  cluster_id = var.oke_cluster_id
}

data "oci_containerengine_cluster_kube_config" "main" {
  cluster_id    = var.oke_cluster_id
  endpoint      = "PRIVATE_ENDPOINT"
  token_version = "2.0.0"
}

locals {
  oke_endpoint_parts = split(":", data.oci_containerengine_cluster.main.endpoints[0].private_endpoint)
  oke_private_ip     = local.oke_endpoint_parts[0]
  oke_port           = local.oke_endpoint_parts[1]
}

data "oci_resourcemanager_private_endpoint_reachable_ip" "oke_api" {
  private_endpoint_id = var.resourcemanager_private_endpoint_id
  private_ip          = local.oke_private_ip
}

locals {
  kubeconfig_parsed = yamldecode(data.oci_containerengine_cluster_kube_config.main.content)

  kubeconfig = {
    host                   = "https://${data.oci_resourcemanager_private_endpoint_reachable_ip.oke_api.ip_address}:${local.oke_port}"
    cluster_ca_certificate = base64decode(local.kubeconfig_parsed["clusters"][0]["cluster"]["certificate-authority-data"])
    insecure               = true
    exec_api_version       = "client.authentication.k8s.io/v1beta1"
    exec_command           = "oci"
    exec_command_args      = ["ce", "cluster", "generate-token", "--cluster-id", var.oke_cluster_id, "--region", var.region]
  }
}

provider "kubernetes" {
  host                   = local.kubeconfig.host
  cluster_ca_certificate = local.kubeconfig.cluster_ca_certificate
  insecure               = local.kubeconfig.insecure
  exec {
    api_version = local.kubeconfig.exec_api_version
    command     = local.kubeconfig.exec_command
    args        = local.kubeconfig.exec_command_args
  }
}

provider "helm" {
  kubernetes = {
    host                   = local.kubeconfig.host
    cluster_ca_certificate = local.kubeconfig.cluster_ca_certificate
    insecure               = local.kubeconfig.insecure
    exec = {
      api_version = local.kubeconfig.exec_api_version
      command     = local.kubeconfig.exec_command
      args        = local.kubeconfig.exec_command_args
    }
  }
}
