FROM jenkins/jenkins:lts-jdk21

USER root
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
USER jenkins

COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt

# Geo-redirects to the closest mirror are unreliable in some networks (TLS-handshake
# failures). archives.jenkins.io is an S3-backed mirror that's reliably reachable.
ENV JENKINS_UC=https://updates.jenkins.io \
    JENKINS_UC_DOWNLOAD=https://archives.jenkins.io
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
