# dpdpguard-server-sdk-jvm

DPDP Guard Server SDK (JVM/Kotlin) - typed API client and consent enforcement for backend fleets

Package: `ai.dpdpguard:server-sdk`

Part of the DPDP Guard SDK family. See the design spec:
https://github.com/chintans/dpdpbot/blob/main/docs/specs/mobile-server-sdk.md

> **Note:** Gradle was not found on PATH when this repo was scaffolded, so
> this is a minimal hand-written skeleton, not the output of gradle init.
> Once Gradle is installed, regenerate properly with:
> `
> gradle init --type kotlin-library --dsl kotlin --test-framework kotlintest --project-name dpdpguard-server-sdk-jvm --package ai.dpdpguard
> `
"@
    New-CiWorkflow E:\Github\chintans\dpdpguard-server-sdk-jvm @"
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - run: ./gradlew build --no-daemon
