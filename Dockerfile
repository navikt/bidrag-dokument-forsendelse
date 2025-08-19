FROM gcr.io/distroless/java21
LABEL maintainer="Team Bidrag" \
      email="bidrag@nav.no"

COPY --from=busybox:1.35.0-glibc /bin/sh /bin/sh
COPY --from=busybox:1.35.0-glibc /bin/printenv /bin/printenv
WORKDIR /app

COPY ./target/bidrag-dokument-forsendelse-*.jar app.jar

EXPOSE 8080
ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"
ENV SPRING_PROFILES_ACTIVE=nais

CMD ["app.jar"]