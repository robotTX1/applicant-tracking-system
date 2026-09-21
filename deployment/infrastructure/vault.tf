resource "oci_kms_vault" "main" {
  compartment_id = oci_identity_compartment.security.id
  display_name   = "MainVault"
  vault_type     = "DEFAULT"
  defined_tags   = local.default_tags
}

resource "oci_kms_key" "main" {
  compartment_id      = oci_identity_compartment.security.id
  display_name        = "MEK"
  management_endpoint = oci_kms_vault.main.management_endpoint

  key_shape {
    algorithm = "AES"
    length    = 32
  }
}
