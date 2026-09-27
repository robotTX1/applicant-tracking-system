locals {
  cert_manager_namespace = "cert-manager"
}

resource "helm_release" "cert_manager" {
  name             = "cert-manager"
  repository       = "oci://quay.io/jetstack/charts"
  chart            = "cert-manager"
  version          = var.cert_manager_version
  namespace        = local.cert_manager_namespace
  create_namespace = true

  set = [
    {
      name  = "crds.enabled"
      value = "true"
    }
  ]
}

### Cloudflare API Token from OCI Vault ###

data "oci_identity_compartments" "security" {
  compartment_id            = var.tenancy_ocid
  compartment_id_in_subtree = true
  filter {
    name   = "name"
    values = ["security"]
  }
}

data "oci_vault_secrets" "cloudflare_token" {
  compartment_id = data.oci_identity_compartments.security.compartments[0].id
  name           = var.cloudflare_vault_secret_name
}

data "oci_secrets_secretbundle" "cloudflare_token" {
  secret_id = data.oci_vault_secrets.cloudflare_token.secrets[0].id
}

### Kubernetes Secret for Cloudflare ###

resource "kubernetes_secret_v1" "cloudflare_api_token" {
  metadata {
    name      = var.cloudflare_k8s_secret_name
    namespace = local.cert_manager_namespace
  }

  data = {
    "api-token" = base64decode(data.oci_secrets_secretbundle.cloudflare_token.secret_bundle_content[0].content)
  }

  depends_on = [helm_release.cert_manager]
}

### Let's Encrypt ClusterIssuer ###

resource "kubernetes_manifest" "letsencrypt_cloudflare_production" {
  manifest = {
    apiVersion = "cert-manager.io/v1"
    kind       = "ClusterIssuer"
    metadata = {
      name = "letsencrypt-cloudflare-production"
    }
    spec = {
      acme = {
        server = "https://acme-v02.api.letsencrypt.org/directory"
        email  = var.acme_email
        privateKeySecretRef = {
          name = "letsencrypt-secret"
        }
        solvers = [
          {
            dns01 = {
              cloudflare = {
                apiTokenSecretRef = {
                  name = kubernetes_secret_v1.cloudflare_api_token.metadata[0].name
                  key  = "api-token"
                }
              }
            }
          }
        ]
      }
    }
  }

  depends_on = [
    helm_release.cert_manager,
    kubernetes_secret_v1.cloudflare_api_token
  ]
}