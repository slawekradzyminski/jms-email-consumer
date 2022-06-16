## Direct run

```commandline
./mvnw clean package spring-boot:repackage
java -jar target/consumer-1.0.0.jar
```

## Docker local run

```commandline
./mvnw clean package spring-boot:repackage
docker build --tag=consumer:latest .
docker run -p4002:4002 consumer:latest
```

