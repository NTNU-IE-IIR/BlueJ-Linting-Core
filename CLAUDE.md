# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A core Java library for building linting extensions for the BlueJ IDE (BlueJ Extension API 2). It is consumed by the [Checkstyle](https://github.com/NTNU-IE-IIR/BlueJ-Checkstyle-Plugin) and [SonarLint](https://github.com/NTNU-IE-IIR/BlueJ-SonarLint-Plugin) extensions for BlueJ. This repo is not a runnable extension itself — it provides shared datatypes and UI plumbing that consuming extensions wire up.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for a full class diagram plus sequence diagrams for the startup flow and the lint-request flow — read it before making structural changes to the classes described in the Architecture section below.

## Build

```bash
mvn package        # compile and produce the jar in target/
mvn javadoc:javadoc # generate Javadoc into target/reports/apidocs
```

There is no test suite in this repo (no `src/test`), so there is no `mvn test` target to run.

### Dependency notes

- `bluejext2` (BlueJ's extension API jar) is **not on Maven Central**. It's resolved from the `lib/` directory, which is configured as a local Maven repository (`repositories` block in `pom.xml`, id `local_repository`). If this dependency fails to resolve, check `lib/bluej/bluejext2/`.
- JavaFX (`javafx-controls`, `javafx-web`) is declared with `<scope>provided</scope>` because BlueJ bundles its own JavaFX runtime at runtime. The `javafx.version` property is pinned to the closest available Maven version to what BlueJ actually ships (see the comment block at the top of `pom.xml` for the exact BlueJ-bundled Java/JavaFX versions this targets).
- Java target is 21 (`maven.compiler.source`/`target`), matching BlueJ 6.0.0's bundled JDK (BlueJ 6.0.0 ships Java 21.0.6 / JavaFX 23.0.2+3; `javafx.version` is set to `23.0.2`).
- This library itself is not published to Maven Central (BlueJ artifacts can't be); consumers use [JitPack](https://jitpack.io/#NTNU-IE-IIR/BlueJ-Linting-Core) instead.

## Architecture

All code lives under `no.ntnu.iir.bluej.extensions.linting.core`, split into five packages that form a pipeline: a checker implementation (provided by the consuming extension) produces `Violation`s, which flow into a `ViolationManager`, which notifies UI listeners.

- **`checker/ICheckerService`** — the extension point. Consuming extensions (Checkstyle, SonarLint) implement this interface to actually run their linter over files. This library only defines the contract (`enable`/`disable`/`isEnabled`/`checkFile`/`checkFiles`); it has no linting logic of its own.

- **`violations/`** — the core data model and pub/sub hub.
  - `Violation` — one lint finding: a summary, the BlueJ `BClass` it was found in, a `TextLocation`, and an optional `RuleDefinition`.
  - `RuleDefinition` — display metadata for a rule (title, id, description, severity/type icons). Icon lookup is delegated to a class-static `IconMapper` that must be injected via `RuleDefinition.setIconMapper(...)` before icons can resolve — this is set once by the consuming extension at startup.
  - `ViolationManager` — the source of truth. Stores violations keyed by file path, notifies registered `ViolationListener`s on any change, and separately tracks the open BlueJ `BPackage`s and a `filePath -> BClass` map (`syncBlueClassMap()`) used to resolve BlueJ class handles back from file paths.
  - `ViolationListener` — implemented by UI components (notably `AuditWindow`) that want to react when the violation map changes.

- **`handlers/`** — BlueJ event glue. These implement BlueJ's `PackageListener`/`ClassListener` interfaces and are meant to be registered with BlueJ by the consuming extension.
  - `PackageEventHandler` — reacts to project/package open/close. Opens one `AuditWindow` per **root** project (tracked in `projectWindowMap`, keyed by directory path; `findRootPackageKey` walks the map to detect that a newly-opened package is a sub-package of an already-open root, in which case no new window is created — it just registers the sub-package with the existing `ViolationManager`). On a root package open it enables the `ICheckerService` and triggers a full re-check via the static `checkAllPackagesOpen`.
  - `FilesChangeHandler` — reacts to individual class file changes/renames/removal, clearing stale violations from the `ViolationManager` and re-triggering `ICheckerService.checkFile` for compiled files only.
  - Both handlers swallow `ProjectNotOpenException`/`PackageNotFoundException` on the assumption that BlueJ only fires these events while the project/package is open — treat these as truly-should-never-happen paths, not places to add real error handling.

- **`ui/`** — JavaFX views, all optional/pluggable by the consuming extension.
  - `AuditWindow` — the main violations window (a `Stage`), one per open BlueJ project. Implements `ViolationListener`; rebuilds its violation list (grouped by file, in `TitledPane`s) every time `onViolationsChanged` fires. Has a shared static `statusBar` and `titlePrefix` that consuming extensions customize once via `setStatusBar`/`setTitlePrefix`.
  - `ViolationCell` — `ListView` cell renderer for a single `Violation`.
  - `RuleWebView` — wraps a JavaFX `WebView` to render a `RuleDefinition`'s HTML description; `AuditWindow` binds its size to the containing pane.
  - `ErrorDialog` — generic error dialog helper.

- **`editor/EditorNotifier`** — static utility that pushes a `Violation`'s location into the BlueJ editor (`JavaEditor`), opening the editor and selecting the offending line/column. Stateless, not instantiable.

- **`util/IconMapper`** — interface for mapping a severity/type name to an icon `URL`; implemented by the consuming extension and injected into `RuleDefinition` (see above).

### Key design point for future changes

This library deliberately does no linting itself and holds no linter-specific config. `ICheckerService` and `IconMapper` are the two seams where a consuming extension plugs in its own logic; everything else (event wiring, violation storage/notification, the audit window UI) is meant to be reused as-is. When changing shared classes (`Violation`, `RuleDefinition`, `ViolationManager`, the handlers), keep in mind both consumers (Checkstyle and SonarLint extensions) depend on the public API shape.

## GitHub Actions

There are three workflows in `.github/workflows/`, and **all of them are `workflow_dispatch` only**. Nothing runs on push or on pull requests, so there is no CI check while a PR is open. They form a chain: `stage.yml` dispatches `publish.yml`, which dispatches `javadoc.yml`. All three build with Temurin Java 21 to match `pom.xml`. Bump `java-version` in all three if the compiler target changes.

- **`stage.yml` — "Stage release with manually assigned versions".** Inputs: `releaseVersion` and `nextDevelopmentVersion`. The job is gated on `github.ref == 'refs/heads/develop'`, so if it's dispatched from any other branch the job is silently skipped. It checks out `develop` using the `SSH_PRIVATE_KEY` repo secret (a deploy key with write access; the `<scm>` URL in `pom.xml` is an SSH URL, so the release plugin pushes over SSH), commits as `github-actions[bot]`, and runs `mvn release:clean release:prepare release:perform` with `-Dmaven.javadoc.skip=true -Dmaven.deploy.skip=true`. Driven by the `maven-release-plugin` config in `pom.xml` (`tagNameFormat` `v@{project.version}`, `scmCommentPrefix` `[ci skip]`), this:
  1. sets the `pom.xml` version to `releaseVersion`, commits it, and tags it `v<releaseVersion>`
  2. sets the version to `nextDevelopmentVersion`, commits it, and pushes `develop` plus the tag
  3. builds the tagged release (deploy is skipped, so nothing goes to a Maven repo)

  It then dispatches `publish.yml` on `main` with `tag_ref: v<releaseVersion>`.
- **`publish.yml` — "Publish release".** Input: `tag_ref` (for example `v1.2.0`). It checks out `main`, runs `git merge <tag_ref>`, and pushes with the default `GITHUB_TOKEN`. It checks out with `fetch-depth: 0` and commits as `github-actions[bot]`, so it can create a real merge commit when `main` has commits that `develop` lacks (a conflicting merge still fails and has to be resolved by hand). It then runs `mvn -B package`, reads `project.version` via `mvn help:evaluate`, and creates a GitHub Release `v<version>` with `./target/*-<version>.jar` attached. Finally it dispatches `javadoc.yml` on `main`.
- **`javadoc.yml` — "Publish Javadoc".** No inputs. It runs `mvn javadoc:javadoc` and deploys `target/reports/apidocs` to the `docs` branch, which GitHub Pages serves. You can also run it by hand to refresh the docs without making a release.

The jar on the GitHub Release is a convenience. Consumers actually resolve the library through JitPack, which builds from the `v<version>` git tag.

### How to cut a release

1. Make sure everything to be released is merged into `develop`. Ideally `main` has no commits that `develop` lacks, so `publish.yml`'s merge is a clean fast-forward.
2. In GitHub → Actions → "Stage release with manually assigned versions", choose **Run workflow** on the `develop` branch. Enter the release version (for example `1.2.0`) and the next development version (for example `1.3.0-SNAPSHOT`). You don't need to edit `pom.xml` by hand.
3. The chain runs automatically: `develop` gets the release and next-snapshot commits plus tag `v1.2.0`, `main` is fast-forwarded to the tag, a GitHub Release `v1.2.0` is created with the jar, and Javadoc is redeployed to `docs`.
4. If a later step fails, re-run it by hand: `publish.yml` with `tag_ref: v<version>` (dispatched on `main`), or `javadoc.yml`.

## Scripts in `tools/`

Both scripts do the same thing — install BlueJ's bundled `bluej.jar` into this project's local Maven repository at `lib/` (the `local_repository` in `pom.xml`) via `mvn install:install-file` — one per OS, since BlueJ's own install layout differs between them:

- **`updateBlueJdeps.ps1`** (Windows/PowerShell) — reads the jar from `C:\Program Files\BlueJ\lib\bluejext2.jar`. Usage: `./updateBlueJdeps.ps1 -Version <n.n.n>`.
- **`updateBlueJdeps.sh`** (macOS/zsh) — same behavior, ported for macOS where BlueJ ships as a `.app` bundle (via jpackage) rather than a fixed Program Files path. Usage: `./updateBlueJdeps.sh <version> [installDir]`. Since the exact jar location inside `BlueJ.app` can vary by release, it searches a few common candidate paths under `/Applications/BlueJ.app` and `~/Applications/BlueJ.app` and falls back to erroring out with instructions to pass the correct directory as `[installDir]` if none are found — confirm the real path with `find /Applications/BlueJ.app -name 'bluejext2.jar'` if the default search fails.

Run either script whenever `pom.xml`'s `bluej` version is bumped to match a newer BlueJ install, so the local jar in `lib/` stays in sync with what the `pom.xml` dependency declares.
