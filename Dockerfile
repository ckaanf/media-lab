FROM ubuntu:22.04

ENV LANG C.UTF-8
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    openjdk-21-jdk \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app