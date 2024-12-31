ARG USER=vscode

# base variables for core dependency versions
# Based on https://docs.aws.amazon.com/emr/latest/ReleaseGuide/emr-750-release.html AWS EMR cluster version used
ARG SPARK_VERSION=3.5.2
ARG HADOOP_VERSION=3
ARG SCALA_VERSION=2.13

# as target EMR version for this is 7.5.0, the appropriate Python version is 3.9.
# you can install multiple Python versions using pyenv, or by following below Spark version configuration
ARG PYTHON_SPARK_VERSION=3.9.21

# base variables for Spark configuration
ARG SPARK_HOME=/opt/spark
ARG SPARK_MASTER_PORT=7077
ARG SPARK_MASTER_WEBUI_PORT=8080
ARG SPARK_WORKER_PORT=7000
ARG SPARK_WORKER_WEBUI_PORT=8080

# base variables for PySpark configuration
ARG PYSPARK_PYTHON=python
ARG PYSPARK_DRIVER_PYTHON=python

# base variables for pyenv configuration
ARG PYENV_ROOT=/home/${USER}/.pyenv

# base image, as EMR 7.5.0 has Java 17 as default, we are using the same version
FROM mcr.microsoft.com/devcontainers/java:1.1.17-17-jdk-bookworm AS base

ARG USER

ARG SPARK_VERSION
ARG HADOOP_VERSION
ARG SCALA_VERSION
ARG PYTHON_SPARK_VERSION

ARG SPARK_HOME
ARG SPARK_MASTER_PORT
ARG SPARK_MASTER_WEBUI_PORT
ARG SPARK_WORKER_PORT
ARG SPARK_WORKER_WEBUI_PORT

ARG PYSPARK_PYTHON=python
ARG PYSPARK_DRIVER_PYTHON=python

ARG PYENV_ROOT

ENV SPARK_VERSION=${SPARK_VERSION} \
    HADOOP_VERSION=${HADOOP_VERSION} \
    SCALA_VERSION=${SCALA_VERSION} \
    SPARK_HOME=${SPARK_HOME} \
    PYENV_ROOT=${PYENV_ROOT} \
    PYTHONHASHSEED=1 \
    PATH="${PYENV_ROOT}/shims:${PYENV_ROOT}/bin:${PYENV_ROOT}/versions/${PYTHON_SPARK_VERSION}/bin:$PATH"

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
    # install dependencies for pyenv
    git \
    wget \
    curl \
    make \
    build-essential \
    libssl-dev \
    zlib1g-dev \
    libbz2-dev \
    libreadline-dev \
    libsqlite3-dev \
    llvm \
    libncurses5-dev \
    libncursesw5-dev \
    xz-utils \
    tk-dev \
    libffi-dev \
    liblzma-dev \
    # install dependencies for pyspark
    software-properties-common \
    ssh \
    net-tools \
    ca-certificates && \
    # install pyenv
    git clone https://github.com/pyenv/pyenv.git /home/${USER}/.pyenv && \
    /home/${USER}/.pyenv/bin/pyenv install ${PYTHON_SPARK_VERSION} && \
    /home/${USER}/.pyenv/bin/pyenv global ${PYTHON_SPARK_VERSION} && \
    chown -R ${USER}:${USER} /home/${USER}/.pyenv && \
    # install spark
    wget --no-verbose -O apache-spark.tgz "https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}-scala${SCALA_VERSION}.tgz" && \
    mkdir -p /opt/spark && \
    tar -xf apache-spark.tgz -C /opt/spark --strip-components=1 && \
    rm apache-spark.tgz

FROM base AS spark

ARG USER

ARG PYTHON_SPARK_VERSION

ARG SPARK_HOME
ARG SPARK_MASTER_PORT
ARG SPARK_MASTER_WEBUI_PORT
ARG SPARK_WORKER_PORT
ARG SPARK_WORKER_WEBUI_PORT

ARG PYSPARK_PYTHON=python
ARG PYSPARK_DRIVER_PYTHON=python

ARG PYENV_ROOT

ENV SPARK_HOME=${SPARK_HOME} \
    SPARK_LOG_DIR=${SPARK_HOME}/logs \
    SPARK_MASTER_LOG=${SPARK_HOME}/logs/spark-master.out \
    SPARK_WORKER_LOG=${SPARK_HOME}/logs/spark-worker.out \
    SPARK_MASTER_PORT=${SPARK_MASTER_PORT} \
    SPARK_MASTER_WEBUI_PORT=${SPARK_MASTER_WEBUI_PORT} \
    SPARK_WORKER_WEBUI_PORT=${SPARK_WORKER_WEBUI_PORT} \
    SPARK_WORKER_PORT=${SPARK_WORKER_PORT} \
    SPARK_MASTER="spark://scout-spark-master:${SPARK_MASTER_PORT}" \
    SPARK_WORKLOAD="master" \
    PYENV_ROOT=${PYENV_ROOT} \
    PYTHONHASHSEED=1 \
    PYSPARK_PYTHON=${PYSPARK_PYTHON} \
    PYSPARK_DRIVER_PYTHON=${PYSPARK_DRIVER_PYTHON} \
    PATH="${PYENV_ROOT}/shims:${PYENV_ROOT}/bin:${PYENV_ROOT}/versions/${PYTHON_SPARK_VERSION}/bin:$PATH"

WORKDIR ${SPARK_HOME}

EXPOSE 8080 7077 7000

RUN mkdir -p ${SPARK_LOG_DIR} && \
    touch ${SPARK_MASTER_LOG} && \
    touch ${SPARK_WORKER_LOG} && \
    ln -sf /dev/stdout ${SPARK_MASTER_LOG} && \
    ln -sf /dev/stdout ${SPARK_WORKER_LOG} && \
    pip install --no-cache-dir numpy matplotlib scipy pandas simpy

COPY scripts/spark/start.sh /

CMD ["/bin/bash", "/start.sh"]

FROM base AS devcontainer

ARG USER

ARG PYTHON_SPARK_VERSION

ARG PYSPARK_PYTHON=python
ARG PYSPARK_DRIVER_PYTHON=python

ARG PYENV_ROOT

ENV PYENV_ROOT=${PYENV_ROOT} \
    PYTHONHASHSEED=1 \
    PYSPARK_PYTHON=${PYSPARK_PYTHON} \
    PYSPARK_DRIVER_PYTHON=${PYSPARK_DRIVER_PYTHON} \
    PATH="${PYENV_ROOT}/shims:${PYENV_ROOT}/bin:${PYENV_ROOT}/versions/${PYTHON_SPARK_VERSION}/bin:$PATH"

COPY docker/scripts/devcontainer/requirements /tmp/requirements
RUN pip install -r /tmp/requirements/dev.txt

ENTRYPOINT [ "sleep", "infinity" ]