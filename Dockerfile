FROM gcr.io/distroless/java21-debian12:nonroot
LABEL maintainer="Team Bidrag" \
      email="bidrag@nav.no"

COPY --from=busybox /bin/sh /bin/sh
COPY --from=busybox /bin/printenv /bin/printenv
WORKDIR /app

COPY ./target/bidrag-dokument-forsendelse-*.jar app.jar

EXPOSE 8080
ENV TZ="Europe/Oslo"
ENV SPRING_PROFILES_ACTIVE=nais

CMD ["app.jar"]