# Release Publishing

The `Publish Release APK` workflow builds the signed release APK after each push to `master`.

It reads `versionName` from `app/build.gradle.kts`, creates or updates the matching `vX.Y.Z` release in the current `Kwanlam08/InspireMusic` repository, and uploads `InspireMusic-release.apk`.

## Version rules

- Bug fixes and visual polish increment the third number.
- New features increment the second number.
- Major product changes increment the first number.

The workflow uses the repository `GITHUB_TOKEN`; no separate release-repository token is required.
