FROM jenkins/jenkins:lts-jdk21

USER root

# Basis-Pakete + Docker-CLI (Docker-outside-of-Docker: der Daemon kommt vom Host
# via /var/run/docker.sock; im Container wird nur die CLI gebraucht).
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
        curl ca-certificates gnupg lsb-release \
 && install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/debian/gpg \
      | gpg --dearmor -o /etc/apt/keyrings/docker.gpg \
 && chmod a+r /etc/apt/keyrings/docker.gpg \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
      https://download.docker.com/linux/debian $(lsb_release -cs) stable" \
      > /etc/apt/sources.list.d/docker.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends docker-ce-cli docker-buildx-plugin \
 && rm -rf /var/lib/apt/lists/*

# 'docker'-Gruppe anlegen (GID 999 — wird in docker-compose über group_add ggf.
# auf die GID des Host-Sockets gemappt) und jenkins-User dort aufnehmen.
RUN groupadd -g 999 docker || groupmod -n docker $(getent group 999 | cut -d: -f1) \
 && usermod -aG docker jenkins

USER jenkins

COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt

# Geo-redirects to the closest mirror are unreliable in some networks (TLS-handshake
# failures). archives.jenkins.io is an S3-backed mirror that's reliably reachable.
ENV JENKINS_UC=https://updates.jenkins.io \
    JENKINS_UC_DOWNLOAD=https://archives.jenkins.io
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
