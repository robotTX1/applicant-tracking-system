terraform {
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "9.2.0"
    }
  }
}

provider "oci" {
  ignore_defined_tags = ["Oracle-Tags.CreatedBy", "Oracle-Tags.CreatedOn"]
}
