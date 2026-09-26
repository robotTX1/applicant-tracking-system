### Kubernetes Metrics Server ###

resource "helm_release" "metrics_server" {
  name             = "metrics-server"
  repository       = "https://kubernetes-sigs.github.io/metrics-server/"
  chart            = "metrics-server"
  version          = var.metrics_server_chart_version
  namespace        = "kube-system"
  create_namespace = false
  cleanup_on_fail  = true
  timeout          = 300

  values = [
    yamlencode({
      replicas = var.metrics_server_replicas

      args = [
        "--kubelet-insecure-tls"
      ]

      resources = {
        requests = {
          cpu    = "100m"
          memory = "200Mi"
        }
      }
    })
  ]
}
