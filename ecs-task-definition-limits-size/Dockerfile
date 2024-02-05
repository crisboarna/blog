# Use a minimal image to run the entrypoint
FROM alpine:3.19.1
# Install bash
RUN apk add --no-cache bash
# Copy the entrypoint script
COPY docker-entrypoint.sh /usr/local/bin/
# Make the entrypoint script executable
RUN chmod +x /usr/local/bin/docker-entrypoint.sh
# Set the entrypoint script as the entrypoint for the image
ENTRYPOINT ["docker-entrypoint.sh"]