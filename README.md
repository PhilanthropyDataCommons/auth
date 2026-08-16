# Authentication and Authorization services for Philanthropy Data Commons

PDC-related extensions of the keycloak auth service.

## Required software

To develop, build, or test code in this repository, an OpenJDK 25 is recommended.

If your machine already has a JDK installed and it works, no further action is needed. If you have no JDK or the one you have is incompatible, standalone packages that work after unzipping can be found at https://www.azul.com/downloads/?version=java-25-lts&package=jdk#zulu

## Releases

Each jar is versioned independently by the `pdc-versioning` Gradle convention plugin (in `buildSrc/`) from the most recent commit that touched either shared build infrastructure (any repo-root file, plus the `.github` and `gradle` directories) or that component's own subdirectory, taking the later of the two: the version is the UTC commit date (`YYYYMMDD`) plus the first seven digits of the commit SHA1 (e.g. `pdc-keycloak-theme-20240313-05793e4.jar`), so any deployed jar traces back to exactly one source commit. See `pdc-keycloak-theme/README.md` for the canonical description of the version scheme.

## License

See each subdirectory for the license associated with its respective extension.
