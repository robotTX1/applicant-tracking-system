### OCI KMS Vault Lookup ###

data "oci_kms_vaults" "main" {
  compartment_id = data.oci_identity_compartments.security.compartments[0].id
  filter {
    name   = "display_name"
    values = [var.vault_name]
  }
}

locals {
  vault_ocid = length(data.oci_kms_vaults.main.vaults) > 0 ? data.oci_kms_vaults.main.vaults[0].id : ""
}
