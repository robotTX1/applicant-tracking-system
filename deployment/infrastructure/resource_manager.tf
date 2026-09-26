resource "oci_resourcemanager_private_endpoint" "main" {
  compartment_id = oci_identity_compartment.network.id
  display_name   = "management-private-endpoint"
  description    = "Resource Manager private endpoint for cluster management and application deployments"
  vcn_id         = oci_core_vcn.main.id
  subnet_id      = oci_core_subnet.management.id
  nsg_id_list    = [oci_core_network_security_group.management.id]
  defined_tags   = local.default_tags
}
