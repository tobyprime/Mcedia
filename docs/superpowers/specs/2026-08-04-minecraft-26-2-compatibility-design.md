# Minecraft 26.2 Compatibility Design

## Goal

Add strict Minecraft 26.2 development support to DeMinecraft, McediaCore, and the McediaTV Fabric client while preserving the existing 1.21.x and 26.1 source sets. The McediaTV Paper plugin is only checked for compatibility and is not changed unless the existing configuration is proven incompatible.

## Scope

- Generate official Minecraft 26.2 common and client-only deobfuscated sources using the existing Fabric Loom workflow.
- Store the generated references in `DeMinecraft/minecraft-common-deobf-26.2-sources` and `DeMinecraft/minecraft-clientonly-deobf-26.2-sources`.
- Add independent `26.2` version containers to McediaCore and McediaTV.
- Resolve API and mapping changes by compiling the 26.2 source sets and adjusting only version-specific overrides.
- Verify the existing Paper plugin configuration for 26.2 without modifying it when it remains compatible.

## Dependencies

- Minecraft: exact release `26.2` from Mojang's version manifest.
- Java: 25, as declared by the 26.2 release metadata.
- Fabric Loader: `0.19.3`.
- Fabric API: `0.156.0+26.2`.
- Paper check target: existing `26.2.build.92-stable` API/dev bundle configuration, if the current repository already contains it.

## Architecture

Each Minecraft version remains an independent Gradle project. Shared application code stays in the existing module source sets, while version-specific client code and resources live under `versions/26.2`. The 26.2 project uses the existing non-remap Loom build path used by 26.1 and reuses the repository's current source synchronization and artifact publishing conventions.

The DeMinecraft output is an expanded source-jar representation, split by Loom into common and client-only trees. Its manifest files remain intact so later reference work can distinguish the two environments. No new build system is introduced in DeMinecraft.

## Core Changes

- Add `26.2` to the root supported-version list and its Modrinth version mapping key.
- Add `mcedia-core/versions/26.2` and `mcedia-cn-platforms/versions/26.2` properties using the 26.2 dependency coordinates.
- Copy the 26.1 version-specific implementation only as an initial compatibility baseline, then retain only changes required by 26.2 compilation.
- Build and test the 26.2 Core and CN Platforms projects and publish local artifacts for TV validation.

## TV Changes

- Add `26.2` to the Fabric version list and add `mcedia-mtv/versions/26.2` properties using the 26.2 dependency coordinates.
- Adjust 26.2 client-only source overrides and mixins only where the 26.2 compile or runtime contract requires it.
- Build and test the 26.2 Fabric MTV project against the locally published 26.2 Core artifacts.
- Inspect the Paper plugin's existing 26.2 configuration and run its relevant build/test command if available. Do not edit Paper plugin files when the current configuration resolves and compiles.

## Source Generation

After the 26.2 Core version container exists, run Loom's `genCommonSources` and `genClientOnlySources` tasks for `mcedia-core-26.2`. Locate the generated source jars in Loom's Minecraft cache, expand them into the two DeMinecraft directories, and verify the manifests, Java files, and resource files are present.

## Testing

- Verify DeMinecraft has both 26.2 source trees, split manifests, Java sources, and resources.
- Run `:mcedia-core-26.2:build` and `:mcedia-cn-platforms-26.2:build`.
- Run the relevant Core unit tests and inspect compile output for unmapped or removed 26.2 APIs.
- Run `:mcedia-mtv-26.2:build` and its tests after publishing Core 26.2 artifacts locally.
- Run the Paper plugin's 26.2 verification without changing its source/configuration if it passes.

## Preservation Rules

- Preserve all pre-existing user changes and untracked files in McediaCore and McediaTV.
- Do not modify the Paper plugin unless a read-only verification demonstrates that its existing 26.2 support is not functional and a targeted fix is required by the user's scope.
- Do not alter existing 1.21.x or 26.1 version containers except where a shared configuration must list the additional 26.2 version.
