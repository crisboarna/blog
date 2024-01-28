FROM public.ecr.aws/docker/library/node:20.11.0-bookworm

# Install AWS Lambda RIE
RUN apt-get update \
    && export DEBIAN_FRONTEND=noninteractive \
    && apt-get -y install --no-install-recommends curl ca-certificates \
    && apt-get autoremove -y && apt-get clean -y && rm -rf /var/lib/apt/lists/* \
    && mkdir /rie-bin \
    && if [ "$(uname -m)" = "x86_64" ]; then \
        curl -Lo aws-lambda-rie https://github.com/aws/aws-lambda-runtime-interface-emulator/releases/download/v1.15/aws-lambda-rie \
        && chmod +x aws-lambda-rie \
        && mv aws-lambda-rie /rie-bin/; \
    elif [ "$(uname -m)" = "aarch64" ]; then \
        curl -Lo aws-lambda-rie https://github.com/aws/aws-lambda-runtime-interface-emulator/releases/download/v1.15/aws-lambda-rie-arm64 \
        && chmod +x aws-lambda-rie \
        && mv aws-lambda-rie /rie-bin/; \
    fi

ENTRYPOINT [ "/rie-bin/aws-lambda-rie" ]
