### Object Storage Namespace & Bucket Lookup ###

data "oci_objectstorage_namespace" "main" {
  compartment_id = var.tenancy_ocid
}

data "oci_objectstorage_bucket_summaries" "workloads" {
  compartment_id = data.oci_identity_compartments.workloads.compartments[0].id
  namespace      = data.oci_objectstorage_namespace.main.namespace
}

locals {
  bucket_name      = length(data.oci_objectstorage_bucket_summaries.workloads.bucket_summaries) > 0 ? data.oci_objectstorage_bucket_summaries.workloads.bucket_summaries[0].name : ""
  bucket_namespace = data.oci_objectstorage_namespace.main.namespace
}
