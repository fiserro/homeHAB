FROM openhab/openhab:5.0.3-debian

# Install Temurin JDK 21 for Java223 script compilation
# Remove OpenJDK from base image to avoid JAVA_HOME conflicts
# Debian Trixie needs Adoptium repository added first
USER root
RUN apt-get update && \
    apt-get install -y wget apt-transport-https gnupg && \
    wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /usr/share/keyrings/adoptium.gpg > /dev/null && \
    echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list && \
    apt-get update && \
    apt-get remove -y openjdk-* && \
    apt-get install -y temurin-21-jdk && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    rm -rf /usr/lib/jvm/java-*-openjdk-* /usr/lib/jvm/temurin-21-jre-*

# Don't switch to user 9001 here - let entrypoint handle user creation
# The entrypoint will switch to openhab user after initialization
