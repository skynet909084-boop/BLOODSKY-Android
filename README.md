# BLOODSKY Android

Private development workspace for the BLOODSKY Android SCADA project.

## Cloud analysis of Kaskada Craft APK

The repository contains a GitHub Actions workflow at `.github/workflows/decompile-apk.yml`. It downloads an APK from a **private GitHub Release**, runs JADX and Apktool on a GitHub-hosted runner, then uploads the decoded output as an Actions artifact for 7 days.

### One-time setup from a phone

1. Open **Releases**: https://github.com/skynet909084-boop/BLOODSKY-Android/releases
2. Choose **Create a new release**.
3. Use tag `kascada-analysis-1`.
4. Title: `Kaskada Craft APK analysis input`.
5. Attach the original `.apk` file under **Attach binaries by dropping them here or selecting them**. Keep the repository private and do not publish the APK in a public repository.
6. Publish the release.

### Run the cloud workflow

1. Open **Actions**: https://github.com/skynet909084-boop/BLOODSKY-Android/actions/workflows/decompile-apk.yml
2. Select **Run workflow**.
3. Leave the release tag blank to use the latest release, or enter `kascada-analysis-1`.
4. Start the workflow and wait for it to finish.
5. Open the completed run and download the artifact named **BLOODSKY-Kaskada-decompiled** from the Artifacts section.
6. Attach that ZIP to the BLOODSKY chat for source analysis.

The workflow uses the repository-scoped `GITHUB_TOKEN` to access release assets. It does not commit the APK or decoded output into Git history. Decompilation is approximate and may report errors. Only analyze APKs you are authorized to inspect; keep the repository and artifacts private.
