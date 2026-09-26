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

data "oci_resourcemanager_private_endpoint_reachable_ip" "oke_api" {
  private_endpoint_id = var.resourcemanager_private_endpoint_id
  private_ip          = data.oci_containerengine_cluster.main.endpoints[0].private_endpoint
}

locals {
  kubeconfig = {
    host                   = "https://${data.oci_resourcemanager_private_endpoint_reachable_ip.oke_api.ip_address}:6443"
    cluster_ca_certificate = base64decode(data.oci_containerengine_cluster_kube_config.main.content)
    cluster_id             = yamldecode(data.oci_containerengine_cluster_kube_config.main.content["users"][0]["user"]["exec"]["args"][4])
    cluster_region         = yamldecode(data.oci_containerengine_cluster_kube_config.main.content["users"][0]["user"]["exec"]["args"][6])
    insecure               = true
    exec_api_version       = "client.authentication.k8s.io/v1beta"
    exec_command           = "oci"
    exec_command_args      = ["ce", "clister", "generate-token", "--cluster-id", local.kubeconfig.cluster_id, "--region", local.kubeconfig.cluster_region]
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
