# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A core Java library for building linting extensions for the BlueJ IDE (BlueJ Extension API 2). It is consumed by the [Checkstyle](https://github.com/NTNU-IE-IIR/BlueJ-Checkstyle-Plugin) and [SonarLint](https://github.com/NTNU-IE-IIR/BlueJ-SonarLint-Plugin) extensions for BlueJ. This repo is not a runnable extension itself — it provides shared datatypes and UI plumbing that consuming extensions wire up.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for a full class diagram plus sequence diagrams for the startup flow and the lint-request flow — read it before making structural changes to the classes described in the Architecture section below.

## Build

```bash
mvn package        # compile and produce the jar in target/
mvn javadoc:javadoc # generate Javadoc into target/site/apidocs
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

There is a single workflow, `.github/workflows/release.yml`. It only triggers on a **pull request closed against `main`**, and every job is additionally gated on `github.event.pull_request.merged == true` — so nothing runs on a merely-closed/rejected PR, and nothing runs on ordinary pushes or PR-opened events. In other words: there is no CI build/check that runs while a PR is open; the only automation is what happens right after a PR merges into `main`.

On merge, three jobs run in parallel:

- **`create-release`** — runs `mvn -B package`, then reads the merged PR's source branch name (`github.event.pull_request.head.ref`) to derive a version string: a `release/<version>` or `hotfix/<version>` branch name has the prefix stripped to get `<version>`, exported as `RELEASE_VERSION`. It then publishes a GitHub Release tagged `v<RELEASE_VERSION>`, attaching the built jar matched by `./target/*-<RELEASE_VERSION>.jar`. If the PR's branch isn't named `release/...` or `hotfix/...`, neither "extract version" step runs, `RELEASE_VERSION` stays unset, and the release step will fail/misbehave — so this job only makes sense for release/hotfix PRs.
- **`deploy-docs`** — runs `mvn javadoc:javadoc` and publishes `target/site/apidocs` to the `docs` branch (which GitHub Pages serves). This runs for **every** merged PR into `main`, not just release/hotfix ones, so Javadoc on the `docs` branch always tracks the latest `main`.
- **`automerge`** — checks out `develop`, merges `main` into it with `--no-ff`, and pushes. Keeps `develop` from drifting behind `main` after a release/hotfix lands. This will fail (and needs manual conflict resolution) if `main` and `develop` have diverged in a conflicting way.

### How to cut a release

1. Branch from `main` (or wherever the release should be cut from) named exactly `release/<version>` (e.g. `release/1.2.0`) for a normal release, or `hotfix/<version>` for a hotfix.
2. Bump `<version>` in `pom.xml` to match the branch's `<version>` (the workflow does not do this for you — it only uses the branch name to name the GitHub Release and locate the jar via `target/*-<version>.jar`, which comes from the jar plugin using the `pom.xml` version).
3. Open a PR into `main` and merge it. On merge, CI builds the jar, cuts a GitHub Release `v<version>` with the jar attached, redeploys Javadoc to the `docs` branch, and auto-merges `main` back into `develop`.

Merging a PR into `main` from a branch not named `release/*` or `hotfix/*` will still run `deploy-docs` and `automerge`, but `create-release` will not produce a usable release (no version gets extracted).

## Scripts in `tools/`

Both scripts do the same thing — install BlueJ's bundled `bluej.jar` into this project's local Maven repository at `lib/` (the `local_repository` in `pom.xml`) via `mvn install:install-file` — one per OS, since BlueJ's own install layout differs between them:

- **`updateBlueJdeps.ps1`** (Windows/PowerShell) — reads the jar from `C:\Program Files\BlueJ\lib\bluejext2.jar`. Usage: `./updateBlueJdeps.ps1 -Version <n.n.n>`.
- **`updateBlueJdeps.sh`** (macOS/zsh) — same behavior, ported for macOS where BlueJ ships as a `.app` bundle (via jpackage) rather than a fixed Program Files path. Usage: `./updateBlueJdeps.sh <version> [installDir]`. Since the exact jar location inside `BlueJ.app` can vary by release, it searches a few common candidate paths under `/Applications/BlueJ.app` and `~/Applications/BlueJ.app` and falls back to erroring out with instructions to pass the correct directory as `[installDir]` if none are found — confirm the real path with `find /Applications/BlueJ.app -name 'bluejext2.jar'` if the default search fails.

Run either script whenever `pom.xml`'s `bluej` version is bumped to match a newer BlueJ install, so the local jar in `lib/` stays in sync with what the `pom.xml` dependency declares.
