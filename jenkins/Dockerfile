FROM jenkins/jenkins:lts

USER root

# Instalar Docker CLI
RUN apt-get update -qq && \
    apt-get install -y -qq ca-certificates curl gnupg && \
    install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg && \
    chmod a+r /etc/apt/keyrings/docker.gpg && \
    echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian bookworm stable" > /etc/apt/sources.list.d/docker.list && \
    apt-get update -qq && \
    apt-get install -y -qq docker-ce-cli && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Dar acceso al socket sin necesitar grupo
RUN chmod 666 /var/run/docker.sock 2>/dev/null || true

USER jenkins