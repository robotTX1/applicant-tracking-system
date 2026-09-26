#!/bin/bash

RESOURCE_MANAGER_STACK_ID="ocid1.ormstack.oc1.eu-frankfurt-1.amaaaaaabwqrreqata6gpoj2r3plguccomeetyajko5mxxvkcfsb7dcuol2q"

JOB_ID=$(oci resource-manager job create-apply-job --wait-for-state SUCCEEDED \
--wait-for-state FAILED --stack-id "$RESOURCE_MANAGER_STACK_ID" \
--execution-plan-strategy AUTO_APPROVED | jq -r ".data.id")

echo "Job ID: $JOB_ID"

oci resource-manager job get-job-logs-content --job-id "$JOB_ID" \
      | jq -r .data \
      | sed -E 's/^[0-9\/]+ [0-9:]+\[TERRAFORM_CONSOLE\] \[[A-Z]+\] //'