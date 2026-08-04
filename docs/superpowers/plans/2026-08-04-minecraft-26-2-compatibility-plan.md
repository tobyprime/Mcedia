# Minecraft 26.2 Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add strict Minecraft 26.2 reference sources and Fabric compatibility to McediaCore and the McediaTV client, while leaving the Paper plugin unchanged when its existing 26.2 configuration works.

**Architecture:** Keep 26.2 as independent Gradle version projects under the existing `versions/26.2` directories. Generate Minecraft reference sources with Core's Fabric Loom project, expand the common/client-only source jars into DeMinecraft, then use version-specific source overrides and compile output to adapt Core and TV.

**Tech Stack:** Gradle wrapper, Fabric Loom 1.15-SNAPSHOT, Mojang official mappings, Fabric Loader 0.19.3, Fabric API 0.156.0+26.2, Java 25, Paper 26.2.build.92-stable for read-only plugin verification.

## Global Constraints

- Minecraft target is the exact `26.2` release, not a later 26.2.x patch.
- Java target is 25.
- Fabric Loader is `0.19.3`.
- Fabric API is `0.156.0+26.2`.
- Preserve all pre-existing user changes and untracked files in McediaCore and McediaTV.
- Do not modify McediaTV Paper plugin files unless a verification failure proves its existing 26.2 configuration is incompatible.
- Do not alter existing 1.21.x or 26.1 version source implementations.

---

### Task 1: Add Core 26.2 Version Containers

**Files:**
- Modify: `G:/workspaces/minecraft/McediaCore/gradle.properties`
- Create: `G:/workspaces/minecraft/McediaCore/mcedia-core/versions/26.2/gradle.properties`
- Create: `G:/workspaces/minecraft/McediaCore/mcedia-cn-platforms/versions/26.2/gradle.properties`
- Create: `G:/workspaces/minecraft/McediaCore/mcedia-core/versions/26.2/src/client/java/top/tobyprime/mcedia_core/client/command/ClientCommands.java`
- Create: `G:/workspaces/minecraft/McediaCore/mcedia-core/versions/26.2/src/client/java/top/tobyprime/mcedia_core/client/debug/DebugRendererAudioHitbox.java`
- Create: `G:/workspaces/minecraft/McediaCore/mcedia-core/versions/26.2/src/client/java/top/tobyprime/mcedia_core/client/mixin/MixinInGameHud.java`
- Create: `G:/workspaces/minecraft/McediaCore/mcedia-core/versions/26.2/src/client/java/top/tobyprime/mcedia_core/client/mixin/MixinLevelRenderer.java`
- Create: `G:/workspaces/minecraft/McediaCore/mcedia-core/versions/26.2/src/client/java/top/tobyprime/mcedia_core/client/renderer/HudScreenRenderer.java`
- Create: `G:/workspaces/minecraft/McediaCore/mcedia-core/versions/26.2/src/client/java/top/tobyprime/mcedia_core/client/renderer/McediaRenderTypes.java`
- Create: `G:/workspaces/minecraft/McediaCore/mcedia-core/versions/26.2/src/client/java/top/tobyprime/mcedia_core/client/renderer/McediaRenderer.java`
- Create: `G:/workspaces/minecraft/McediaCore/mcedia-core/versions/26.2/src/client/java/top/tobyprime/mcedia_core/client/renderer/MediaTextureImpl.java`
- Create: `G:/workspaces/minecraft/McediaCore/mcedia-core/versions/26.2/src/client/java/top/tobyprime/mcedia_core/client/renderer/PlayerScreenEntityRenderer.java`
- Create: `G:/workspaces/minecraft/McediaCore/mcedia-core/versions/26.2/src/client/resources/mcedia_core.client.mixins.json`

**Interfaces:**
- Produces Gradle projects `:mcedia-core-26.2` and `:mcedia-cn-platforms-26.2`.
- Produces version-specific source overrides initially copied from the 26.1 baseline.

- [ ] **Step 1: Add the 26.2 root version entry**

Change `supported_mc_versions` from `1.21.8,1.21.11,26.1` to `1.21.8,1.21.11,26.1,26.2` and add `modrinth_game_versions_26_2=26.2` in `gradle.properties`.

- [ ] **Step 2: Create the Core 26.2 properties**

Use this exact content in both version property files:

```properties
minecraft_version=26.2
minecraft_version_range=>=26.2 <26.3
modrinth_game_versions=26.2
loader_version=0.19.3
fabric_api_version=0.156.0+26.2
java_version=25
```

Keep `description = 'Version container for mcedia-core'` in the Core file and `description = 'Version container for mcedia-cn-platforms'` in the CN Platforms file if those descriptions exist in their 26.1 counterparts.

- [ ] **Step 3: Copy the known 26.1 override baseline**

Copy the ten listed 26.1 Core override files to the matching `26.2` paths. Do not copy generated build directories, logs, or the root shared source set.

- [ ] **Step 4: Inspect the Gradle project graph**

Run:

```powershell
./gradlew.bat --no-daemon projects -Psupported_mc_versions=1.21.8,1.21.11,26.1,26.2
```

Expected: the output includes `:mcedia-core-26.2` and `:mcedia-cn-platforms-26.2`.

- [ ] **Step 5: Commit the Core container changes**

Stage only the root property, the two 26.2 property files, and the copied 26.2 override files. Commit with:

```powershell
git commit -m "build: add Minecraft 26.2 Core containers"
```

### Task 2: Generate and Export 26.2 Reference Sources

**Files:**
- Create: `G:/workspaces/minecraft/DeMinecraft/minecraft-common-deobf-26.2-sources/`
- Create: `G:/workspaces/minecraft/DeMinecraft/minecraft-clientonly-deobf-26.2-sources/`

**Interfaces:**
- Consumes `:mcedia-core-26.2` and Mojang release `26.2`.
- Produces split source trees with `META-INF/MANIFEST.MF`, Java sources, and Minecraft resources.

- [ ] **Step 1: Run Loom decompilation tasks**

Run from McediaCore:

```powershell
./gradlew.bat --no-daemon :mcedia-core-26.2:genCommonSources :mcedia-core-26.2:genClientOnlySources
```

Expected: both tasks finish successfully and Loom's cache contains 26.2 common/client-only source jars.

- [ ] **Step 2: Locate the generated source jars**

Run:

```powershell
Get-ChildItem -Recurse -File .gradle\loom-cache | Where-Object { $_.FullName -match 'minecraft-(common|clientOnly).*26\.2' -and $_.Name -match 'sources.*\.jar$' } | Select-Object FullName,Length
```

Use the exact common and client-only jars returned by this command; do not use a 26.1 or 26.2.x jar.

- [ ] **Step 3: Expand the jars into DeMinecraft**

Create the two destination directories and expand each source jar with the JDK `jar` tool, preserving all files:

```powershell
New-Item -ItemType Directory -Force 'G:\workspaces\minecraft\DeMinecraft\minecraft-common-deobf-26.2-sources'
New-Item -ItemType Directory -Force 'G:\workspaces\minecraft\DeMinecraft\minecraft-clientonly-deobf-26.2-sources'
& jar xf '<common-26.2-sources.jar>' -C 'G:\workspaces\minecraft\DeMinecraft\minecraft-common-deobf-26.2-sources'
& jar xf '<clientonly-26.2-sources.jar>' -C 'G:\workspaces\minecraft\DeMinecraft\minecraft-clientonly-deobf-26.2-sources'
```

Because `jar xf -C` is not supported by every JDK, if the installed JDK rejects `-C`, run `jar xf` from each destination directory after changing the current directory to that destination.

- [ ] **Step 4: Verify the split source output**

Run:

```powershell
Get-Content -Raw 'G:\workspaces\minecraft\DeMinecraft\minecraft-common-deobf-26.2-sources\META-INF\MANIFEST.MF'
Get-Content -Raw 'G:\workspaces\minecraft\DeMinecraft\minecraft-clientonly-deobf-26.2-sources\META-INF\MANIFEST.MF'
Get-ChildItem -Recurse -File 'G:\workspaces\minecraft\DeMinecraft\minecraft-common-deobf-26.2-sources' -Filter '*.java' | Measure-Object
Get-ChildItem -Recurse -File 'G:\workspaces\minecraft\DeMinecraft\minecraft-clientonly-deobf-26.2-sources' -Filter '*.java' | Measure-Object
```

Expected: the common manifest says `Fabric-Loom-Split-Environment-Name: common`, the client manifest says `client`, and both Java counts are greater than zero.

### Task 3: Compile and Adapt Core 26.2

**Files:**
- Modify: `G:/workspaces/minecraft/McediaCore/mcedia-core/versions/26.2/src/client/java/` only for 26.2 API errors.
- Modify: `G:/workspaces/minecraft/McediaCore/mcedia-cn-platforms/versions/26.2/` only if its 26.2 compile requires a version-specific override.

**Interfaces:**
- Consumes the Core 26.2 containers and DeMinecraft reference sources.
- Produces compiled and locally published `mcedia_core-mc26.2` and `mcedia_cn_platforms-mc26.2` artifacts.

- [ ] **Step 1: Run the Core 26.2 build**

Run:

```powershell
./gradlew.bat --no-daemon :mcedia-core-26.2:build :mcedia-cn-platforms-26.2:build
```

Record every compiler error whose source path is under `versions/26.2` before editing.

- [ ] **Step 2: Fix only version-specific compile errors**

Update the matching files under `mcedia-core/versions/26.2` or `mcedia-cn-platforms/versions/26.2`. Use the 26.2 source tree under DeMinecraft to confirm changed method signatures, state objects, render submission APIs, and mixin targets. Do not edit 26.1 files to resolve a 26.2-only error.

- [ ] **Step 3: Re-run the focused Core tests**

Run:

```powershell
./gradlew.bat --no-daemon :mcedia-core-26.2:test :mcedia-cn-platforms-26.2:test
```

Expected: exit code 0 with no failed tests. If a module has no tests, Gradle must still complete its test task successfully.

- [ ] **Step 4: Publish the 26.2 artifacts locally**

Run:

```powershell
./gradlew.bat --no-daemon :mcedia-core-26.2:publishMavenJavaPublicationToMavenLocal :mcedia-cn-platforms-26.2:publishMavenJavaPublicationToMavenLocal
```

Verify the local Maven repository contains artifacts whose artifact IDs include `mc26.2`.

### Task 4: Add and Adapt the McediaTV Fabric 26.2 Client

**Files:**
- Modify: `G:/workspaces/minecraft/McediaTV/gradle.properties`
- Create: `G:/workspaces/minecraft/McediaTV/mcedia-mtv/versions/26.2/gradle.properties`
- Create: `G:/workspaces/minecraft/McediaTV/mcedia-mtv/versions/26.2/src/client/java/top/tobyprime/mcedia_mtv/client/channel/MtvClientChannelPayloads.java`
- Create: `G:/workspaces/minecraft/McediaTV/mcedia-mtv/versions/26.2/src/client/java/top/tobyprime/mcedia_mtv/client/command/MtvHudCommand.java`
- Create: `G:/workspaces/minecraft/McediaTV/mcedia-mtv/versions/26.2/src/client/resources/mcedia_mtv.client.mixins.json`

**Interfaces:**
- Consumes the locally published Core/CN Platforms 26.2 artifacts.
- Produces the `:mcedia-mtv-26.2` Fabric client artifact.

- [ ] **Step 1: Add the TV 26.2 root version entry**

Change `supported_mc_versions=1.21.11,26.1` to `supported_mc_versions=1.21.11,26.1,26.2` and add `modrinth_game_versions_26_2=26.2` in the TV root `gradle.properties`.

- [ ] **Step 2: Create the TV 26.2 properties**

Use:

```properties
minecraft_version=26.2
minecraft_version_range=>=26.2 <26.3
loader_version=0.19.3
fabric_api_version=0.156.0+26.2
java_version=25
```

- [ ] **Step 3: Copy the three 26.1 TV override files**

Copy the two Java overrides and the client mixin resource from `mcedia-mtv/versions/26.1` to the matching 26.2 paths.

- [ ] **Step 4: Build the TV 26.2 client**

Run from McediaTV:

```powershell
./gradlew.bat --no-daemon :mcedia-mtv-26.2:build
```

- [ ] **Step 5: Resolve only 26.2 TV API errors**

Update only `mcedia-mtv/versions/26.2` files, consulting the 26.2 DeMinecraft source tree for changed client packet, command, or mixin signatures. Keep the 26.1 source tree unchanged.

- [ ] **Step 6: Run TV tests and rebuild**

Run:

```powershell
./gradlew.bat --no-daemon :mcedia-mtv-26.2:test :mcedia-mtv-26.2:build
```

Expected: exit code 0 and a 26.2 Fabric artifact in `mcedia-mtv/versions/26.2/build/libs`.

### Task 5: Verify the Paper Plugin Without Editing It

**Files:**
- Read-only: `G:/workspaces/minecraft/McediaTV/mcedia-mtv-plugin/build.gradle`
- Read-only: `G:/workspaces/minecraft/McediaTV/mcedia-mtv-plugin/src/main/resources/plugin.yml`

**Interfaces:**
- Consumes the existing Paper 26.2 configuration.
- Produces verification evidence only; produces no source/configuration changes when compatible.

- [ ] **Step 1: Confirm the existing 26.2 configuration**

Verify `mcedia-mtv-plugin/build.gradle` already contains `26.2`, `26.2.build.92-stable`, Java 25, and API level `26.2`.

- [ ] **Step 2: Run the existing Paper verification**

Run from McediaTV:

```powershell
./gradlew.bat --no-daemon :mcedia-mtv-plugin:test -PserverVersion=26.2
```

If this succeeds, do not modify any Paper plugin file. If it fails because of a genuine 26.2 API incompatibility, stop before editing and report the exact failure for scope confirmation.

### Task 6: Final Cross-Repository Audit

**Files:**
- Read-only: Git status and diffs in `G:/workspaces/minecraft/McediaCore` and `G:/workspaces/minecraft/McediaTV`.
- Read-only: source trees under `G:/workspaces/minecraft/DeMinecraft`.

- [ ] **Step 1: Verify preservation of unrelated changes**

Run `git status --short` in both Git repositories and confirm existing Netease files, logs, artifact directories, and other user files remain present.

- [ ] **Step 2: Verify the changed-file scope**

Confirm the new tracked changes are limited to 26.2 Fabric configuration/source overrides, the DeMinecraft 26.2 source directories, design/plan documents, and any compile-driven 26.2 fixes. Confirm no Paper plugin file changed when Task 5 passes.

- [ ] **Step 3: Run final builds**

Re-run the Core 26.2 build and TV 26.2 build commands from Tasks 3 and 4 after all fixes, then report exact exit codes and test counts.

- [ ] **Step 4: Commit implementation changes separately per repository**

Commit only the new implementation files in each repository, preserving unrelated worktree changes. Use:

```powershell
git commit -m "feat: add Minecraft 26.2 Fabric compatibility"
```

Do not commit or delete files in the DeMinecraft directory unless it is later initialized as a repository by the user.
