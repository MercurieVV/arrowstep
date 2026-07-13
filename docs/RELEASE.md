# Release

arrowstep publishes with Scala CLI. The project currently ships one JVM artifact:

```scala
"io.github.mercurievv" %% "arrowstep" % "<version>"
```

The artifact contains the `core`, `runtime`, and `example` packages from the `app/` source tree.
The package split is source-level for now, matching D10; separate `core` and `runtime` artifacts
should wait until the public API is large enough to justify a multi-module build.

## Metadata

Scala CLI currently marks publish metadata directives as experimental, and those directives make
ordinary `scala-cli compile .` fail unless power mode is enabled. Keep release metadata on the
publish command line so the normal developer commands stay stable. The canonical CI command lives
in `.github/workflows/publish.yml`.

- organization: `io.github.mercurievv`
- artifact name: `arrowstep`
- version: computed from git tags by Scala CLI (`publish.computeVersion git:tag`)
- license: Apache-2.0
- VCS: `github:MercurieVV/arrowstep`

## Local Dry Run

Use a temporary Maven repository so the artifact can be resolved without touching `~/.m2`:

```bash
scala-cli --power publish local . \
  --maven-local \
  --m2-home /tmp/arrowstep-m2 \
  --project-version 0.0.0-local \
  --organization io.github.mercurievv \
  --name arrowstep \
  --url https://github.com/MercurieVV/arrowstep \
  --license Apache-2.0:https://www.apache.org/licenses/LICENSE-2.0.txt \
  --vcs github:MercurieVV/arrowstep \
  --developer 'MercurieVV|MercurieVV|https://github.com/MercurieVV' \
  --description 'Typed, compiler-checked, replayable dialogues between Scala programs and coding agents.' \
  --signer none \
  --with-sources \
  --doc \
  --server=false
```

Then verify the expected POM exists:

```bash
test -f /tmp/arrowstep-m2/io/github/mercurievv/arrowstep_3/0.0.0-local/arrowstep_3-0.0.0-local.pom
```

## Maven Central

Central publishing requires Sonatype Portal credentials and a signing key. Keep all secrets outside
the repository. GitHub Actions expects these secrets:

- `SONATYPE_USERNAME`
- `SONATYPE_PASSWORD`
- `PGP_SECRET_KEY`
- `PGP_SECRET_KEY_PASSWORD`

```bash
scala-cli --power publish . \
  --publish-repository central \
  --organization io.github.mercurievv \
  --name arrowstep \
  --compute-version git:tag \
  --url https://github.com/MercurieVV/arrowstep \
  --license Apache-2.0:https://www.apache.org/licenses/LICENSE-2.0.txt \
  --vcs github:MercurieVV/arrowstep \
  --developer 'MercurieVV|MercurieVV|https://github.com/MercurieVV' \
  --description 'Typed, compiler-checked, replayable dialogues between Scala programs and coding agents.' \
  --user "$SONATYPE_USERNAME" \
  --password "$SONATYPE_PASSWORD" \
  --secret-key env:PGP_SECRET_KEY \
  --secret-key-password env:PGP_SECRET_KEY_PASSWORD \
  --with-sources \
  --doc \
  --server=false
```

Use an annotated git tag for the release version before publishing:

```bash
git tag -a v0.1.0 -m "v0.1.0"
git push origin v0.1.0
```
