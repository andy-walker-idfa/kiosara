# Third-party notices

Kiosara (formerly `smarthome-dashboard`) is licensed under the Apache License 2.0
(see `LICENSE`). It includes the following third-party software. The license of every runtime dependency is checked on each build by the
[licensee](https://github.com/cashapp/licensee) Gradle plugin, which fails the build for any
license not on the allow-list in `app/build.gradle.kts`.

## Runtime dependencies (shipped in the APK)

| Library | Version | License |
|---|---|---|
| AndroidX Core (`androidx.core:core-ktx`) | 1.19.1 | Apache-2.0 |
| AndroidX Activity Compose | 1.13.0 | Apache-2.0 |
| AndroidX Lifecycle (runtime, runtime-compose, viewmodel-compose) | 2.11.0 | Apache-2.0 |
| AndroidX DataStore | 1.2.1 | Apache-2.0 |
| Jetpack Compose (BOM 2026.09.00: UI 1.12.1, Foundation 1.12.1, Material3 1.4.0) | 2026.09.00 | Apache-2.0 |
| Kotlin standard library | 2.4.20 | Apache-2.0 |
| kotlinx.coroutines | 1.11.0 | Apache-2.0 |
| kotlinx.serialization (JSON) | 1.11.0 | Apache-2.0 |
| HiveMQ MQTT Client (`com.hivemq:hivemq-mqtt-client`) | 1.4.0 | Apache-2.0 |
| Netty (buffer, codec, common, handler, resolver, transport, transport-native-unix-common), via HiveMQ | 4.1.137.Final | Apache-2.0 |
| RxJava 2, via HiveMQ | 2.2.21 | Apache-2.0 |
| Dagger + javax.inject, via HiveMQ | 2.42 / 1 | Apache-2.0 |
| JCTools, via HiveMQ | 4.0.7 | Apache-2.0 |
| Reactive Streams, via HiveMQ | 1.0.4 | MIT-0 (MIT No Attribution) |

Transitive runtime dependencies (120 artifacts in total, e.g. Okio 3.9.1, JetBrains annotations
23.0.0, JSpecify 1.0.0, Guava listenablefuture 1.0, other AndroidX modules) are Apache-2.0, except Reactive Streams (MIT-0, listed above). The
full list with versions is generated at `app/build/reports/licensee/androidGithubRelease/artifacts.json`
by `./gradlew licenseeAndroidGithubRelease` (the `store` flavor has the same dependencies).

## Build and test tooling (not shipped)

| Tool | Version | License |
|---|---|---|
| Gradle (wrapper) | 9.7.1 | Apache-2.0 |
| Android Gradle Plugin | 9.4.1 | Apache-2.0 |
| ktlint / ktlint-gradle | 1.8.0 / 14.2.0 | MIT |
| licensee | 1.14.1 | Apache-2.0 |
| JUnit 4 | 4.13.2 | EPL-1.0 |
| AndroidX Test (core, runner, ext-junit) | 1.7.0 / 1.7.0 / 1.3.0 | Apache-2.0 |
| kotlinx-coroutines-test | 1.11.0 | Apache-2.0 |

The full text of the Apache License 2.0 is in `LICENSE`.
