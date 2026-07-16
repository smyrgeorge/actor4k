FROM eclipse-temurin:21-jre
COPY microbank/build/libs/microbank.jar microbank.jar
ENTRYPOINT ["java","-jar","/microbank.jar"]