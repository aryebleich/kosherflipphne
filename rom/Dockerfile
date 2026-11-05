# Reproducible build environment for the KosherFlip ROM and apps.
#
#   docker build -t kosherflip-builder rom/
#   docker run --rm -it -v $PWD:/src -v $HOME/aosp:/aosp -e AOSP_DIR=/aosp \
#       kosherflip-builder /src/rom/build-rom.sh
FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        git git-lfs gnupg flex bison build-essential zip unzip curl zlib1g-dev \
        libc6-dev-i386 x11proto-core-dev libx11-dev lib32z1-dev libgl1-mesa-dev \
        libxml2-utils xsltproc fontconfig python3 python-is-python3 rsync bc \
        openjdk-21-jdk-headless ccache xz-utils ca-certificates repo \
    && rm -rf /var/lib/apt/lists/*

# Android SDK (only needed to build the three apps with Gradle)
ENV ANDROID_HOME=/opt/android-sdk
RUN mkdir -p $ANDROID_HOME/cmdline-tools && cd /tmp \
    && curl -fsSLo tools.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip \
    && unzip -q tools.zip -d $ANDROID_HOME/cmdline-tools && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
    && rm tools.zip \
    && yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null \
    && $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
ENV PATH=$PATH:$ANDROID_HOME/platform-tools

RUN git config --global user.name "KosherFlip Builder" && git config --global user.email "builder@localhost" \
    && git config --global color.ui false
ENV USE_CCACHE=1 CCACHE_DIR=/aosp/.ccache
WORKDIR /src
