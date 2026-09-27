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

### Cert Manager ###

variable "cert_manager_version" {
  type    = string
  default = "v1.21.2"
}

variable "cloudflare_vault_secret_name" {
  type        = string
  description = "Name of the secret in OCI Vault containing the Cloudflare API token"
  default     = "cloudflare-cert-manager-api-token"
}

variable "cloudflare_k8s_secret_name" {
  type        = string
  description = "Name of the Kubernetes secret created in cert-manager namespace"
  default     = "cloudflare-api-token-secret"
}

variable "acme_email" {
  type        = string
  description = "Email address used for ACME (Let's Encrypt) registration"
  default     = "csikos.csaba.hu@gmail.com"
}

### Traefik ###

variable "traefik_chart_version" {
  type        = string
  description = "Version of the Traefik Helm chart"
  default     = "41.6.0"
}

variable "traefik_load_balancer_nsg_id" {
  type        = string
  description = "OCID of the Network Security Group for Traefik Load Balancer (optional, dynamically resolved if null)"
  default     = null
}
