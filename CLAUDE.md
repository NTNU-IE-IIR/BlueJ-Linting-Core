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

- BlueJ's extension API (Extension API 2, shipped inside BlueJ's `bluej.jar`) is **not on Maven Central**. It's declared as `bluej:bluej:6.0.0` and resolved from the `lib/` directory, which is configured as a local Maven repository (`repositories` block in `pom.xml`, id `local_repository`). If this dependency fails to resolve, check `lib/bluej/bluej/<version>/`, and repopulate it with the scripts in `tools/` (see below).
- JavaFX (`javafx-controls`, `javafx-web`) is declared with `<scope>provided</scope>` because BlueJ bundles its own JavaFX runtime at runtime. The `javafx.version` property is pinned to the closest available Maven version to what BlueJ actually ships (see the comment block at the top of `pom.xml` for the exact BlueJ-bundled Java/JavaFX versions this targets).
- Java target is 21 (`maven.compiler.release`), matching BlueJ 6.0.0's bundled JDK (BlueJ 6.0.0 ships Java 21.0.6 / JavaFX 23.0.2+3; `javafx.version` is set to `23.0.2`).
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

**GitHub runs a dispatched workflow from the file on the branch it is dispatched on.** `stage.yml` runs from `develop`, but `publish.yml` and `javadoc.yml` are dispatched on `main`, so `main`'s copies are the ones that run. Each release merges the release tag into `main`, which carries workflow changes from `develop` over to `main`. But the publish run has already loaded `main`'s old `publish.yml` by then, so a change takes effect from the *next* release. If a workflow change on `develop` must apply to the release you're about to cut, copy it to `main` first (`git checkout develop -- .github/workflows` on `main`). The first v1.2.0 publish failed because `main` still had a Java 17 `publish.yml`.

All pushes, tags, releases and dispatches use the default `GITHUB_TOKEN`; no secrets are needed. The repo setting **Settings → Actions → General → Workflow permissions** must be *Read and write*, and both `develop` and `main` must accept pushes from `github-actions[bot]`.

- **`stage.yml` — "Stage release with manually assigned versions".** Inputs: `releaseVersion` and `nextDevelopmentVersion`. The job is gated on `github.ref == 'refs/heads/develop'`, so if it's dispatched from any other branch the job is silently skipped. It checks out `develop`, commits as `github-actions[bot]`, and runs `mvn release:clean release:prepare release:perform` with `-Dmaven.javadoc.skip=true -Dmaven.deploy.skip=true`. The release plugin pushes over HTTPS (the `<scm>` URLs in `pom.xml`) with the token that `actions/checkout` stores in the git config. Driven by the `maven-release-plugin` config in `pom.xml` (`tagNameFormat` `v@{project.version}`, `scmCommentPrefix` `[ci skip]`), this:
  1. runs `clean verify`, then sets the `pom.xml` version to `releaseVersion`, commits it, and tags it `v<releaseVersion>`
  2. sets the version to `nextDevelopmentVersion`, commits it, and pushes `develop` plus the tag
  3. clones the tag into `target/checkout` and builds it (deploy is skipped, so nothing goes to a Maven repo; this only verifies the tagged code builds)

  It then dispatches `publish.yml` on `main` with `tag_ref: v<releaseVersion>`.
- **`publish.yml` — "Publish release".** Input: `tag_ref` (for example `v1.2.0`). It checks out `main` with full history (`fetch-depth: 0`), merges `<tag_ref>` into `main` (a fast-forward when `main` has no commits that `develop` lacks, otherwise a merge commit), and pushes `main`. A conflicting merge fails the run and has to be resolved by hand (merge the tag into `main` locally, push, then re-run `publish.yml`). The merge needs the full history; with a shallow clone git can't find the merge base and fails with `refusing to merge unrelated histories`. It then runs `git checkout <tag_ref>` (detached HEAD) so the build is exactly the tagged code. Re-running it for an already-merged tag is safe, because the merge and push are then no-ops. It runs `mvn -B package`, reads `project.version` via `mvn help:evaluate`, and creates a GitHub Release `v<version>` on the existing tag with `./target/*-<version>.jar` attached and the placeholder body `**Changes:**`. Finally it dispatches `javadoc.yml` on `main`.
- **`javadoc.yml` — "Publish Javadoc".** No inputs. It checks out `main`, runs `mvn javadoc:javadoc` and deploys `target/reports/apidocs` to the `docs` branch, which GitHub Pages serves. Because `publish.yml` merges the release into `main` first, the published Javadoc matches the latest release. You can also run it by hand to refresh the docs without making a release.

### JitPack

The jar on the GitHub Release is a convenience. Consumers (the Checkstyle and SonarLint plugins) resolve the library through JitPack (`https://jitpack.io` repository in their `pom.xml`), which clones this repo at the requested git tag and builds it on its own servers. `jitpack.yml` in the repo root pins JitPack's build JDK to 21. Without it, JitPack uses an old default JDK and fails on `maven.compiler.release=21`. Keep it in sync with the Java target. None of the workflows talk to JitPack; it builds lazily the first time a consumer asks for a version. Check `https://jitpack.io/#NTNU-IE-IIR/BlueJ-Linting-Core` after a release.

### How to cut a release

1. Make sure everything to be released is merged into `develop` and that `mvn clean verify` passes locally (`release:prepare` runs the same build). If you changed workflow files on `develop` since the last release and need them for this one, copy them to `main` first (see above). Ideally `main` has no commits that `develop` lacks, so the release merge is a clean fast-forward.
2. In GitHub → Actions → "Stage release with manually assigned versions", choose **Run workflow** with **Use workflow from: `develop`**. Enter the release version **without a `v` prefix** (for example `1.3.0`; the tag format adds the `v`, and typing it yourself gives `vv1.3.0`) and the next development version (for example `1.4.0-SNAPSHOT`). You don't need to edit `pom.xml` by hand; the inputs override whatever snapshot version it has.
3. The chain runs automatically: `develop` gets the release and next-snapshot commits plus tag `v1.3.0`, the tag is merged into `main`, a GitHub Release `v1.3.0` is created with the jar, and Javadoc is redeployed to `docs` from `main`.
4. Afterwards: edit the release notes (the body is only a placeholder), `git pull` on `develop` to get the bot commits, and check that JitPack builds the new tag.
5. If a later step fails, don't re-run `stage.yml` once the tag has been pushed. Instead re-run the failed step by hand: `publish.yml` with `tag_ref: v<version>` (dispatched on `main`), or `javadoc.yml` (on `main`).

## Scripts in `tools/`

Both scripts do the same thing — install BlueJ's bundled `bluej.jar` into this project's local Maven repository at `lib/` (the `local_repository` in `pom.xml`) via `mvn install:install-file` — one per OS, since BlueJ's own install layout differs between them:

- **`updateBlueJdeps.ps1`** (Windows/PowerShell) — reads the jar from `C:\Program Files\BlueJ\lib\bluej.jar`. Usage: `./updateBlueJdeps.ps1 -Version <n.n.n>`.
- **`updateBlueJdeps.sh`** (macOS/zsh) — same behavior, ported for macOS where BlueJ ships as a `.app` bundle (via jpackage) rather than a fixed Program Files path. Usage: `./updateBlueJdeps.sh <version> [installDir]`. Since the exact jar location inside `BlueJ.app` can vary by release, it searches a few common candidate paths under `/Applications/BlueJ.app` and `~/Applications/BlueJ.app` and falls back to erroring out with instructions to pass the correct directory as `[installDir]` if none are found — confirm the real path with `find /Applications/BlueJ.app -name 'bluej.jar'` if the default search fails.

Run either script whenever `pom.xml`'s `bluej` version is bumped to match a newer BlueJ install, so the local jar in `lib/` stays in sync with what the `pom.xml` dependency declares.
