data "oci_identity_compartments" "security" {
  compartment_id            = var.tenancy_ocid
  compartment_id_in_subtree = true
  filter {
    name   = "name"
    values = ["security"]
  }
}

data "oci_identity_compartments" "network" {
  compartment_id            = var.tenancy_ocid
  compartment_id_in_subtree = true
  filter {
    name   = "name"
    values = ["network"]
  }
}