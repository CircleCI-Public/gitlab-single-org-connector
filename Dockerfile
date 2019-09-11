FROM openjdk:11-slim

ENV PORT=8080
ENV ADMIN_PORT=8081
ARG JAR
ARG DEFAULT_CONFIG
COPY ${JAR} /service.jar
COPY ${DEFAULT_CONFIG} /default_config.yml

RUN useradd --groups users \
            --home-dir / \
            --shell /bin/bash \
            service-user

USER service-user
EXPOSE $PORT
EXPOSE $ADMIN_PORT
ENTRYPOINT ["java", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "-jar", "/service.jar"]
CMD ["server", "/default_config.yml"]
