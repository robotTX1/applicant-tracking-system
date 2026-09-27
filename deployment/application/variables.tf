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

### Keycloak ###

variable "keycloak_chart_version" {
  type        = string
  description = "Version of the Keycloak Helm chart"
  default     = "7.3.2"
}

variable "keycloak_hostname" {
  type        = string
  description = "Hostname for Keycloak"
  default     = "auth.robottx.hu"
}

variable "keycloak_ingress_hosts" {
  type        = list(string)
  description = "Host matching rules for Keycloak IngressRoute"
  default     = ["auth.robottx.hu", "www.auth.robottx.hu"]
}

variable "keycloak_certificate_dns_names" {
  type        = list(string)
  description = "DNS names for Keycloak TLS certificate"
  default     = ["auth.robottx.hu", "robottx.hu"]
}

variable "keycloak_db_name" {
  type        = string
  description = "Name of the MySQL database for Keycloak"
  default     = "keycloak"
}

variable "keycloak_replicas" {
  type        = number
  description = "Number of Keycloak replicas"
  default     = 2
}

variable "keycloak_admin_username_secret_name" {
  type        = string
  description = "Name of the Vault secret containing the Keycloak bootstrap admin username"
  default     = "keycloak-temp-admin-username"
}

variable "keycloak_admin_password_secret_name" {
  type        = string
  description = "Name of the Vault secret containing the Keycloak bootstrap admin password"
  default     = "keycloak-temp-admin-password"
}

variable "keycloak_db_username_secret_name" {
  type        = string
  description = "Name of the Vault secret containing the MySQL username for Keycloak"
  default     = "mysql-keycloak-username"
}

variable "keycloak_db_password_secret_name" {
  type        = string
  description = "Name of the Vault secret containing the MySQL password for Keycloak"
  default     = "mysql-keycloak-password"
}

### Metrics Server ###

variable "metrics_server_chart_version" {
  type        = string
  description = "Version of the Metrics Server Helm chart"
  default     = "3.14.0"
}

variable "metrics_server_replicas" {
  type        = number
  description = "Number of Metrics Server replicas"
  default     = 2
}


