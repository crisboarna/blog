#!/bin/sh

echo "$NGINX_CONF" | base64 -d > /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'