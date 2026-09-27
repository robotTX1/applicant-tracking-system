### Load Balancer NSG ###

data "oci_core_network_security_groups" "oke_load_balancer" {
  compartment_id = data.oci_identity_compartments.network.compartments[0].id
  display_name   = "oke-load-balancer-nsg"
}

locals {
  traefik_load_balancer_nsg_id = var.traefik_load_balancer_nsg_id != null ? var.traefik_load_balancer_nsg_id : data.oci_core_network_security_groups.oke_load_balancer.network_security_groups[0].id
}

### Traefik Ingress Controller ###

resource "helm_release" "traefik" {
  name             = "traefik"
  repository       = "https://traefik.github.io/charts"
  chart            = "traefik"
  version          = var.traefik_chart_version
  namespace        = "traefik"
  create_namespace = true

  values = [
    yamlencode({
      global = {
        checkNewVersion    = false
        sendAnonymousUsage = false
      }

      log = {
        level = "INFO"
      }

      deployment = {
        enabled = true
        kind    = "DaemonSet"
        labels = {
          app = "traefik"
        }
        resources = {
          requests = {
            cpu    = "100m"
            memory = "100Mi"
          }
          limits = {
            cpu    = "250m"
            memory = "200Mi"
          }
        }
      }

      podDisruptionBudget = {
        enabled        = true
        maxUnavailable = 1
      }

      ports = {
        web = {
          http = {
            redirections = {
              entryPoint = {
                to        = "websecure"
                scheme    = "https"
                permanent = true
              }
            }
          }
        }
        websecure = {
          http = {
            tls = {
              enabled = true
            }
          }
          http3 = {
            enabled = true
          }
        }
      }

      ingressRoute = {
        dashboard = {
          enabled = false
        }
      }

      providers = {
        kubernetesCRD = {
          enabled      = true
          ingressClass = "traefik-external"
        }
        kubernetesIngress = {
          publishedService = {
            enabled = true
          }
        }
      }

      service = {
        enabled = true
        single  = true
        annotations = {
          "oci.oraclecloud.com/load-balancer-type"                                  = "nlb"
          "oci-network-load-balancer.oraclecloud.com/oci-network-security-groups"   = local.traefik_load_balancer_nsg_id
          "oci-network-load-balancer.oraclecloud.com/is-preserve-source"            = "true"
          "oci-network-load-balancer.oraclecloud.com/security-list-management-mode" = "None"
        }
        spec = {
          externalTrafficPolicy = "Local"
        }
      }
    })
  ]
}
