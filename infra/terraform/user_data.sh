#!/bin/bash
set -euo pipefail

dnf install -y docker
systemctl enable --now docker
systemctl enable --now amazon-ssm-agent

install -d -m 0755 /usr/local/lib/docker/cli-plugins
curl --fail --location --silent --show-error \
  "https://github.com/docker/compose/releases/download/v2.32.4/docker-compose-linux-x86_64" \
  --output /usr/local/lib/docker/cli-plugins/docker-compose
chmod 0755 /usr/local/lib/docker/cli-plugins/docker-compose
usermod -aG docker ec2-user
