# [2.5.0](https://github.com/Project-Carbonica/RuntimeDependency/compare/v2.4.0...v2.5.0) (2026-01-06)


### Features

* add support for excluding dependencies in runtime loader ([356c3b0](https://github.com/Project-Carbonica/RuntimeDependency/commit/356c3b08f96f04bdf3e276d6747a373faf6f479e))

# [2.4.0](https://github.com/Project-Carbonica/RuntimeDependency/compare/v2.3.0...v2.4.0) (2026-01-02)


### Features

* add support for local Maven dependencies and update README ([e4ce12a](https://github.com/Project-Carbonica/RuntimeDependency/commit/e4ce12a3465feebf0ed28800ada90d95851ab79b))

# [2.3.0](https://github.com/Project-Carbonica/RuntimeDependency/compare/v2.2.2...v2.3.0) (2026-01-02)


### Features

* add support for local Maven repositories in RepositoryInfo ([15e4d85](https://github.com/Project-Carbonica/RuntimeDependency/commit/15e4d850c174ec27127fa3d26a9124b58a746a67))

## [2.2.2](https://github.com/Project-Carbonica/RuntimeDependency/compare/v2.2.1...v2.2.2) (2026-01-02)


### Bug Fixes

* restrict credential collection and authentication to HTTP/HTTPS repositories ([c47e754](https://github.com/Project-Carbonica/RuntimeDependency/commit/c47e754575a735ca502e172c0474197649f9cab7))

## [2.2.1](https://github.com/Project-Carbonica/RuntimeDependency/compare/v2.2.0...v2.2.1) (2025-12-17)


### Bug Fixes

* update package name for RuntimeDependencyPlugin and clean up project structure ([d20f89f](https://github.com/Project-Carbonica/RuntimeDependency/commit/d20f89f94c27fbd0ba9ca45bbb2f78cd697f60db))

# [2.2.0](https://github.com/Project-Carbonica/RuntimeDependency/compare/v2.1.3...v2.2.0) (2025-12-17)


### Bug Fixes

* remove unnecessary gradle properties from release assets configuration ([1000773](https://github.com/Project-Carbonica/RuntimeDependency/commit/1000773b75bb1dc5b309786493b60138f846b76e))


### Features

* restructure Gradle build configuration and update package names for RuntimeDependency ([7bd0a02](https://github.com/Project-Carbonica/RuntimeDependency/commit/7bd0a023829f54aa0af3636887053a4f3663452a))

## [2.1.3](https://github.com/Project-Carbonica/RuntimeDependency/compare/v2.1.2...v2.1.3) (2025-12-17)


### Bug Fixes

* update release configuration and add gradle properties for build caching ([8eb0b54](https://github.com/Project-Carbonica/RuntimeDependency/commit/8eb0b54c16aa9e476b214f74dd09f66ae18a09f5))

## [2.1.2](https://github.com/Project-Carbonica/RuntimeDependency/compare/v2.1.1...v2.1.2) (2025-12-17)


### Bug Fixes

* publish keys ([0afcfcd](https://github.com/Project-Carbonica/RuntimeDependency/commit/0afcfcd7fbc81980ff9a9afcb6415c8a87004348))

## [2.1.1](https://github.com/Project-Carbonica/RuntimeDependency/compare/v2.1.0...v2.1.1) (2025-12-17)


### Bug Fixes

* add Nexus release and snapshot URLs to workflow configuration ([e2d42bb](https://github.com/Project-Carbonica/RuntimeDependency/commit/e2d42bb483a67b5f97e43404bf9a8d439b9277c2))

# [2.1.0](https://github.com/Project-Carbonica/RuntimeDependency/compare/v2.0.1...v2.1.0) (2025-12-17)


### Bug Fixes

* add auto-detection of plugin package and update README with repository declaration instructions ([9d2a393](https://github.com/Project-Carbonica/RuntimeDependency/commit/9d2a393044753d4f0d2ece93cfbad671a6e74e25))
* add CI/CD pipeline and update release configuration ([db74627](https://github.com/Project-Carbonica/RuntimeDependency/commit/db746274753f72a026ea261b5961191df5777d4b))
* deleted test plugin ([fd66f13](https://github.com/Project-Carbonica/RuntimeDependency/commit/fd66f13d5cfb0001e2b495cbf83b10d75170e3dc))
* enable recursive submodule checkout in build-test.yml ([2bfd98d](https://github.com/Project-Carbonica/RuntimeDependency/commit/2bfd98d7a409010589e8a2f0769d9cfad892546a))
* update build-test.yml to use GITHUB_TOKEN for submodule checkout ([30757b1](https://github.com/Project-Carbonica/RuntimeDependency/commit/30757b112934e19791bf013d30dfce542ef03888))


### Features

* add root build file for managing included builds ([b6158d4](https://github.com/Project-Carbonica/RuntimeDependency/commit/b6158d4da14433ec0f39c5009fe500f5656b060d))

## [2.0.1](https://github.com/Project-Carbonica/RuntimeDependency/compare/v2.0.0...v2.0.1) (2025-11-28)


### Bug Fixes

* add kapt task dependency for generatePaperLoader ([da5dae0](https://github.com/Project-Carbonica/RuntimeDependency/commit/da5dae0853199b03bad0f22d14fb5f4e54814bc9))

# [2.0.0](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.6.1...v2.0.0) (2025-11-28)


### Code Refactoring

* remove Standalone and Velocity modes, keep only Paper mode ([1ee2d46](https://github.com/Project-Carbonica/RuntimeDependency/commit/1ee2d468c0fb257cee9ae94964d8982f8d098557))


### BREAKING CHANGES

* Removed Standalone and Velocity modes
- Deleted InjectionTasks.kt and BootstrapMain.java
- Removed VelocityExtension and StandaloneExtension from Extensions.kt
- Simplified RuntimeDependencyPlugin to Paper-only mode
- Removed GenerateVelocityUtilityTask from Tasks.kt
- Deleted test-standalone module
- Updated README.md and USAGE.md for Paper-only documentation

## [1.6.1](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.6.0...v1.6.1) (2025-11-28)


### Bug Fixes

* remove clean task from publish workflow to prevent Gradle 8.14 deadlock ([276dd27](https://github.com/Project-Carbonica/RuntimeDependency/commit/276dd27ec5697b7e5a155180d67e3d9faf373143))

# [1.6.0](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.5.1...v1.6.0) (2025-11-28)


### Bug Fixes

* remove clean task to prevent Gradle 8.14 deadlock in CI ([ff754d6](https://github.com/Project-Carbonica/RuntimeDependency/commit/ff754d6aa54724ef4a9b2a682f54a2e6292ec466))


### Features

* Add Velocity mode for runtime dependency loading ([6259b04](https://github.com/Project-Carbonica/RuntimeDependency/commit/6259b0429e00551b6fad1da84b6ebd331502962c))

## [1.5.1](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.5.0...v1.5.1) (2025-11-27)


### Bug Fixes

* revert version to use property fallback for flexibility ([0713cd7](https://github.com/Project-Carbonica/RuntimeDependency/commit/0713cd783b6f7420f1312c2151c57a217fb2727e))

# [1.5.0](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.4.0...v1.5.0) (2025-11-27)


### Features

* fix standolone and publish new version ([c4a0189](https://github.com/Project-Carbonica/RuntimeDependency/commit/c4a01891ef25ddcf37810526e98aac8ddde8bb3b))

# [1.4.0](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.3.0...v1.4.0) (2025-11-05)


### Features

* add standalone mode support with launcher and configuration options ([e336598](https://github.com/Project-Carbonica/RuntimeDependency/commit/e336598a4e36af78195d6f3df3b7fc87617ff848))

# [1.3.0](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.2.0...v1.3.0) (2025-11-05)


### Bug Fixes

* changed workflows ([aa9ae66](https://github.com/Project-Carbonica/RuntimeDependency/commit/aa9ae66435a5e4c8d5c0fa8c0943dcf7201bc611))
* fixed paper private repo error ([8526597](https://github.com/Project-Carbonica/RuntimeDependency/commit/852659764699028755cffdc4c5e7207d64489d34))


### Features

* add support for private repository authentication in PluginLoader generation ([19d50ea](https://github.com/Project-Carbonica/RuntimeDependency/commit/19d50ead342cdb430d7ebad7f3ff6eac11ce4ad1))
* deleted app module ([1ce8bd8](https://github.com/Project-Carbonica/RuntimeDependency/commit/1ce8bd824ba51834c1b2c3adbb14733ff6b23b58))

# [1.2.0](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.1.6...v1.2.0) (2025-11-05)


### Features

* enhance private repository credential handling and documentation ([2f90802](https://github.com/Project-Carbonica/RuntimeDependency/commit/2f9080205cb4276a00ce38e5d277b17f7e70d7a5))

## [1.1.6](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.1.5...v1.1.6) (2025-11-05)


### Bug Fixes

* fixed speciacal character problem in secret ([8efcfd2](https://github.com/Project-Carbonica/RuntimeDependency/commit/8efcfd2cb8a0e086fc24b615953eab806c70aeeb))

## [1.1.5](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.1.4...v1.1.5) (2025-11-05)


### Bug Fixes

* changed nexus variables for plugin ([f73ce34](https://github.com/Project-Carbonica/RuntimeDependency/commit/f73ce345776e11a97b7d74ebb5172bb097ea3001))
* changed password type ([1a260f0](https://github.com/Project-Carbonica/RuntimeDependency/commit/1a260f041e0c848182022f865de0d9005f8b4e0e))

## [1.1.4](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.1.3...v1.1.4) (2025-11-05)


### Bug Fixes

* changed nexus variables for plugin ([ce37470](https://github.com/Project-Carbonica/RuntimeDependency/commit/ce37470a3474af04ce9a54d3ab8ec937777a6a0b))

## [1.1.3](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.1.2...v1.1.3) (2025-11-05)


### Bug Fixes

* changed maven name ([76564fc](https://github.com/Project-Carbonica/RuntimeDependency/commit/76564fcd65cb79b464c11027a4b21109b716fd11))

## [1.1.2](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.1.1...v1.1.2) (2025-11-05)


### Bug Fixes

* changed publications in gradle ([f5455c1](https://github.com/Project-Carbonica/RuntimeDependency/commit/f5455c1af9920384e1fee16fc730dec849f5f7f5))

## [1.1.1](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.1.0...v1.1.1) (2025-11-05)


### Bug Fixes

* fixed publish task find ([d3584d8](https://github.com/Project-Carbonica/RuntimeDependency/commit/d3584d8e4e7340653b2194ee690761da8dff01d4))

# [1.1.0](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.0.6...v1.1.0) (2025-11-05)


### Bug Fixes

* added publications ([d8f6bab](https://github.com/Project-Carbonica/RuntimeDependency/commit/d8f6babb9aa9c1191daf99f9d4f0d350564241f0))
* changed publish ([500e31c](https://github.com/Project-Carbonica/RuntimeDependency/commit/500e31c292be9088a4754ab40fbe45cd5db95234))
* changed publish directory ([a4b8c04](https://github.com/Project-Carbonica/RuntimeDependency/commit/a4b8c049d8bed159ee5b473694a4ec14c8f54d69))
* changed publish.yml ([7f3161d](https://github.com/Project-Carbonica/RuntimeDependency/commit/7f3161d9dd191fe7db62c093190d3f5355c71087))
* changed version.yml ([760267a](https://github.com/Project-Carbonica/RuntimeDependency/commit/760267af628752eb032b969b5620e2ea7b08106e))
* removed test security ([bfb376b](https://github.com/Project-Carbonica/RuntimeDependency/commit/bfb376b28a10de434eada511d31a87718b37430d))


### Features

* changed all workflows ([13c51dd](https://github.com/Project-Carbonica/RuntimeDependency/commit/13c51dd314654c71712a48bbfca306022a92d9c2))

## [1.0.6](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.0.5...v1.0.6) (2025-11-05)


### Bug Fixes

* changed gradle.properties for version ([b3308d5](https://github.com/Project-Carbonica/RuntimeDependency/commit/b3308d5c79260a89d3664990afb4f3a42a25b978))

## [1.0.5](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.0.4...v1.0.5) (2025-11-05)


### Bug Fixes

* changed ci-cd ([01d3780](https://github.com/Project-Carbonica/RuntimeDependency/commit/01d3780761c3c183840189f1d477b6087b583253))
* changed publish.yml ([dd10bb0](https://github.com/Project-Carbonica/RuntimeDependency/commit/dd10bb0f166b3d21b54336997313abbff2361fbb))

## [1.0.4](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.0.3...v1.0.4) (2025-11-05)


### Bug Fixes

* added debug for nexus publish ([7ee6e42](https://github.com/Project-Carbonica/RuntimeDependency/commit/7ee6e426d740ff8468c3ccd40c89e85839a2a058))

## [1.0.3](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.0.2...v1.0.3) (2025-11-05)


### Bug Fixes

* changed some deploy mechanichs ([150a4f2](https://github.com/Project-Carbonica/RuntimeDependency/commit/150a4f28e5ab12505ffdd54d0510194bc6985ff0))

## [1.0.2](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.0.1...v1.0.2) (2025-11-05)


### Bug Fixes

* changed some deploy mechanichs ([d871f3f](https://github.com/Project-Carbonica/RuntimeDependency/commit/d871f3f07296c5fa5c99dc6e4c7057826599bc60))

## [1.0.1](https://github.com/Project-Carbonica/RuntimeDependency/compare/v1.0.0...v1.0.1) (2025-11-04)


### Bug Fixes

* add support for Nexus credentials via project properties ([880d84c](https://github.com/Project-Carbonica/RuntimeDependency/commit/880d84c7fc68610d0e7880d1e46772efad559fb1))

# 1.0.0 (2025-11-04)


### Bug Fixes

* added exec plugin to version.yml ([bbe5fbb](https://github.com/Project-Carbonica/RuntimeDependency/commit/bbe5fbb81141ed85418394974226ca6413fe4b4b))
* added version ([7b706bd](https://github.com/Project-Carbonica/RuntimeDependency/commit/7b706bd1e093f656f3ebe883866120fa5991982c))
