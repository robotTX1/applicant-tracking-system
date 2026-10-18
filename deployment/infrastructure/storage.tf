data "oci_objectstorage_namespace" "ns" {
  compartment_id = var.tenancy_ocid
}

resource "oci_objectstorage_bucket" "workloads" {
  compartment_id = oci_identity_compartment.workloads.id
  name           = var.workloads_bucket_name
  namespace      = data.oci_objectstorage_namespace.ns.namespace
  storage_tier   = "Standard"
  access_type    = "NoPublicAccess"
  kms_key_id     = oci_kms_key.main.id
  defined_tags   = local.default_tags

  depends_on = [oci_identity_policy.kms_access]
}
