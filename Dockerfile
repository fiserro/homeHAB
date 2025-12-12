FROM openhab/openhab:5.0.0-debian

# Install Temurin JDK 21 for Java223 script compilation
# Debian Bookworm only has OpenJDK 17, so we use Temurin from Adoptium
USER root
RUN apt-get update && \
    apt-get install -y temurin-21-jdk && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    # Remove JRE directory to avoid entrypoint script confusion (JDK contains JRE)
    rm -rf /usr/lib/jvm/temurin-21-jre-arm64

# Don't switch to user 9001 here - let entrypoint handle user creation
# The entrypoint will switch to openhab user after initialization
