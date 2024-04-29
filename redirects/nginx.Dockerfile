FROM nginx:alpine

RUN apk add --no-cache openssl

COPY ./docker/nginx/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

RUN chmod +x /usr/local/bin/docker-entrypoint.sh \
    && mkdir -p /etc/nginx/html \
    && touch /etc/nginx/html/index.html


ENTRYPOINT ["docker-entrypoint.sh"]