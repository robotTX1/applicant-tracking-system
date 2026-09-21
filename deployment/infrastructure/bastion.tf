resource "oci_bastion_bastion" "main" {
  bastion_type                 = "STANDARD"
  name                         = "bastion"
  compartment_id               = oci_identity_compartment.security.id
  target_subnet_id             = oci_core_subnet.bastion.id
  dns_proxy_status             = "ENABLED"
  max_session_ttl_in_seconds   = 10800
  client_cidr_block_allow_list = ["0.0.0.0/0"]
  defined_tags                 = local.default_tags
}
