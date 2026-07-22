#!/bin/bash

# The website is built using MkDocs with the Material theme.
# https://squidfunk.github.io/mkdocs-material/
# It requires Python to run.
# Install the packages with the following command:
# pip install mkdocs mkdocs-material

set -ex

# Generate the API docs
./gradlew dokkaGeneratePublicationHtml -Dkjs=true -Dkwasm=true -Dokio.build.dokka=true
mv build/dokka/html docs/3.x

# Copy in special files that GitHub wants in the project root.
cp CHANGELOG.md docs/changelog.md
cp CONTRIBUTING.md docs/contributing.md
