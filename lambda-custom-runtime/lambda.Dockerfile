ARG FUNCTION_DIR="/function"

FROM public.ecr.aws/docker/library/node:20.11.0-bookworm as build-image

# Include global arg in this stage of the build
ARG FUNCTION_DIR

## Install aws-lambda-cpp build dependencies
RUN apt-get update && \
    apt-get install -y \
    g++ \
    make \
    cmake \
    unzip \
    libcurl4-openssl-dev

# Copy function code
RUN mkdir -p ${FUNCTION_DIR}

WORKDIR ${FUNCTION_DIR}
#
# If the dependency is not in package.json uncomment the following line
RUN npm install aws-lambda-ric

COPY ./main.js ${FUNCTION_DIR}

# Grab a fresh slim copy of the image to reduce the final size
FROM public.ecr.aws/docker/library/node:20.11.0-bookworm-slim

# Required for Node runtimes which use npm@8.6.0+ because
# by default npm writes logs under /home/.npm and Lambda fs is read-only
ENV NPM_CONFIG_CACHE=/tmp/.npm

# Include global arg in this stage of the build
ARG FUNCTION_DIR

# Set working directory to function root directory
WORKDIR ${FUNCTION_DIR}

# Copy in the built dependencies
COPY --from=build-image ${FUNCTION_DIR} ${FUNCTION_DIR}

ENTRYPOINT ["/usr/local/bin/npx", "aws-lambda-ric"]
CMD ["main.handler"]