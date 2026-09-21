data "oci_vault_secrets" "mysql_admin_password" {
  compartment_id = oci_identity_compartment.security.id
  name           = var.mysql_admin_password_secret_name
  state          = "ACTIVE"
}

data "oci_secrets_secretbundle" "mysql_admin_password" {
  secret_id = data.oci_vault_secrets.mysql_admin_password.secrets[0].id
}

resource "oci_mysql_mysql_db_system" "main" {
  compartment_id      = oci_identity_compartment.workloads.id
  display_name        = "mysql-db"
  availability_domain = data.oci_identity_availability_domains.domains.availability_domains[var.mysql_availability_domain_number - 1].name
  fault_domain        = "FAULT-DOMAIN-2"
  shape_name          = "MySQL.Free"

  subnet_id = oci_core_subnet.database.id
  nsg_ids   = [oci_core_network_security_group.database.id]

  data_storage_size_in_gb = 50
  mysql_version           = var.mysql_version
  admin_username          = var.mysql_admin_username
  admin_password          = base64decode(data.oci_secrets_secretbundle.mysql_admin_password.secret_bundle_content[0].content)
  defined_tags            = local.default_tags

  deletion_policy {
    automatic_backup_retention = "RETAIN"
    final_backup               = "SKIP_FINAL_BACKUP"
    is_delete_protected        = true
  }
}

resource "oci_mysql_heat_wave_cluster" "main" {
  db_system_id         = oci_mysql_mysql_db_system.main.id
  cluster_size         = 1
  shape_name           = "HeatWave.Free"
  is_lakehouse_enabled = true
}

