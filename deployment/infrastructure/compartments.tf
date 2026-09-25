resource "oci_identity_compartment" "network" {
  compartment_id = var.tenancy_ocid
  name           = "network"
  description    = "Shared networking infrastructure (VCNs, DRGs, Gateways)"
  defined_tags   = local.default_tags
}

resource "oci_identity_compartment" "security" {
  compartment_id = var.tenancy_ocid
  name           = "security"
  description    = "Central security, Vaults, and Audit logs"
  defined_tags   = local.default_tags
}

resource "oci_identity_compartment" "workloads" {
  compartment_id = var.tenancy_ocid
  name           = "workloads"
  description    = "Parent compartment for applications"
  defined_tags   = local.default_tags
}

