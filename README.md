# Authentication and Authorization services for Philanthropy Data Commons

PDC-related extensions of the keycloak auth service.

## Required software

To develop, build, or test code in this repository, an OpenJDK 25 is recommended.

If your machine already has a JDK installed and it works, no further action is needed. If you have no JDK or the one you have is incompatible, standalone packages that work after unzipping can be found at https://www.azul.com/downloads/?version=java-25-lts&package=jdk#zulu

## Releases

On each merge to `main` the CI workflow (`.github/workflows/ci.yml`) builds the three extension jars, runs the tests, and -- if both succeed -- publishes the jars. Each jar is versioned independently by the `pdc-versioning` Gradle convention plugin (in `buildSrc/`) from the most recent commit that touched either shared build infrastructure (everything outside the component subdirectories -- repo-root files plus `.github`, `gradle`, `buildSrc`, and any future top-level directory) or that component's own subdirectory, taking the later of the two: the version is the UTC commit date (`YYYYMMDD`) plus the first seven characters of the commit SHA1 (e.g. `pdc-keycloak-theme-20240313-05793e4.jar`), so any deployed jar traces back to exactly one source commit. The jars are published to GitHub Packages (Maven registry) under the group `org.philanthropydatacommons`; a jar whose GAV is already in the registry is not re-uploaded. One GitHub Release per merge commit to `main` (tagged with that merge commit's `<YYYYMMDD-utc>-<sha7>`, the same format as a per-jar version but computed from the merge commit itself, so a rerun of the same commit targets the same release) carries every jar current at that merge commit as an asset; each jar keeps its own per-jar version in its filename. A jar already present in the registry (from an earlier merge or an off-main prerelease) is downloaded from the registry into the release assets rather than re-uploaded, so every release asset is byte-identical to the canonical jar in GitHub Packages. A build off `main` is a final "latest" release; a build off any other branch is a pre-release.

The "latest" status shown on the **GitHub Releases** page is the authoritative publication marker: only the release whose merge commit is the current `main` tip is marked "Latest", and a build off any other branch is a pre-release. The "latest" tag that appears on a package in the **GitHub Packages** (Maven) UI is a different, automatic registry bookkeeping field -- it is set by GitHub to whichever version was uploaded most recently, regardless of branch or release status, and Maven metadata has no prerelease concept. An off-main pre-release can therefore show as "latest" in GitHub Packages even though its GitHub Release is correctly a pre-release. Depend on a pinned, exact `<version>` (e.g. `20260817-2046ee7`) rather than the Maven "latest" tag, and use the GitHub Releases page to determine which version is the current official release. GLM-5.2

## License

See each subdirectory for the license associated with its respective extension.
