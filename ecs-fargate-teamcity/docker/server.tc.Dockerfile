FROM jetbrains/teamcity-server:2023.11.4

USER root

COPY --chown=tcuser:tcuser ./docker/server/server.xml /opt/teamcity/conf/server.xml
COPY --chown=tcuser:tcuser ./docker/server/docker-entrypoint.sh /opt/teamcity/docker-entrypoint.sh

CMD ["/opt/teamcity/docker-entrypoint.sh"]