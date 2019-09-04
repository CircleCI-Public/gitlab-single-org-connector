FROM openjdk:8u222-jdk-slim

ARG JAR
COPY ${JAR} /service.jar

RUN useradd --groups users \
            --home-dir / \
            --shell /bin/bash \
            service-user

USER service-user
EXPOSE 8080
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/service.jar"]
CMD ["server"]
