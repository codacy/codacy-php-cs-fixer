FROM php:8.5-cli AS fixer-metadata

WORKDIR /app

RUN curl -sS https://getcomposer.org/installer | php

COPY composer.json .
COPY dev-tools dev-tools

RUN php composer.phar install --no-interaction --no-dev --optimize-autoloader

RUN php dev-tools/dump-fixers.php > fixers-dump.json

FROM sbtscala/scala-sbt:eclipse-temurin-alpine-25.0.3_9_1.12.13_3.8.4 AS doc-generator

WORKDIR /app

COPY build.sbt .
COPY docs /docs
COPY project project
COPY doc-generator doc-generator
COPY --from=fixer-metadata /app/fixers-dump.json /app/fixers-dump.json

RUN sbt 'doc-generator/runMain codacy.phpcsfixer.docsgen.GeneratorMain'

FROM sbtscala/scala-sbt:graalvm-ce-22.3.3-b1-java17_1.12.11_3.8.4 AS builder

WORKDIR /app

COPY build.sbt .
COPY project project
COPY src src

RUN --mount=type=cache,target=/root/.cache/coursier \
    sbt nativeImage

FROM php:8.5-cli

WORKDIR /app

# Set environment variables
ENV COMPOSER_HOME=/app/.composer
ENV COMPOSER_ALLOW_SUPERUSER=1
ENV PATH=${COMPOSER_HOME}/vendor/bin:${PATH}

# Install Composer and packages
RUN curl -sS https://getcomposer.org/installer | php
COPY composer.* ${COMPOSER_HOME}
RUN php composer.phar global install

# Cleanup and miscellaneous
RUN rm -rf /tmp/* && \
    useradd -m -u 2004 docker

# Copy codacy-php-cs-fixer and docs
COPY --chown=docker:docker --from=builder /app/target/native-image/codacy-php-cs-fixer bin/codacy-php-cs-fixer
COPY --chown=docker:docker --from=doc-generator /docs/ /docs/
COPY --chown=docker:docker --from=doc-generator app/docs/ /docs/

WORKDIR /src

USER docker
ENTRYPOINT ["/app/bin/codacy-php-cs-fixer"]
