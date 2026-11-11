# Build the executable JAR with Maven before building this runtime image.
FROM eclipse-temurin:21-jre
WORKDIR /opt/manabi
RUN mkdir -p /var/lib/manabi/publications && chown -R 10001:10001 /opt/manabi /var/lib/manabi
COPY --chown=10001:10001 target/manabi-api-0.0.1-SNAPSHOT.jar /opt/manabi/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","/opt/manabi/app.jar"]
