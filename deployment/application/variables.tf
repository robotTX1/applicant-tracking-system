### General ###

variable "tenancy_ocid" {
  type = string
}

variable "region" {
  type = string
}

### OKE ###

variable "oke_cluster_id" {
  type = string
}

### Resource Manager ###

variable "resourcemanager_private_endpoint_id" {
  type = string
}