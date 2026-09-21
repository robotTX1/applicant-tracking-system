resource "oci_containerengine_cluster" "main" {
  compartment_id     = oci_identity_compartment.workloads.id
  kubernetes_version = var.oke_kubernetes_version
  type               = "BASIC_CLUSTER"
  name               = "main-cluster"
  vcn_id             = oci_core_vcn.main.id
  defined_tags       = local.default_tags

  cluster_pod_network_options {
    cni_type = "FLANNEL_OVERLAY"
  }

  endpoint_config {
    is_public_ip_enabled = false
    nsg_ids              = [oci_core_network_security_group.oke_control_plane.id]
    subnet_id            = oci_core_subnet.oke_api.id
  }

  kms_key_id = oci_kms_key.main.id

  options {
    service_lb_subnet_ids = [oci_core_subnet.oke_load_balancer.id]

    add_ons {
      is_kubernetes_dashboard_enabled = false
      is_tiller_enabled               = false
    }
  }

}

data "oci_identity_availability_domains" "domains" {
  compartment_id = var.tenancy_ocid
}

locals {
  ad_pools = {
    "ad-1" = {
      ad_name = data.oci_identity_availability_domains.domains.availability_domains[0].name
      size    = var.oke_node_counts_by_ad["ad-1"]
    }
    "ad-2" = {
      ad_name = data.oci_identity_availability_domains.domains.availability_domains[1].name
      size    = var.oke_node_counts_by_ad["ad-2"]
    }
    "ad-3" = {
      ad_name = data.oci_identity_availability_domains.domains.availability_domains[2].name
      size    = var.oke_node_counts_by_ad["ad-3"]
    }
  }
}

resource "oci_containerengine_node_pool" "pools" {
  for_each = local.ad_pools

  cluster_id         = oci_containerengine_cluster.main.id
  compartment_id     = oci_identity_compartment.workloads.id
  name               = "worker-pool-${each.key}"
  node_shape         = var.oke_node_shape
  kubernetes_version = oci_containerengine_cluster.main.kubernetes_version
  defined_tags       = local.default_tags

  node_shape_config {
    ocpus         = var.oke_node_ocpus
    memory_in_gbs = var.oke_node_memory_in_gbs
  }

  node_config_details {
    size    = each.value.size
    nsg_ids = [oci_core_network_security_group.oke_worker.id]

    placement_configs {
      availability_domain = each.value.ad_name
      subnet_id           = oci_core_subnet.oke_worker.id
    }
  }

  node_source_details {
    source_type             = "IMAGE"
    image_id                = var.oke_image_ocid
    boot_volume_size_in_gbs = var.oke_node_volume_size_in_gbs
  }

}
