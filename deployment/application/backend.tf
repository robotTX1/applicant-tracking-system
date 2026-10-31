### Docker Hub Secret Lookup from Vault ###

data "oci_vault_secrets" "dockerhub_token" {
  compartment_id = data.oci_identity_compartments.security.compartments[0].id
  name           = local.dockerhub_vault_secret_name
  state          = "ACTIVE"
}

data "oci_secrets_secretbundle" "dockerhub_token" {
  secret_id = data.oci_vault_secrets.dockerhub_token.secrets[0].id
}

locals {
  backend_release_name = "applicant-tracking-system-backend"
  backend_namespace    = "ats-backend"
  backend_image_name   = "docker.io/robottx/applicant-tracking-system-backend"

  dockerhub_vault_secret_name = "dockerhub-token"
  dockerhub_k8s_secret_name   = "dockerhub-token"
  dockerhub_username          = "robottx"

  backend_db_name = "applicant_tracking_system"
  backend_db_url  = "jdbc:mysql://${local.mysql_ip}:${local.mysql_port}/${local.backend_db_name}"

  gemini_system_prompt = replace(trimspace(<<-EOF
    You are an expert technical recruiter and HR specialist evaluating job applications.
    Evaluate the candidate's attached CV and applicant message against the job listing title
    and description. Provide a fair, objective score between 0 and 100 and a concise review
    summary (maximum 2000 characters).
  EOF
  ), "\n", " ")

  gemini_user_prompt = replace(trimspace(<<-EOF
    Job Title: {jobTitle}
    Job Description:
    {jobDescription}

    Applicant Message:
    {applicantMessage}

    Please evaluate the attached CV and applicant message for this job listing.
  EOF
  ), "\n", "\\n")

  backend_config = {
    "service.properties" = {
      "service-config.databaseUrl"                  = local.backend_db_url
      "service-config.authorizationServerJwksUri"   = "https://auth.robottx.hu/realms/ats/protocol/openid-connect/certs"
      "service-config.authorizationServerIssuerUri" = "https://auth.robottx.hu/realms/ats"
    }
    "vault.properties" = {
      "vault-config.vaultOcid"              = local.vault_ocid
      "vault-config.vaultRegion"            = var.region
      "vault-config.dbUsernameSecretName"   = "mysql-ats-username"
      "vault-config.dbPasswordSecretName"   = "mysql-ats-password"
      "vault-config.geminiApiKeySecretName" = "ats-gemini-api-key"
      "vault-config.mailUsernameSecretName" = "brevo-ats-username"
      "vault-config.mailPasswordSecretName" = "brevo-ats-smtp-key"
    }
    "storage.properties" = {
      "oci-storage-config.bucketName" = local.bucket_name
      "oci-storage-config.namespace"  = local.bucket_namespace
      "oci-storage-config.region"     = var.region
    }
    "cors.properties" = {
      "cors-config.allowedOriginPatterns" = "http://localhost:4200,https://ats.robottx.hu"
      "cors-config.allowedMethods"        = "GET,POST,PUT,PATCH,DELETE,OPTIONS,HEAD"
      "cors-config.allowedHeaders"        = "*"
      "cors-config.exposedHeaders"        = "Location,Content-Disposition,Content-Length"
      "cors-config.allowCredentials"      = "true"
      "cors-config.maxAge"                = "3600"
    }
    "mail.properties" = {
      "mail-config.host"           = var.backend_mail_host
      "mail-config.port"           = "587"
      "mail-config.from"           = "no-reply@robottx.hu"
      "mail-config.protocol"       = "smtp"
      "mail-config.auth"           = true
      "mail-config.starttlsEnable" = true
    }
    "gemini.properties" = {
      # Model & Generation Options
      "gemini-config.model"           = "gemma-4-31b-it"
      "gemini-config.temperature"     = "0.2"
      "gemini-config.maxOutputTokens" = "2048"

      # Prompts
      "gemini-config.systemPrompt" = local.gemini_system_prompt
      "gemini-config.userPrompt"   = local.gemini_user_prompt

      # Scheduled Job & ShedLock
      "gemini-config.reviewJobCron"           = "0 */1 * * * *"
      "gemini-config.reviewJobLockAtMostFor"  = "PT10M"
      "gemini-config.reviewJobLockAtLeastFor" = "PT10S"
      "gemini-config.batchSize"               = "3"

      # Resilience4j RateLimiter (Gemini Free Tier)
      "gemini-config.rateLimiterLimitForPeriod"     = "3"
      "gemini-config.rateLimiterLimitRefreshPeriod" = "PT1M"
      "gemini-config.rateLimiterTimeoutDuration"    = "PT10S"

      # Resilience4j Retry
      "gemini-config.retryMaxAttempts"  = "3"
      "gemini-config.retryWaitDuration" = "PT2S"

      # Resilience4j CircuitBreaker
      "gemini-config.circuitBreakerFailureRateThreshold"                  = "50.0"
      "gemini-config.circuitBreakerSlidingWindowSize"                     = "10"
      "gemini-config.circuitBreakerMinimumNumberOfCalls"                  = "5"
      "gemini-config.circuitBreakerWaitDurationInOpenState"               = "PT1M"
      "gemini-config.circuitBreakerPermittedNumberOfCallsInHalfOpenState" = "2"
    }
  }
}

### Kubernetes Namespace ###

resource "kubernetes_namespace_v1" "ats" {
  metadata {
    name = local.backend_namespace
  }
}

### Docker Hub Image Pull Secret ###

resource "kubernetes_secret_v1" "dockerhub_token" {
  metadata {
    name      = local.dockerhub_k8s_secret_name
    namespace = kubernetes_namespace_v1.ats.metadata[0].name
  }

  type = "kubernetes.io/dockerconfigjson"

  data = {
    ".dockerconfigjson" = jsonencode({
      auths = {
        "https://index.docker.io/v1/" = {
          username = local.dockerhub_username
          password = base64decode(data.oci_secrets_secretbundle.dockerhub_token.secret_bundle_content[0].content)
          auth     = base64encode("${local.dockerhub_username}:${base64decode(data.oci_secrets_secretbundle.dockerhub_token.secret_bundle_content[0].content)}")
        }
      }
    })
  }
}

### Applicant Tracking System Backend Helm Release ###

resource "helm_release" "ats_backend" {
  name             = local.backend_release_name
  repository       = "oci://ghcr.io/robottx1/charts"
  chart            = "applicant-tracking-system"
  version          = var.backend_chart_version
  namespace        = kubernetes_namespace_v1.ats.metadata[0].name
  create_namespace = false
  cleanup_on_fail  = true
  timeout          = 600

  values = [
    yamlencode({
      replicaCount         = var.backend_replicas
      revisionHistoryLimit = 2

      image = {
        name       = local.backend_image_name
        version    = var.backend_image_version
        pullPolicy = "IfNotPresent"
      }

      podSecurityContext = {
        runAsUser  = 10001
        runAsGroup = 10001
        fsGroup    = 10001
      }

      imagePullSecrets = [
        kubernetes_secret_v1.dockerhub_token.metadata[0].name
      ]

      resources = {
        requests = {
          cpu    = "100m"
          memory = "512Mi"
        }
        limits = {
          cpu    = "500m"
          memory = "1Gi"
        }
      }

      config = local.backend_config

      ingressRoute = {
        hosts         = ["ats.robottx.hu", "www.ats.robottx.hu"]
        pathPrefix    = "/api/v1"
        tlsSecretName = "ats-backend-production-tls-certificate"
      }

      certificate = {
        dnsNames = ["ats.robottx.hu", "www.ats.robottx.hu"]
      }
    })
  ]

  depends_on = [
    kubernetes_secret_v1.dockerhub_token,
    helm_release.traefik,
    helm_release.cert_manager,
    kubernetes_manifest.letsencrypt_cloudflare_production,
    helm_release.keycloak
  ]
}
