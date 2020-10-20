#       Copyright 2017-2020 IBM Corp All Rights Reserved

#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at

#       http://www.apache.org/licenses/LICENSE-2.0

#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

FROM alpine:latest AS cert-extractor
ARG keycloak_connection_string
ARG extract_keycloak_cert
RUN echo "Extract cert: '$extract_keycloak_cert' - Connection string: '$keycloak_connection_string'" && touch keycloak.pem
RUN if [ "$extract_keycloak_cert" = "true" ]; then apk add openssl && openssl s_client -showcerts -connect ${keycloak_connection_string} </dev/null 2>/dev/null|openssl x509 -outform PEM > keycloak.pem ; fi

FROM maven:3.6-jdk-11-slim AS build
COPY . /usr/
RUN mvn -f /usr/pom.xml clean package

FROM openliberty/open-liberty:kernel-java11-openj9-ubi
ARG extract_keycloak_cert
ENV OPENJ9_SCC=false
USER root

# Following line is a workaround for an issue where sometimes the server somehow loads the built-in server.xml,
# rather than the one I copy into the image.  That shouldn't be possible, but alas, it appears to be some Docker bug.
RUN rm /opt/ol/wlp/usr/servers/defaultServer/server.xml

COPY src/main/liberty/config /opt/ol/wlp/usr/servers/defaultServer/
COPY --from=build /usr/target/trader-1.0-SNAPSHOT.war /opt/ol/wlp/usr/servers/defaultServer/apps/TraderUI.war
COPY --from=cert-extractor /keycloak.pem /tmp/keycloak.pem
RUN chown -R 1001:0 /opt/ol/wlp/usr/servers/defaultServer/

USER 1001
RUN if [ "$extract_keycloak_cert" = "true" ]; then keytool -import -v -trustcacerts -alias keycloak -file /tmp/keycloak.pem -keystore /opt/ol/wlp/usr/servers/defaultServer/resources/security/trust.p12 --noprompt --storepass St0ckTr@der ; fi
USER root
RUN chmod 777 /opt/ol/wlp/usr/servers/defaultServer
RUN yum -y install shadow-utils
RUN groupadd -g 1000590000 appgrp && useradd -l -r -d /home/appuser -u 1000590000 -g appgrp appuser && chown -R appuser:appgrp /opt/ol/wlp && chown -R appuser:appgrp /logs
USER appuser
COPY ibm-cloud-apm-dc-configpack.tar /opt/
COPY javametrics.liberty.icam-1.2.1.esa /opt/
RUN mkdir -p /opt/ol/wlp/usr/extension/lib/features/
RUN cd /tmp && jar xvf /opt/javametrics.liberty.icam-1.2.1.esa && mv /tmp/wlp/liberty_dc /opt/ol/wlp/usr/extension/ && mv /tmp/OSGI-INF/SUBSYSTEM.MF /opt/ol/wlp/usr/extension/lib/features/javametrics.liberty.icam-1.2.1.mf
COPY silent_config_liberty_dc.txt /opt/ol/wlp/usr/extension/liberty_dc/bin/
USER root
RUN chmod 777 /opt/ol/wlp/usr/extension/*
RUN chmod 777 /opt/ol/wlp/usr/extension/lib/*
RUN chmod 777 /opt/ol/wlp/usr/extension/liberty_dc/*
RUN chmod 777 /opt/ol/wlp/usr/extension/liberty_dc/bin/*
USER appuser
RUN /opt/ol/wlp/usr/extension/liberty_dc/bin/config_unified_dc.sh -silent
RUN configure.sh
