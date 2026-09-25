locals {
  default_tags = {
    "${oci_identity_tag_namespace.governance.name}.${oci_identity_tag.managed_by.name}" = "terraform"
  }
}

resource "oci_identity_tag_namespace" "governance" {
  compartment_id = var.tenancy_ocid
  name           = "Governance"
  description    = "Governance and lifecycle tags"
}

resource "oci_identity_tag" "managed_by" {
  tag_namespace_id = oci_identity_tag_namespace.governance.id
  name             = "ManagedBy"
  description      = "Differentiates Terraform manged and manual resources"
  is_cost_tracking = false
}
