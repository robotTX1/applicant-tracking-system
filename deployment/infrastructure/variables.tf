### General ###

variable "tenancy_ocid" {
  type = string
}

### OKE ###

variable "oke_kubernetes_version" {
  type        = string
  description = "Kubernetes version to use"
}

variable "oke_node_counts_by_ad" {
  description = "Number of worker nodes per Availability Domain"
  type        = map(number)
  default = {
    "ad-1" = 2
    "ad-2" = 1
    "ad-3" = 1
  }
}

variable "oke_node_shape" {
  description = "Compute shape for worker nodes"
  type        = string
  default     = "VM.Standard.A1.Flex"
}

variable "oke_node_ocpus" {
  description = "OCPUs per node"
  type        = number
  default     = 1
}

variable "oke_node_memory_in_gbs" {
  description = "Memory in GBs per node"
  type        = number
  default     = 6
}

variable "oke_node_volume_size_in_gbs" {
  description = "Volume size in GBs"
  type        = number
  default     = 50
}

variable "oke_image_ocid" {
  type = string
}

### MySQL ###

variable "mysql_admin_username" {
  type        = string
  description = "Admin username for MySQL DB system"
  default     = "ADMIN"
}

variable "mysql_admin_password_secret_name" {
  type        = string
  description = "Name of the Vault secret containing the MySQL admin password"
  default     = "mysql-admin-password"
}

variable "mysql_version" {
  type        = string
  description = "MySQL Server version"
  default     = "26.7.0"
}

variable "mysql_availability_domain_number" {
  type        = number
  description = "Availability Domain number (1, 2, or 3) for MySQL DB system"
  default     = 3
}
