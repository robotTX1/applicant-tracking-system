### MySQL Database Lookup ###

data "oci_mysql_mysql_db_systems" "main" {
  compartment_id = data.oci_identity_compartments.workloads.compartments[0].id
  state          = "ACTIVE"
}

locals {
  mysql_ip           = data.oci_mysql_mysql_db_systems.main.db_systems[0].ip_address
  mysql_port         = data.oci_mysql_mysql_db_systems.main.db_systems[0].port
  kc_db_url          = "jdbc:mysql://${local.mysql_ip}:${local.mysql_port}/${var.keycloak_db_name}"
  keycloak_namespace = "keycloak"
}

### Vault Secrets for Keycloak ###

data "oci_vault_secrets" "keycloak_admin_username" {
  compartment_id = data.oci_identity_compartments.security.compartments[0].id
  name           = var.keycloak_admin_username_secret_name
  state          = "ACTIVE"
}

data "oci_secrets_secretbundle" "keycloak_admin_username" {
  secret_id = data.oci_vault_secrets.keycloak_admin_username.secrets[0].id
}

data "oci_vault_secrets" "keycloak_admin_password" {
  compartment_id = data.oci_identity_compartments.security.compartments[0].id
  name           = var.keycloak_admin_password_secret_name
  state          = "ACTIVE"
}

data "oci_secrets_secretbundle" "keycloak_admin_password" {
  secret_id = data.oci_vault_secrets.keycloak_admin_password.secrets[0].id
}

data "oci_vault_secrets" "mysql_keycloak_username" {
  compartment_id = data.oci_identity_compartments.security.compartments[0].id
  name           = var.keycloak_db_username_secret_name
  state          = "ACTIVE"
}

data "oci_secrets_secretbundle" "mysql_keycloak_username" {
  secret_id = data.oci_vault_secrets.mysql_keycloak_username.secrets[0].id
}

data "oci_vault_secrets" "mysql_keycloak_password" {
  compartment_id = data.oci_identity_compartments.security.compartments[0].id
  name           = var.keycloak_db_password_secret_name
  state          = "ACTIVE"
}

data "oci_secrets_secretbundle" "mysql_keycloak_password" {
  secret_id = data.oci_vault_secrets.mysql_keycloak_password.secrets[0].id
}

### Kubernetes Namespace ###

resource "kubernetes_namespace_v1" "keycloak" {
  metadata {
    name = local.keycloak_namespace
  }
}

### Keycloak Config Secret ###

resource "kubernetes_secret_v1" "keycloak_config" {
  metadata {
    name      = "keycloak-config"
    namespace = kubernetes_namespace_v1.keycloak.metadata[0].name
  }

  type = "Opaque"

  data = {
    KEYCLOAK_ADMIN          = base64decode(data.oci_secrets_secretbundle.keycloak_admin_username.secret_bundle_content[0].content)
    KEYCLOAK_ADMIN_PASSWORD = base64decode(data.oci_secrets_secretbundle.keycloak_admin_password.secret_bundle_content[0].content)
    KC_HOSTNAME             = var.keycloak_hostname
    KC_DB_URL               = local.kc_db_url
    KC_DB_USERNAME          = base64decode(data.oci_secrets_secretbundle.mysql_keycloak_username.secret_bundle_content[0].content)
    KC_DB_PASSWORD          = base64decode(data.oci_secrets_secretbundle.mysql_keycloak_password.secret_bundle_content[0].content)
  }
}

### Cert-Manager Certificate ###

resource "kubernetes_manifest" "keycloak_certificate" {
  manifest = {
    apiVersion = "cert-manager.io/v1"
    kind       = "Certificate"
    metadata = {
      name      = "keycloak-production-certificate"
      namespace = kubernetes_namespace_v1.keycloak.metadata[0].name
    }
    spec = {
      secretName = "keycloak-production-tls-certificate"
      issuerRef = {
        name = "letsencrypt-cloudflare-production"
        kind = "ClusterIssuer"
      }
      dnsNames = var.keycloak_certificate_dns_names
    }
  }

  depends_on = [
    helm_release.cert_manager,
    kubernetes_manifest.letsencrypt_cloudflare_production
  ]
}

### Keycloak Helm Release ###

resource "helm_release" "keycloak" {
  name             = "keycloak"
  repository       = "https://codecentric.github.io/helm-charts"
  chart            = "keycloakx"
  version          = var.keycloak_chart_version
  namespace        = kubernetes_namespace_v1.keycloak.metadata[0].name
  create_namespace = false
  cleanup_on_fail  = true
  timeout          = 600

  values = [
    yamlencode({
      fullnameOverride = "keycloak"
      replicas         = var.keycloak_replicas

      command = ["/opt/keycloak/bin/kc.sh"]
      args    = ["start"]

      dbchecker = {
        enabled = false
      }

      database = {
        vendor = "mysql"
      }

      proxy = {
        enabled = true
        mode    = "xforwarded"
        http = {
          enabled = true
        }
      }

      http = {
        relativePath           = "/"
        managementRelativePath = "/"
      }

      health = {
        enabled = true
      }

      metrics = {
        enabled = true
      }

      resources = {
        requests = {
          cpu    = "500m"
          memory = "500Mi"
        }
        limits = {
          cpu    = "1000m"
          memory = "1000Mi"
        }
      }

      extraEnv = <<-EOT
        - name: KC_BOOTSTRAP_ADMIN_USERNAME
          valueFrom:
            secretKeyRef:
              name: keycloak-config
              key: KEYCLOAK_ADMIN
        - name: KC_BOOTSTRAP_ADMIN_PASSWORD
          valueFrom:
            secretKeyRef:
              name: keycloak-config
              key: KEYCLOAK_ADMIN_PASSWORD
        - name: KC_DB_URL
          valueFrom:
            secretKeyRef:
              name: keycloak-config
              key: KC_DB_URL
        - name: KC_DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: keycloak-config
              key: KC_DB_USERNAME
        - name: KC_DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: keycloak-config
              key: KC_DB_PASSWORD
        - name: KC_HOSTNAME
          valueFrom:
            secretKeyRef:
              name: keycloak-config
              key: KC_HOSTNAME
        - name: KC_HOSTNAME_STRICT
          value: "true"
        - name: POD_IP
          valueFrom:
            fieldRef:
              fieldPath: status.podIP
        - name: KC_CACHE_EMBEDDED_NETWORK_BIND_ADDRESS
          value: $(POD_IP)
      EOT
    })
  ]

  depends_on = [
    kubernetes_secret_v1.keycloak_config,
    helm_release.traefik
  ]
}

### Traefik IngressRoute for Keycloak ###

resource "kubernetes_manifest" "keycloak_ingress_route" {
  manifest = {
    apiVersion = "traefik.io/v1alpha1"
    kind       = "IngressRoute"
    metadata = {
      name      = "keycloak-ingress-route"
      namespace = kubernetes_namespace_v1.keycloak.metadata[0].name
      annotations = {
        "kubernetes.io/ingress.class" = "traefik-external"
      }
    }
    spec = {
      entryPoints = [
        "websecure"
      ]
      routes = [
        for host in var.keycloak_ingress_hosts : {
          match = "Host(`${host}`)"
          kind  = "Rule"
          services = [
            {
              name = "keycloak-http"
              port = 80
            }
          ]
        }
      ]
      tls = {
        secretName = "keycloak-production-tls-certificate"
      }
    }
  }

  depends_on = [
    helm_release.keycloak,
    kubernetes_manifest.keycloak_certificate
  ]
}
