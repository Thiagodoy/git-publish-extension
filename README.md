# Git Publish CLI

`git-publish` is a cross-platform Git extension written in **Java 21**
with [Picocli](https://picocli.info/) that standardizes the release
process for repositories following a Git Flow-style branching model.

Instead of executing the release steps manually, the extension provides
a single command:

``` shell
git publish <tag|major|minor|patch> [options]
```

## 1. Purpose

The main goal of `git-publish` is to keep the repository history simple
and predictable while automating the repetitive parts of publishing a
release.

The release model is based on the following principles:

-   The production branch (`main` or `master`, depending on
    configuration) should represent **released versions only**.
-   Development continues on `develop` and feature/fix branches
    following a [Git
    Flow](https://nvie.com/posts/a-successful-git-branching-model/)-style
    workflow.
-   Releases are identified using [Semantic
    Versioning](https://semver.org/).
-   Production releases squash the changes from `develop` into the
    release branch, creating a single release commit.
-   A `CHANGELOG.md` keeps the historical list of changes included in
    production releases.
-   Commit messages should follow the project commit convention
    described below, based on [Conventional
    Commits](https://www.conventionalcommits.org/).
-   Releases can optionally trigger Jenkins and create/link a Jira
    release.

### Release flow

``` text
feature/* ───────┐
fix/* ───────────┤
                 ▼
              develop
                 │
                 │ Release Candidate
                 ├──────────────► v1.2.0-rc0.01
                 │
                 │ Production Release
                 ▼
          main / master
                 │
                 ├── Release v1.2.0
                 ├── CHANGELOG.md
                 └── tag v1.2.0
```

For a production release, the current implementation uses a squash merge
from `origin/develop`, generates the changelog, creates a release
commit, creates an annotated Git tag, pushes the release branch/tag, and
updates the internal `refs/git-publish/production` reference used to
determine the commits for the next changelog.

### Tag policies

  --------------------------------------------------------------------------
  Release type            Format                  Allowed branch
  ----------------------- ----------------------- --------------------------
  Production release      `vX.Y.Z`                Configured `releaseBranch`

  Release candidate       `vX.Y.Z-rc0.NN`         Configured
                                                  `releaseCandidateBranch`

  Alpha                   `vX.Y.Z-alpha0.NN`      Any branch except `main`,
                                                  `master`, the release
                                                  branch, or the
                                                  release-candidate branch
  --------------------------------------------------------------------------

> **Note:** `rc0.01` and `alpha0.01` are intentionally supported by this
> project. Numeric identifiers containing leading zeroes are not
> strictly compliant with the SemVer 2.0.0 specification.

### Commit convention

The changelog categorization is inspired by Conventional Commits, but
the **current parser uses the project's existing parenthesized
convention**.

Recommended commit format:

``` text
<JIRA-KEY>: (<type>) <description>
```

Examples:

``` text
NXDX6-667: (feat) add automatic release creation
NXDX6-681: (fix) correct tag validation
NXDX6-690: (perf) improve release lookup
NXDX6-702: (docs) update installation guide
NXDX6-710: (feat)! change release API contract
```

The currently recognized changelog categories are:

  Commit marker   Changelog category
  --------------- ---------------------
  `!:`            💥 Breaking Changes
  `(feat)`        ✨ Features
  `(fix)`         🐛 Bug Fixes
  `(perf)`        ⚡ Performance
  `(docs)`        📚 Documentation
  Anything else   🔄 Other Changes

For Jira integration, the issue key must currently appear at the
**beginning** of the commit subject followed by `:`, for example:

``` text
NXDX6-667: (feat) add release endpoint
```

> The implementation currently categorizes commits but does **not
> reject** commits that do not follow this convention. If strict
> Conventional Commit enforcement is required, add validation through a
> Git hook, CI check, or a future `git-publish` validation step.

### References

-   [Semantic Versioning 2.0.0](https://semver.org/)
-   [Conventional Commits](https://www.conventionalcommits.org/)
-   [Git Flow branching
    model](https://nvie.com/posts/a-successful-git-branching-model/)
-   [Picocli](https://picocli.info/)
-   [Jenkins Documentation](https://www.jenkins.io/doc/)
-   [Jira Cloud REST
    API](https://developer.atlassian.com/cloud/jira/platform/rest/v3/)

------------------------------------------------------------------------

## 2. Requirements and Installation

### Requirements

The machine running `git-publish` requires:

-   **Java 21**
-   **Git**
-   **Maven 3.9+** to build the project
-   Access to the repository's `origin` remote
-   Permission to push branches and tags
-   Jenkins connectivity when Jenkins integration is enabled
-   Jira connectivity and credentials when Jira integration is enabled

Verify the main dependencies:

``` shell
java --version
git --version
mvn --version
```

### Build

Build the project before installing it:

``` shell
mvn clean package
```

The Maven Shade plugin creates the executable fat JAR:

``` text
target/git-publish.jar
```

### Configuration file

By default, the CLI loads:

``` text
Windows:    %USERPROFILE%\.git-publish\config.yml
Linux/macOS: ~/.git-publish/config.yml
```

A different file can be supplied with `--config`.

------------------------------------------------------------------------

### Windows installation

The repository contains `install-windows.ps1`.

Build the project:

``` powershell
mvn clean package
```

Run the installer:

``` powershell
.\install-windows.ps1
```

The installer:

1.  Creates `%USERPROFILE%\.git-publish`.
2.  Copies `target\git-publish.jar` into `.git-publish\lib`.
3.  Copies the Windows launcher into `.git-publish\bin`.
4.  Creates the default configuration file if one does not already
    exist.
5.  Adds `.git-publish\bin` to the user's `PATH`.

Open a **new** PowerShell or CMD window and verify:

``` powershell
git publish --help
```

Git automatically exposes an executable named `git-publish` as the Git
subcommand:

``` shell
git publish
```

------------------------------------------------------------------------

### Unix installation --- Linux and macOS

The current repository contains the Unix launcher:

``` text
bin/git-publish
```

but does **not currently contain an `install-unix.sh` installer**.
Installation is therefore manual.

Build the application:

``` shell
mvn clean package
```

Create the installation directories:

``` shell
mkdir -p "$HOME/.git-publish/bin"
mkdir -p "$HOME/.git-publish/lib"
```

Copy the JAR and launcher:

``` shell
cp target/git-publish.jar "$HOME/.git-publish/lib/git-publish.jar"
cp bin/git-publish "$HOME/.git-publish/bin/git-publish"
```

Make the launcher executable:

``` shell
chmod +x "$HOME/.git-publish/bin/git-publish"
```

Create the default configuration if it does not already exist:

``` shell
if [ ! -f "$HOME/.git-publish/config.yml" ]; then
  cp config/config.example.yml "$HOME/.git-publish/config.yml"
fi
```

Add the executable directory to `PATH`.

For **Zsh** (default on modern macOS):

``` shell
echo 'export PATH="$HOME/.git-publish/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

For **Bash**:

``` shell
echo 'export PATH="$HOME/.git-publish/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
```

Verify:

``` shell
git publish --help
```

The expected installation structure is:

``` text
~/.git-publish/
├── bin/
│   └── git-publish
├── lib/
│   └── git-publish.jar
└── config.yml
```

------------------------------------------------------------------------

## 3. Command Parameters

Basic syntax:

```shell
git publish TAG [MODE] [OPTIONS]
```

| Parameter / Option | Required | Description | Example |
| --- | --- | --- | --- |
| `TAG` | **Yes** | Explicit release tag or automatic SemVer increment. Automatic values: `major`, `minor`, `patch`. | `v1.2.0`, `minor` |
| `MODE` | No | Compatibility positional mode. Accepted values: `dryrun` or `dry-run`. | `dryrun` |
| `-n`, `--dry-run` | No | Requests a publication simulation. See **Current Implementation Notes** before relying on it as write-free. | `--dry-run` |
| `--changelog` | No | Requests changelog generation. Production releases currently generate the changelog regardless of this flag. | `--changelog` |
| `-c`, `--config FILE` | No | Uses another YAML configuration file instead of the default `~/.git-publish/config.yml`. | `--config ./config.yml` |
| `-tje`, `--trigger-jenkins` | No | Enables the Jenkins HTTP trigger. | `--trigger-jenkins` |
| `-tji`, `--trigger-jira` | No | Enables Jira release processing. | `--trigger-jira` |
| `-f`, `--fix BRANCH` | No | For a production release, merges the specified fix branch into `develop` before preparing the release. | `--fix=fix/NXDX6-667` |
| `-h`, `--help` | No | Displays Picocli-generated command help. | `git publish --help` |
| `-V`, `--version` | No | Displays the CLI version. | `git publish --version` |

------------------------------------------------------------------------

## 4. Usage Examples

### Explicit production release

From the configured release branch:

``` shell
git publish v1.2.0
```

### Explicit release candidate

From `develop` by default:

``` shell
git publish v1.2.0-rc0.01
```

### Explicit alpha

From a feature or another allowed non-production branch:

``` shell
git publish v1.2.0-alpha0.01
```

### Automatic patch increment

``` shell
git publish patch
```

### Automatic minor increment

``` shell
git publish minor
```

### Automatic major increment

``` shell
git publish major
```

### Request changelog generation

``` shell
git publish minor --changelog
```

### Dry-run syntax

``` shell
git publish minor --dry-run
```

or:

``` shell
git publish minor dryrun
```

> **Important:** in the reviewed source version, the `dryRun` value is
> passed into `PublishService` but is not used to prevent Git writes. Do
> not rely on `--dry-run` as a safe write-free simulation until that
> implementation is completed.

### Trigger Jenkins

``` shell
git publish v1.2.0 --trigger-jenkins
```

### Trigger Jenkins and Jira

``` shell
git publish v1.2.0 --trigger-jenkins --trigger-jira
```

### Production release including a fix branch

``` shell
git publish v1.2.0 --fix=fix/NXDX6-667
```

The current release implementation checks out the fix branch, merges it
into `develop`, pushes `develop`, returns to the original release
branch, and then prepares the production release.

### Custom configuration

Windows:

``` powershell
git publish v1.2.0 --config C:\tools\git-publish.yml
```

Linux/macOS:

``` shell
git publish v1.2.0 --config "$HOME/config/git-publish.yml"
```

------------------------------------------------------------------------

## 5. Semantic Versioning

`git-publish` uses Semantic Versioning as the basis for stable releases:

``` text
MAJOR.MINOR.PATCH
```

Example:

``` text
v2.4.7
 │ │ └── PATCH
 │ └──── MINOR
 └────── MAJOR
```

### Patch

Increment **PATCH** for backward-compatible bug fixes.

``` text
v1.4.2 → v1.4.3
```

``` shell
git publish patch
```

Typical example:

``` text
NXDX6-681: (fix) correct account validation
```

### Minor

Increment **MINOR** when adding backward-compatible functionality.

``` text
v1.4.2 → v1.5.0
```

``` shell
git publish minor
```

Typical example:

``` text
NXDX6-667: (feat) add release history endpoint
```

### Major

Increment **MAJOR** when introducing incompatible or breaking changes.

``` text
v1.4.2 → v2.0.0
```

``` shell
git publish major
```

Typical example:

``` text
NXDX6-710: (feat)! change authentication contract
```

### Automatic version resolution

When `major`, `minor`, or `patch` is supplied, the CLI finds the highest
stable tag matching:

``` text
vX.Y.Z
```

and calculates the next version.

For example:

``` text
Latest stable tag: v1.4.2

git publish patch  → v1.4.3
git publish minor  → v1.5.0
git publish major  → v2.0.0
```

The current branch determines the resulting tag category:

``` text
release branch           + minor → v1.5.0
release-candidate branch + minor → v1.5.0-rc0.01
other allowed branch     + minor → v1.5.0-alpha0.01
```

Existing pre-release sequences are incremented up to `99`:

``` text
v1.5.0-rc0.01    → v1.5.0-rc0.02
v1.5.0-alpha0.01 → v1.5.0-alpha0.02
```

------------------------------------------------------------------------

## 6. Jenkins and Jira Integration

### Configuration model

The source code currently expects this configuration structure:

``` yaml
releaseBranch: master
releaseCandidateBranch: develop

jiraProperties:
  url: "https://company.atlassian.net/rest/api/3/version"
  email: "developer@company.com"
  token: "jira-api-token"

projects:
  payment-service:
    jenkinsWebhookUrl: "https://jenkins.example.com/job/payment-service/build"
    projectAlias: "<projectName>_<tag>"
```

The key under `projects` must match the repository name derived from:

``` shell
git remote get-url origin
```

For example, if the remote is:

``` text
git@github.com:company/payment-service.git
```

the configuration key must be:

``` yaml
projects:
  payment-service:
```

> The `config/config.example.yml` included in the reviewed project is
> currently outdated: it uses `url` and `headers`, while `GitProperties`
> now expects `jenkinsWebhookUrl` and `projectAlias`.

### Jenkins

Jenkins triggering is opt-in:

``` shell
git publish v1.2.0 --trigger-jenkins
```

Example:

``` yaml
projects:
  payment-service:
    jenkinsWebhookUrl: "https://jenkins.example.com/job/payment-service/build"
    projectAlias: "<projectName>_<tag>"
```

The CLI sends an HTTP `POST` with `Content-Type: application/json`.

Example payload:

``` json
{
  "project": "payment-service",
  "tag": "v1.2.0",
  "tagType": "RELEASE",
  "branch": "master",
  "commit": "0add499a4f28b86d4a60a61fb2814637b2a245fc"
}
```

Your Jenkins endpoint must accept this request and use the supplied
tag/commit to start the appropriate build or release pipeline.

#### Jenkins authentication

The reviewed `JenkinsClient` does **not currently add authentication
headers**. Therefore, the configured endpoint must either:

-   be accessible through an internal authenticated network/proxy
    mechanism, or
-   be an endpoint/webhook that does not require additional headers.

If Jenkins requires a token, Basic Auth, bearer token, or CSRF crumb,
`JenkinsClient` must be extended before documenting that authentication
as supported.

#### Jenkins build ID

The current implementation logs a hard-coded build ID (`987`) after a
successful HTTP response. It does not yet retrieve the real Jenkins
queue/build number.

------------------------------------------------------------------------

### Jira

Jira triggering is also opt-in:

``` shell
git publish v1.2.0 --trigger-jira
```

The intended Jira flow is:

``` text
Release commits
      │
      ▼
Extract Jira issue keys
      │
      ▼
Create Jira release/version
      │
      ▼
Associate issues using fixVersions
```

The commit subject must begin with the Jira key:

``` text
NXDX6-667: (feat) add release creation
```

The Jira credentials represented by the current configuration model are:

``` yaml
jiraProperties:
  url: "https://company.atlassian.net/rest/api/3/version"
  email: "developer@company.com"
  token: "jira-api-token"
```

`projectAlias` will used to define release version in Jira:

``` yaml
projects:
  payment-service:
    jenkinsWebhookUrl: "https://jenkins.example.com/job/payment-service/build"
    projectAlias: "<projectName>_<tag>"
```

#### Current Jira implementation status

The reviewed Jira integration should be considered **incomplete and not
production-ready yet**.

The current source has several inconsistencies that should be fixed
before enabling `--trigger-jira`:

1.  The `ReleaseVersionRequest` constructor arguments do not currently
    match the record field semantics (`projectId` / `releaseDate`).
2.  The create-version request does not currently add the Basic
    Authorization header.
3.  The same configured `url` is used both as the create-version
    endpoint and as the base for constructing
    `/rest/api/3/issue/{issue}`, which produces incompatible URL
    requirements.
4.  `JiraService` dereferences `jiraProperties` during construction even
    when Jira triggering is disabled, so the Jira configuration
    currently needs to exist to avoid a null configuration failure.

A cleaner configuration model would be:

``` yaml
jiraProperties:
  baseUrl: "https://company.atlassian.net"
  email: "${JIRA_EMAIL}"
  token: "${JIRA_TOKEN}"
```

with the application constructing:

``` text
POST /rest/api/3/version
PUT  /rest/api/3/issue/{issueKey}
```

This is a recommended code change; `baseUrl` is **not yet the field
expected by the reviewed implementation**.

> Avoid committing real Jira tokens or Jenkins credentials to the
> repository. Prefer environment variables or a secret-management
> mechanism.

------------------------------------------------------------------------

## 7. Changelog and Production Release Behavior

For a production release, the current code:

1.  Verifies that the command is running inside a Git repository.
2.  Detects the current branch and repository name.
3.  Requires a clean working tree.
4.  Fetches the current branch and tags.
5.  Requires the local branch to match `origin/<branch>`.
6.  Resolves the explicit or automatically calculated tag.
7.  Validates the tag against the current branch.
8.  Optionally merges a supplied fix branch into `develop`.
9.  Reads commits between `refs/git-publish/production` and
    `origin/develop`.
10. Excludes commit subjects containing `Merge` or `Release`.
11. Groups commits into changelog categories.
12. Squash-merges `origin/develop`.
13. Prepends the release section to `CHANGELOG.md`.
14. Creates a release commit.
15. Creates an annotated Git tag.
16. Pushes the release branch and tag.
17. Updates and pushes `refs/git-publish/production`.
18. Optionally triggers Jenkins.
19. Optionally creates the Jira release and associates discovered
    issues.

This approach keeps the production branch centered around release
commits instead of copying the complete development history into the
main line.

------------------------------------------------------------------------

## 8. Current Implementation Notes

This README was reviewed against the supplied source code. The following
differences from the older README are important.

### Dry run is not currently write-free

`--dry-run` and positional `dryrun` are parsed correctly, but
`PublishService` does not use the value to skip Git writes.

As a result, the current source can still create and push tags/commits
while `--dry-run` is present.

**Recommendation:** guard every mutating operation or introduce a
command/execution abstraction with dry-run support before advertising
this as a safe simulation mode.

### `--changelog` is currently ignored for production releases

The `generateChangelog` parameter reaches `publishRelease`, but the
implementation generates and writes `CHANGELOG.md` regardless of its
value.

**Recommendation:** either make changelog generation mandatory and
remove `--changelog`, or honor the option in `PublishService`.

Given the purpose of this project---maintaining release history---the
cleaner model is probably to make changelog generation automatic for
every production release.

### Production release currently hard-codes `develop`

Although `releaseCandidateBranch` is configurable, production release
operations currently use literal `develop` / `origin/develop`.

**Recommendation:** replace those literals with:

``` java
config.releaseCandidateBranch()
```

so custom Git Flow configurations work consistently.

### Existing-tag helper methods are not currently used

`GitClient` implements:

``` text
localTagExists(tag)
remoteTagExists(tag)
```

but the reviewed publication flow does not call them before creating the
tag.

Git itself will reject an existing local tag, but explicit validation
would provide a clearer error and should also verify the remote state.

### Configuration example in the repository is outdated

The current Java record is:

``` text
GitProperties(
    String jenkinsWebhookUrl,
    String projectAlias
)
```

The checked-in example still contains:

``` yaml
url:
headers:
```

Update `config/config.example.yml` to match the actual model.

### Unix installer is not included

The Unix launcher exists at:

``` text
bin/git-publish
```

but there is no `install-unix.sh` in the reviewed project.

This README therefore documents the installation steps manually instead
of claiming that an installer exists.

### Jira initialization currently requires configuration

`PublishCommand` always creates `JiraService`, and its constructor
immediately accesses `config.jiraProperties()`.

Until this is changed, provide `jiraProperties` in `config.yml` even if
`--trigger-jira` is not being used.

### Release branch default

`AppConfig` defaults `releaseBranch` to `main`, but the supplied
`config.example.yml` explicitly sets:

``` yaml
releaseBranch: master
```

Therefore the actual release branch depends on the configuration file.
This README deliberately refers to the **configured release branch**
rather than assuming `main`.

------------------------------------------------------------------------

## Recommended configuration after aligning the current code

Until the Jira URL handling is refactored, the configuration file should
be updated carefully alongside the Jira implementation. For the non-Jira
fields, the source-aligned structure is:

``` yaml
releaseBranch: master
releaseCandidateBranch: develop

projects:
  payment-service:
    jenkinsWebhookUrl: "https://jenkins.example.com/job/payment-service/build"
    projectAlias: "<projectName>_<tag>"
```

After the recommended Jira `baseUrl` refactor, the target configuration
should become:

``` yaml
releaseBranch: master
releaseCandidateBranch: develop

jiraProperties:
  baseUrl: "https://company.atlassian.net"
  email: "${JIRA_EMAIL}"
  token: "${JIRA_TOKEN}"

projects:
  payment-service:
    jenkinsWebhookUrl: "https://jenkins.example.com/job/payment-service/build"
    projectAlias: "<projectName>_<tag>"
```

------------------------------------------------------------------------

## License

Add the project's license information here if the repository is intended
for distribution outside the organization.
