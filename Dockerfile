FROM gcr.io/distroless/java:8-debug AS build

WORKDIR /src
COPY . .

RUN ["ln", "-s", "/busybox/env", "/usr/bin/env"]

RUN ["./gradlew", "--console=plain", "--no-daemon", "installDist"]

FROM gcr.io/cloudshell-images/cloudshell:latest
COPY --from=build /src/build/install/one-off-cloud-run/bin/ /bin/
COPY --from=build /src/build/install/one-off-cloud-run/lib/ /lib/

RUN ["gcloud", "config", "set", "survey/disable_prompts", "True"]

RUN ["ln", "-s", "/bin/one-off-cloud-run", "/bin/cloudshell_open"]
