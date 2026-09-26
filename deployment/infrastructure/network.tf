### VCNs ###

locals {
  main_vcn_cidr = "10.1.0.0/16"
}

resource "oci_core_vcn" "main" {
  compartment_id = oci_identity_compartment.network.id
  cidr_block     = local.main_vcn_cidr
  display_name   = "main"
  dns_label      = "main"
  defined_tags   = local.default_tags
}

### Gateways ###

resource "oci_core_internet_gateway" "main" {
  compartment_id = oci_identity_compartment.network.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "main-internet-gateway"
  enabled        = true
  defined_tags   = local.default_tags
}

resource "oci_core_nat_gateway" "main" {
  compartment_id = oci_identity_compartment.network.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "main-nat-gateway"
  defined_tags   = local.default_tags
}

resource "oci_core_service_gateway" "main" {
  compartment_id = oci_identity_compartment.network.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "main-service-gateway"
  services {
    service_id = data.oci_core_services.all_services.services[0].id
  }
  defined_tags = local.default_tags
}

data "oci_core_services" "all_services" {
  filter {
    name   = "name"
    values = ["All .* Services In Oracle Services Network"]
    regex  = true
  }
}


### Subnets ###

locals {
  oke_api_subnet_cidr           = "10.1.1.0/24"
  oke_worker_subnet_cidr        = "10.1.2.0/24"
  oke_load_balancer_subnet_cidr = "10.1.3.0/24"
  bastion_subnet_cidr           = "10.1.4.0/28"
  database_subnet_cidr          = "10.1.5.0/24"
  management_subnet_cidr        = "10.1.6.0/28"
}

resource "oci_core_subnet" "oke_api" {
  compartment_id             = oci_identity_compartment.network.id
  vcn_id                     = oci_core_vcn.main.id
  display_name               = "oke-api-subnet"
  cidr_block                 = local.oke_api_subnet_cidr
  prohibit_public_ip_on_vnic = true
  route_table_id             = oci_core_route_table.oke_private.id
  defined_tags               = local.default_tags
}

resource "oci_core_subnet" "oke_worker" {
  compartment_id             = oci_identity_compartment.network.id
  vcn_id                     = oci_core_vcn.main.id
  display_name               = "oke-worker-subnet"
  cidr_block                 = local.oke_worker_subnet_cidr
  prohibit_public_ip_on_vnic = true
  route_table_id             = oci_core_route_table.oke_private.id
  defined_tags               = local.default_tags
}

resource "oci_core_subnet" "oke_load_balancer" {
  compartment_id             = oci_identity_compartment.network.id
  vcn_id                     = oci_core_vcn.main.id
  display_name               = "oke-load-balancer-subnet"
  cidr_block                 = local.oke_load_balancer_subnet_cidr
  prohibit_public_ip_on_vnic = false
  route_table_id             = oci_core_route_table.oke_public.id
  defined_tags               = local.default_tags
}

resource "oci_core_subnet" "bastion" {
  compartment_id             = oci_identity_compartment.network.id
  vcn_id                     = oci_core_vcn.main.id
  display_name               = "bastion-subnet"
  cidr_block                 = local.bastion_subnet_cidr
  prohibit_public_ip_on_vnic = true
  route_table_id             = oci_core_route_table.bastion.id
  defined_tags               = local.default_tags
}

resource "oci_core_subnet" "database" {
  compartment_id             = oci_identity_compartment.network.id
  vcn_id                     = oci_core_vcn.main.id
  display_name               = "database-subnet"
  cidr_block                 = local.database_subnet_cidr
  prohibit_public_ip_on_vnic = true
  route_table_id             = oci_core_route_table.database.id
  defined_tags               = local.default_tags
}

resource "oci_core_subnet" "management" {
  compartment_id             = oci_identity_compartment.network.id
  vcn_id                     = oci_core_vcn.main.id
  display_name               = "management-subnet"
  cidr_block                 = local.management_subnet_cidr
  prohibit_public_ip_on_vnic = true
  route_table_id             = oci_core_route_table.management.id
  defined_tags               = local.default_tags
}

### Route tables ###

resource "oci_core_route_table" "oke_private" {
  compartment_id = oci_identity_compartment.network.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "oke-private-subnet-route-table"
  defined_tags   = local.default_tags
  route_rules {
    network_entity_id = oci_core_nat_gateway.main.id
    description       = "NAT gateway route rule"
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
  }
  route_rules {
    network_entity_id = oci_core_service_gateway.main.id
    description       = "Oracle Services route rule"
    destination       = data.oci_core_services.all_services.services[0].cidr_block
    destination_type  = "SERVICE_CIDR_BLOCK"
  }
}

resource "oci_core_route_table" "oke_public" {
  compartment_id = oci_identity_compartment.network.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "oke-public-subnet-route-table"
  defined_tags   = local.default_tags
  route_rules {
    network_entity_id = oci_core_internet_gateway.main.id
    description       = "Internet gateway route rule"
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
  }
}

resource "oci_core_route_table" "bastion" {
  compartment_id = oci_identity_compartment.network.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "bastion-subnet-route-table"
  defined_tags   = local.default_tags

  route_rules {
    network_entity_id = oci_core_service_gateway.main.id
    description       = "Oracle Services route rule"
    destination       = data.oci_core_services.all_services.services[0].cidr_block
    destination_type  = "SERVICE_CIDR_BLOCK"
  }
}

resource "oci_core_route_table" "database" {
  compartment_id = oci_identity_compartment.network.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "database-subnet-route-table"
  defined_tags   = local.default_tags

  route_rules {
    network_entity_id = oci_core_service_gateway.main.id
    description       = "Oracle Services route rule"
    destination       = data.oci_core_services.all_services.services[0].cidr_block
    destination_type  = "SERVICE_CIDR_BLOCK"
  }
}

resource "oci_core_route_table" "management" {
  compartment_id = oci_identity_compartment.network.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "management-subnet-route-table"
  defined_tags   = local.default_tags

  route_rules {
    network_entity_id = oci_core_service_gateway.main.id
    description       = "Oracle Services route rule"
    destination       = data.oci_core_services.all_services.services[0].cidr_block
    destination_type  = "SERVICE_CIDR_BLOCK"
  }
}

### Network Security Groups ###

resource "oci_core_network_security_group" "oke_control_plane" {
  compartment_id = oci_identity_compartment.network.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "oke-control-plane-nsg"
  defined_tags   = local.default_tags
}

resource "oci_core_network_security_group" "oke_worker" {
  compartment_id = oci_identity_compartment.network.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "oke-worker-nsg"
  defined_tags   = local.default_tags
}

resource "oci_core_network_security_group" "oke_load_balancer" {
  compartment_id = oci_identity_compartment.network.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "oke-load-balancer-nsg"
  defined_tags   = local.default_tags
}

resource "oci_core_network_security_group" "database" {
  compartment_id = oci_identity_compartment.network.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "database-nsg"
  defined_tags   = local.default_tags
}

resource "oci_core_network_security_group" "management" {
  compartment_id = oci_identity_compartment.network.id
  vcn_id         = oci_core_vcn.main.id
  display_name   = "management-nsg"
  defined_tags   = local.default_tags
}

### Network Security Group Rules ###
# Protocol numbers: 6 = TCP, 1 = ICMP, "all" = All protocols

# Kubernetes Control Plane Ingress Rules

resource "oci_core_network_security_group_security_rule" "control_plane_ingress_workers_6443" {
  network_security_group_id = oci_core_network_security_group.oke_control_plane.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source_type               = "NETWORK_SECURITY_GROUP"
  source                    = oci_core_network_security_group.oke_worker.id
  description               = "Allow worker nodes to reach Kubernetes API"

  tcp_options {
    destination_port_range {
      min = 6443
      max = 6443
    }
  }
}

resource "oci_core_network_security_group_security_rule" "control_plane_ingress_workers_12250" {
  network_security_group_id = oci_core_network_security_group.oke_control_plane.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source_type               = "NETWORK_SECURITY_GROUP"
  source                    = oci_core_network_security_group.oke_worker.id
  description               = "Allow worker nodes to communicate with OKE control plane"

  tcp_options {
    destination_port_range {
      min = 12250
      max = 12250
    }
  }
}

resource "oci_core_network_security_group_security_rule" "control_plane_ingress_workers_icmp" {
  network_security_group_id = oci_core_network_security_group.oke_control_plane.id
  direction                 = "INGRESS"
  protocol                  = "1"
  source_type               = "NETWORK_SECURITY_GROUP"
  source                    = oci_core_network_security_group.oke_worker.id
  description               = "Path MTU discovery from workers"

  icmp_options {
    type = 3
    code = 4
  }
}

# Kubernetes Control Plane Egress Rules

resource "oci_core_network_security_group_security_rule" "control_plane_egress_workers_all_tcp" {
  network_security_group_id = oci_core_network_security_group.oke_control_plane.id
  direction                 = "EGRESS"
  protocol                  = "6"
  destination_type          = "NETWORK_SECURITY_GROUP"
  destination               = oci_core_network_security_group.oke_worker.id
  description               = "All TCP traffic to worker nodes (for Flannel webhooks and kubelets)"
}

resource "oci_core_network_security_group_security_rule" "control_plane_egress_osn" {
  network_security_group_id = oci_core_network_security_group.oke_control_plane.id
  direction                 = "EGRESS"
  protocol                  = "6"
  destination_type          = "SERVICE_CIDR_BLOCK"
  destination               = data.oci_core_services.all_services.services[0].cidr_block
  description               = "Allow control plane to communicate with Oracle Services Network"

  tcp_options {
    destination_port_range {
      min = 443
      max = 443
    }
  }
}

resource "oci_core_network_security_group_security_rule" "control_plane_egress_icmp" {
  network_security_group_id = oci_core_network_security_group.oke_control_plane.id
  direction                 = "EGRESS"
  protocol                  = "1"
  destination_type          = "NETWORK_SECURITY_GROUP"
  destination               = oci_core_network_security_group.oke_worker.id
  description               = "Path MTU discovery to workers"

  icmp_options {
    type = 3
    code = 4
  }
}

# Kubernetes Worker Ingress Rules

resource "oci_core_network_security_group_security_rule" "worker_ingress_self" {
  network_security_group_id = oci_core_network_security_group.oke_worker.id
  direction                 = "INGRESS"
  protocol                  = "all"
  source_type               = "NETWORK_SECURITY_GROUP"
  source                    = oci_core_network_security_group.oke_worker.id
  description               = "Allows intra-cluster pod and VXLAN communication"
}

resource "oci_core_network_security_group_security_rule" "worker_ingress_control_plane_all_tcp" {
  network_security_group_id = oci_core_network_security_group.oke_worker.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source_type               = "NETWORK_SECURITY_GROUP"
  source                    = oci_core_network_security_group.oke_control_plane.id
  description               = "Allows all TCP traffic from control plane (kubelet and webhooks)"
}

resource "oci_core_network_security_group_security_rule" "worker_ingress_lb_nodeports" {
  network_security_group_id = oci_core_network_security_group.oke_worker.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source_type               = "NETWORK_SECURITY_GROUP"
  source                    = oci_core_network_security_group.oke_load_balancer.id
  description               = "Allow Load Balancer traffic to reach Traefik on NodePorts"

  tcp_options {
    destination_port_range {
      min = 30000
      max = 32767
    }
  }
}

resource "oci_core_network_security_group_security_rule" "worker_ingress_lb_healthcheck" {
  network_security_group_id = oci_core_network_security_group.oke_worker.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source_type               = "NETWORK_SECURITY_GROUP"
  source                    = oci_core_network_security_group.oke_load_balancer.id
  description               = "Allow Load Balancer health checks"

  tcp_options {
    destination_port_range {
      min = 10256
      max = 10256
    }
  }
}

resource "oci_core_network_security_group_security_rule" "worker_ingress_icmp" {
  network_security_group_id = oci_core_network_security_group.oke_worker.id
  direction                 = "INGRESS"
  protocol                  = "1"
  source_type               = "CIDR_BLOCK"
  source                    = "0.0.0.0/0"
  description               = "Path MTU Discovery"

  icmp_options {
    type = 3
    code = 4
  }
}

resource "oci_core_network_security_group_security_rule" "control_plane_ingress_bastion_6443" {
  network_security_group_id = oci_core_network_security_group.oke_control_plane.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source_type               = "CIDR_BLOCK"
  source                    = local.bastion_subnet_cidr
  description               = "Allow Bastion port-forwarding to Kubernetes API"

  tcp_options {
    destination_port_range {
      min = 6443
      max = 6443
    }
  }
}

resource "oci_core_network_security_group_security_rule" "control_plane_ingress_management_6443" {
  network_security_group_id = oci_core_network_security_group.oke_control_plane.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source_type               = "NETWORK_SECURITY_GROUP"
  source                    = oci_core_network_security_group.management.id
  description               = "Allow Resource Manager management endpoint to reach Kubernetes API"

  tcp_options {
    destination_port_range {
      min = 6443
      max = 6443
    }
  }
}

# Kubernetes Worker Egress Rules

resource "oci_core_network_security_group_security_rule" "worker_egress_self" {
  network_security_group_id = oci_core_network_security_group.oke_worker.id
  direction                 = "EGRESS"
  protocol                  = "all"
  destination_type          = "NETWORK_SECURITY_GROUP"
  destination               = oci_core_network_security_group.oke_worker.id
  description               = "Allows intra-cluster pod and VXLAN communication"
}

resource "oci_core_network_security_group_security_rule" "worker_egress_control_plane_6443" {
  network_security_group_id = oci_core_network_security_group.oke_worker.id
  direction                 = "EGRESS"
  protocol                  = "6"
  destination_type          = "NETWORK_SECURITY_GROUP"
  destination               = oci_core_network_security_group.oke_control_plane.id
  description               = "Kubernetes worker to Kubernetes API endpoint communication"

  tcp_options {
    destination_port_range {
      min = 6443
      max = 6443
    }
  }
}

resource "oci_core_network_security_group_security_rule" "worker_egress_control_plane_12250" {
  network_security_group_id = oci_core_network_security_group.oke_worker.id
  direction                 = "EGRESS"
  protocol                  = "6"
  destination_type          = "NETWORK_SECURITY_GROUP"
  destination               = oci_core_network_security_group.oke_control_plane.id
  description               = "Kubernetes worker to OKE agent communication"

  tcp_options {
    destination_port_range {
      min = 12250
      max = 12250
    }
  }
}

resource "oci_core_network_security_group_security_rule" "worker_egress_internet" {
  network_security_group_id = oci_core_network_security_group.oke_worker.id
  direction                 = "EGRESS"
  protocol                  = "all"
  destination_type          = "CIDR_BLOCK"
  destination               = "0.0.0.0/0"
  description               = "Allow worker nodes outbound access to the internet"
}

resource "oci_core_network_security_group_security_rule" "worker_egress_osn" {
  network_security_group_id = oci_core_network_security_group.oke_worker.id
  direction                 = "EGRESS"
  protocol                  = "6"
  destination_type          = "SERVICE_CIDR_BLOCK"
  destination               = data.oci_core_services.all_services.services[0].cidr_block
  description               = "Allow worker nodes to access Oracle Cloud services (OCIR, Vault)"

  tcp_options {
    destination_port_range {
      min = 443
      max = 443
    }
  }
}

resource "oci_core_network_security_group_security_rule" "worker_egress_icmp" {
  network_security_group_id = oci_core_network_security_group.oke_worker.id
  direction                 = "EGRESS"
  protocol                  = "1"
  destination_type          = "CIDR_BLOCK"
  destination               = "0.0.0.0/0"
  description               = "Path MTU Discovery"

  icmp_options {
    type = 3
    code = 4
  }
}

resource "oci_core_network_security_group_security_rule" "worker_egress_database_3306" {
  network_security_group_id = oci_core_network_security_group.oke_worker.id
  direction                 = "EGRESS"
  protocol                  = "6"
  destination_type          = "NETWORK_SECURITY_GROUP"
  destination               = oci_core_network_security_group.database.id
  description               = "Allow worker nodes outbound communication to MySQL database"

  tcp_options {
    destination_port_range {
      min = 3306
      max = 3306
    }
  }
}

# Kubernetes Load Balancer Ingress & Egress Rules

resource "oci_core_network_security_group_security_rule" "lb_ingress_http" {
  network_security_group_id = oci_core_network_security_group.oke_load_balancer.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source_type               = "CIDR_BLOCK"
  source                    = "0.0.0.0/0"
  description               = "Allow inbound HTTP from internet"

  tcp_options {
    destination_port_range {
      min = 80
      max = 80
    }
  }
}

resource "oci_core_network_security_group_security_rule" "lb_ingress_https" {
  network_security_group_id = oci_core_network_security_group.oke_load_balancer.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source_type               = "CIDR_BLOCK"
  source                    = "0.0.0.0/0"
  description               = "Allow inbound HTTPS from internet"

  tcp_options {
    destination_port_range {
      min = 443
      max = 443
    }
  }
}

resource "oci_core_network_security_group_security_rule" "lb_egress_worker_nodeports" {
  network_security_group_id = oci_core_network_security_group.oke_load_balancer.id
  direction                 = "EGRESS"
  protocol                  = "6"
  destination_type          = "NETWORK_SECURITY_GROUP"
  destination               = oci_core_network_security_group.oke_worker.id
  description               = "Route traffic to Traefik NodePorts on workers"

  tcp_options {
    destination_port_range {
      min = 30000
      max = 32767
    }
  }
}

resource "oci_core_network_security_group_security_rule" "lb_egress_worker_healthcheck" {
  network_security_group_id = oci_core_network_security_group.oke_load_balancer.id
  direction                 = "EGRESS"
  protocol                  = "6"
  destination_type          = "NETWORK_SECURITY_GROUP"
  destination               = oci_core_network_security_group.oke_worker.id
  description               = "Health checks to worker nodes"

  tcp_options {
    destination_port_range {
      min = 10256
      max = 10256
    }
  }
}

### Database Network Security Group Rules ###

resource "oci_core_network_security_group_security_rule" "database_ingress_workers_3306" {
  network_security_group_id = oci_core_network_security_group.database.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source_type               = "NETWORK_SECURITY_GROUP"
  source                    = oci_core_network_security_group.oke_worker.id
  description               = "Allow OKE worker nodes to connect to MySQL database"

  tcp_options {
    destination_port_range {
      min = 3306
      max = 3306
    }
  }
}

resource "oci_core_network_security_group_security_rule" "database_ingress_bastion_3306" {
  network_security_group_id = oci_core_network_security_group.database.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source_type               = "CIDR_BLOCK"
  source                    = local.bastion_subnet_cidr
  description               = "Allow Bastion to connect to MySQL database"

  tcp_options {
    destination_port_range {
      min = 3306
      max = 3306
    }
  }
}

resource "oci_core_network_security_group_security_rule" "database_ingress_icmp" {
  network_security_group_id = oci_core_network_security_group.database.id
  direction                 = "INGRESS"
  protocol                  = "1"
  source_type               = "CIDR_BLOCK"
  source                    = local.main_vcn_cidr
  description               = "Path MTU discovery within VCN"

  icmp_options {
    type = 3
    code = 4
  }
}

resource "oci_core_network_security_group_security_rule" "database_egress_osn" {
  network_security_group_id = oci_core_network_security_group.database.id
  direction                 = "EGRESS"
  protocol                  = "6"
  destination_type          = "SERVICE_CIDR_BLOCK"
  destination               = data.oci_core_services.all_services.services[0].cidr_block
  description               = "Allow database outbound communication to Oracle Services Network"

  tcp_options {
    destination_port_range {
      min = 443
      max = 443
    }
  }
}

### Management Network Security Group Rules ###

resource "oci_core_network_security_group_security_rule" "management_egress_control_plane_6443" {
  network_security_group_id = oci_core_network_security_group.management.id
  direction                 = "EGRESS"
  protocol                  = "6"
  destination_type          = "NETWORK_SECURITY_GROUP"
  destination               = oci_core_network_security_group.oke_control_plane.id
  description               = "Allow outbound communication to Kubernetes API control plane"

  tcp_options {
    destination_port_range {
      min = 6443
      max = 6443
    }
  }
}

resource "oci_core_network_security_group_security_rule" "management_egress_osn" {
  network_security_group_id = oci_core_network_security_group.management.id
  direction                 = "EGRESS"
  protocol                  = "6"
  destination_type          = "SERVICE_CIDR_BLOCK"
  destination               = data.oci_core_services.all_services.services[0].cidr_block
  description               = "Allow management endpoint outbound communication to Oracle Services Network"

  tcp_options {
    destination_port_range {
      min = 443
      max = 443
    }
  }
}

resource "oci_core_network_security_group_security_rule" "management_egress_icmp" {
  network_security_group_id = oci_core_network_security_group.management.id
  direction                 = "EGRESS"
  protocol                  = "1"
  destination_type          = "CIDR_BLOCK"
  destination               = local.main_vcn_cidr
  description               = "Path MTU discovery within VCN"

  icmp_options {
    type = 3
    code = 4
  }
}
