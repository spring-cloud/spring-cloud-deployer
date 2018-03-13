FROM java:8-alpine

ARG JAR_FILE

ADD target/${JAR_FILE} spring-cloud-deployer-spi-test-app.jar

ENTRYPOINT exec java $JAVA_OPTS -jar /spring-cloud-deployer-spi-test-app.jar
