### Dynamic Groups ###

resource "oci_identity_dynamic_group" "oke_clusters" {
  compartment_id = var.tenancy_ocid
  name           = "oke-clusters-dynamic-group"
  description    = "Dynamic group for OKE clusters and KMS"
  matching_rule  = "ALL {resource.type = 'cluster', resource.compartment.id = '${oci_identity_compartment.workloads.id}'}"
}

### Policies ###

resource "oci_identity_policy" "oke_kms_access" {
  compartment_id = var.tenancy_ocid
  name           = "oke-kms-access-policy"
  description    = "Allows OKE clusters to use the master encryption key in the security compartment"
  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.oke_clusters.name} to use vaults in compartment ${oci_identity_compartment.security.name}",
    "Allow dynamic-group ${oci_identity_dynamic_group.oke_clusters.name} to use keys in compartment ${oci_identity_compartment.security.name} where target.key.id = '${oci_kms_key.main.id}'"
  ]
}

resource "oci_identity_policy" "mysql_network" {
  compartment_id = var.tenancy_ocid
  name           = "mysql-network-policy"
  description    = "Allows MySQL DB systems in workloads to attach to subnets and NSGs in network compartment"
  defined_tags   = local.default_tags

  statements = [
    "Allow any-user to {NETWORK_SECURITY_GROUP_UPDATE_MEMBERS} in compartment ${oci_identity_compartment.network.name} where all {request.principal.type='mysqldbsystem', request.resource.compartment.id='${oci_identity_compartment.workloads.id}'}",
    "Allow any-user to {VNIC_CREATE, VNIC_UPDATE, VNIC_ASSOCIATE_NETWORK_SECURITY_GROUP, VNIC_DISASSOCIATE_NETWORK_SECURITY_GROUP} in compartment ${oci_identity_compartment.network.name} where all {request.principal.type='mysqldbsystem', request.resource.compartment.id='${oci_identity_compartment.workloads.id}'}",
    "Allow any-user to {VCN_READ, SUBNET_READ, SUBNET_ATTACH, SUBNET_DETACH} in compartment ${oci_identity_compartment.network.name} where all {request.principal.type='mysqldbsystem', request.resource.compartment.id='${oci_identity_compartment.workloads.id}'}"
  ]
}

