FROM alpine

WORKDIR /src
COPY . .

RUN apk --update add postgresql-client

ENTRYPOINT while :; do nc -k -l -p $PORT -e sh -c 'echo -e "HTTP/1.1 200 OK\n\n hello, world"'; done
