resource "helm_release" "cert_manager" {
  name             = "cert-manager"
  repository       = "oci://quay.io/jetstack/charts"
  chart            = "cert-manager"
  version          = var.cert_manager_version
  namespace        = "cert-manager"
  create_namespace = true

  set = [
    {
      name  = "crds.enabled"
      value = "true"
    }
  ]
}