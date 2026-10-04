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

There is one workflow, `.github/workflows/release.yml` ("Release"). It is `workflow_dispatch` only. Nothing runs on push or on pull requests, so there is no CI check while a PR is open. It builds with Temurin Java 21 to match `pom.xml`; bump `java-version` there if the compiler target changes. The same workflow is used in the [Checkstyle plugin](https://github.com/NTNU-IE-IIR/BlueJ-Checkstyle-Plugin), which has no Javadoc steps.

It replaced a three-workflow chain (`stage.yml` → `publish.yml` → `javadoc.yml`). In that chain, `publish.yml` and `javadoc.yml` were dispatched on `main`, so they ran `main`'s copies of the files. Those copies were often stale, which is why the first v1.2.0 publish failed. `release.yml` runs entirely on `develop`, so only `develop`'s copy of the workflow is ever used.

Inputs: `releaseVersion` and `nextDevelopmentVersion`. The job is gated on `github.ref == 'refs/heads/develop'`, so if it's dispatched from any other branch the job is silently skipped. It runs as one job with `permissions: contents: write`, using the default `GITHUB_TOKEN`, so no secrets are needed. `develop`, `main` and `docs` must accept pushes from `github-actions[bot]`. `concurrency: release` stops two releases running at once. Steps:

1. **Checks, before anything is changed.** It fails if `main` has commits that aren't in `develop`, because `main` could then not be fast-forwarded. It also fails if the tag `v<releaseVersion>` already exists.
2. **`mvn -B release:clean release:prepare release:perform`** with `-Dmaven.javadoc.skip=true -Dmaven.deploy.skip=true`, driven by the `maven-release-plugin` config in `pom.xml` (`tagNameFormat` `v@{project.version}`, `scmCommentPrefix` `[ci skip]`). The release plugin pushes over HTTPS (the `<scm>` URLs in `pom.xml`) with the token that `actions/checkout` stores in the git config. This:
   - runs `clean verify`, sets the `pom.xml` version to `releaseVersion`, commits it, and tags it `v<releaseVersion>`
   - sets the version to `nextDevelopmentVersion`, commits it, and pushes `develop` plus the tag
   - clones the tag into `target/checkout` and builds it there (deploy is skipped, so nothing goes to a Maven repo)
3. **Fast-forwards `main` to the tag** with a plain (non-force) push, so it fails rather than creating a merge commit if `main` has diverged.
4. **Creates the GitHub Release** `v<releaseVersion>` with `target/checkout/target/bluej-linting-core-<releaseVersion>.jar` attached and release notes generated by GitHub from the merged PRs and commits since the previous tag. Edit them afterwards if needed.
5. **Builds Javadoc from the tagged code** (`mvn javadoc:javadoc` in `target/checkout`) and deploys `target/checkout/target/reports/apidocs` to the `docs` branch, which GitHub Pages serves.

**Never commit directly to `main`.** It only moves forward to release tags. Everything goes to `develop` (via PRs) and reaches `main` with the next release. If `main` ever gets a commit of its own, merge `main` into `develop` before releasing, or the release stops at step 1.

### JitPack

The jar on the GitHub Release is a convenience. Consumers (the Checkstyle and SonarLint plugins) resolve the library through JitPack (`https://jitpack.io` repository in their `pom.xml`), which clones this repo at the requested git tag and builds it on its own servers. `jitpack.yml` in the repo root pins JitPack's build JDK to 21. Without it, JitPack uses an old default JDK and fails on `maven.compiler.release=21`. Keep it in sync with the Java target. The release workflow doesn't talk to JitPack; it builds lazily the first time a consumer asks for a version. Check `https://jitpack.io/#NTNU-IE-IIR/BlueJ-Linting-Core` after a release.

### How to cut a release

1. Make sure everything to be released is merged into `develop`, and that `mvn clean verify` passes locally (`release:prepare` runs the same build).
2. In GitHub → Actions → "Release", choose **Run workflow** with **Use workflow from: `develop`**. Enter the release version **without a `v` prefix** (for example `1.3.0`; the tag format adds the `v`, and typing it yourself gives `vv1.3.0`) and the next development version (for example `1.4.0-SNAPSHOT`). You don't need to edit `pom.xml` by hand; the inputs override whatever snapshot version it has.
3. Afterwards: review the generated release notes, `git pull` on `develop` and `main` to get the bot's commits, and check that JitPack builds the new tag.
4. **If the run fails, what to do depends on where it failed.** Don't simply re-run it once the tag has been pushed, because the tag check will stop it.
   - **Before or during step 2, with nothing pushed:** fix the problem and run "Release" again.
   - **After the tag was pushed:** finish the remaining steps by hand.
     - Fast-forward `main`: `git push origin v<version>^{commit}:refs/heads/main`.
     - Create the release: check out the tag, run `mvn package`, then `gh release create v<version> --generate-notes target/bluej-linting-core-<version>.jar`.
     - Redeploy the Javadoc: check out the tag, run `mvn javadoc:javadoc`, and publish `target/reports/apidocs` to the `docs` branch.

## Scripts in `tools/`

Both scripts do the same thing — install BlueJ's bundled `bluej.jar` into this project's local Maven repository at `lib/` (the `local_repository` in `pom.xml`) via `mvn install:install-file` — one per OS, since BlueJ's own install layout differs between them:

- **`updateBlueJdeps.ps1`** (Windows/PowerShell) — reads the jar from `C:\Program Files\BlueJ\lib\bluej.jar`. Usage: `./updateBlueJdeps.ps1 -Version <n.n.n>`.
- **`updateBlueJdeps.sh`** (macOS/zsh) — same behavior, ported for macOS where BlueJ ships as a `.app` bundle (via jpackage) rather than a fixed Program Files path. Usage: `./updateBlueJdeps.sh <version> [installDir]`. Since the exact jar location inside `BlueJ.app` can vary by release, it searches a few common candidate paths under `/Applications/BlueJ.app` and `~/Applications/BlueJ.app` and falls back to erroring out with instructions to pass the correct directory as `[installDir]` if none are found — confirm the real path with `find /Applications/BlueJ.app -name 'bluej.jar'` if the default search fails.

Run either script whenever `pom.xml`'s `bluej` version is bumped to match a newer BlueJ install, so the local jar in `lib/` stays in sync with what the `pom.xml` dependency declares.
