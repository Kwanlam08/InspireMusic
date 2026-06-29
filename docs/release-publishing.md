# Release Publishing Setup

This project can stay private while APK downloads are published through a separate public repository.

## One-Time GitHub Setup

1. Create a public repository:

   `Kwanlam08/InspireMusic-Releases`

2. Copy `docs/release-repo/README.md` into that public repository as its `README.md`.

3. Create a GitHub fine-grained personal access token.

   Recommended permissions:

   - Repository access: only `Kwanlam08/InspireMusic-Releases`
   - Contents: Read and write

4. In the private source repository, add an Actions secret:

   `Settings` -> `Secrets and variables` -> `Actions` -> `New repository secret`

   Name:

   `RELEASE_REPO_TOKEN`

   Value:

   your fine-grained token

## Publishing A New APK

Create and push a version tag from the private source repository:

```powershell
git tag v3.0.1
git push origin v3.0.1
```

The workflow will:

1. Build the debug APK.
2. Create or update a release in `Kwanlam08/InspireMusic-Releases`.
3. Upload `InspireMusic-debug.apk` to that public release.

## Manual Publishing

You can also run the workflow manually:

`Actions` -> `Publish Release APK` -> `Run workflow`

Enter a release tag such as `v3.0.1`.

## Notes

- The source repository can remain private.
- The release repository is public and contains only README/release assets.
- The APK is currently the debug build: `app-debug.apk`.
- If you later add release signing, update `.github/workflows/publish-release-apk.yml` to run the release build instead.
