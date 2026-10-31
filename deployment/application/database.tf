### MySQL Database Lookup ###

data "oci_mysql_mysql_db_systems" "main" {
  compartment_id = data.oci_identity_compartments.workloads.compartments[0].id
  state          = "ACTIVE"
}

data "oci_mysql_mysql_db_system" "main" {
  db_system_id = data.oci_mysql_mysql_db_systems.main.db_systems[0].id
}

locals {
  mysql_ip   = coalesce(data.oci_mysql_mysql_db_system.main.ip_address, data.oci_mysql_mysql_db_system.main.endpoints[0].ip_address)
  mysql_port = coalesce(data.oci_mysql_mysql_db_system.main.port, data.oci_mysql_mysql_db_system.main.endpoints[0].port, 3306)
}
