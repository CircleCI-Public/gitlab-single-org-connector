FROM openjdk:11-slim

ARG JAR
COPY ${JAR} /service.jar

RUN useradd --groups users \
            --home-dir / \
            --shell /bin/bash \
            service-user

USER service-user
EXPOSE 8080
EXPOSE 8081
ENTRYPOINT ["java", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "-jar", "/service.jar"]
CMD ["server"]
