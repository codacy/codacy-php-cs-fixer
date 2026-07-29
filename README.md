# Codacy PHP-CS-Fixer

This is the docker engine we use at Codacy to have [PHP-CS-Fixer](https://github.com/PHP-CS-Fixer/PHP-CS-Fixer) support.
You can also create a docker to integrate the tool and language of your choice!
See the [codacy-engine-scala-seed](https://github.com/codacy/codacy-engine-scala-seed) repository for more information.

## Usage

You can create the docker by doing:

```bash
docker build -t codacy-php-cs-fixer .
```

The docker is ran with the following command:

```bash
docker run -it -v $srcDir:/src <DOCKER_NAME>:<DOCKER_VERSION>
```

## Test

We use the [codacy-plugins-test](https://github.com/codacy/codacy-plugins-test) to test our external tools integration.
You can follow the instructions there to make sure your tool is working as expected.

## Generating the documentation

Requires PHP and Composer to build the metadata dump, and `sbt` to generate the final docs:

```bash
composer install
php dev-tools/dump-fixers.php > fixers-dump.json
sbt "doc-generator/runMain codacy.phpcsfixer.docsgen.GeneratorMain"
```

This will create/update `docs/patterns.json`, `docs/description/description.json`, and one
markdown file per fixer under `docs/description/`.

Unlike `codacy-codesniffer` (which has to clone and `phpdoc`-parse several external plugin
repositories), all of PHP-CS-Fixer's fixer metadata - summary, description, configuration
options, risky/deprecated flags, code samples - is available through its own PHP API
(`FixerFactory`, `getDefinition()`, `getConfigurationDefinition()`), so `dev-tools/dump-fixers.php`
is the single source of truth the doc generator reads from.

## Agent Playbook: Updating This Repository End-to-End

This section is written for an AI coding agent (or a human) tasked with updating this repo — most commonly bumping the wrapped PHP-CS-Fixer version, but also base image / orb / sbt-plugin bumps. Follow it top to bottom; it tells you what to change, how the derived docs get produced, how to test locally, and how to interpret CI so you can iterate on failures without guessing.

### 1. What this repository is

This is a **Codacy engine**: a Scala native-image wrapper (`src/main/scala/codacy/Engine.scala` and `src/main/scala/codacy/phpcsfixer/PhpCsFixer.scala`, built on `codacy-engine-scala-seed`) that packages [PHP-CS-Fixer](https://github.com/PHP-CS-Fixer/PHP-CS-Fixer) — a PHP code-style tool installed via Composer — as a Docker image Codacy's platform can run against a customer's PHP source code.

Unlike some other Codacy engines, the generated pattern docs are **not committed to git**. `docs/patterns.json` and `docs/description/*` are both listed in `.gitignore` and are produced fresh on every Docker build:

1. The `fixer-metadata` Docker stage runs `composer install` then `php dev-tools/dump-fixers.php`, which uses PHP-CS-Fixer's own `FixerFactory`/`getDefinition()`/`getConfigurationDefinition()` API to dump every built-in fixer's metadata (summary, description, config options, risky/deprecated flags, code samples) plus the `@PSR12` rule set as `fixers-dump.json`.
2. The `doc-generator` Docker stage feeds that JSON into the Scala program `doc-generator/src/main/scala/codacy/phpcsfixer/docsgen/GeneratorMain.scala` (logic in `Generator.scala`, version helpers in `VersionsHelper.scala`), which writes `docs/patterns.json` and `docs/description/description.json` + one markdown file per fixer.
3. The final image stage copies those generated docs from the `doc-generator` stage into `/docs/` in the runtime image.

`docs/tool-description.md` is hand-maintained. `docs/tests/*` and `docs/multiple-tests/*` are hand-maintained fixtures used by `codacy-plugins-test`.

### 2. Files that encode versions — check all of these on every update

| File | What it controls | What to check |
|---|---|---|
| `composer.json` (`friendsofphp/php-cs-fixer`) | The PHP-CS-Fixer release that gets installed and wrapped | Bump to the target version; then regenerate `composer.lock` (see below). |
| `composer.lock` | Locked dependency tree matching `composer.json` | Must be regenerated with Composer, not hand-edited — it fixes exact versions/hashes for every transitive PHP dependency too. |
| `Dockerfile` (`FROM php:8.5-cli`) | The PHP runtime the tool runs on and is built against | Only bump if the target PHP-CS-Fixer version requires a newer PHP minimum. |
| `Dockerfile` (`sbtscala/scala-sbt:...` base images, two of them: doc-generator and builder stages) | sbt/Scala/GraalVM toolchain used to build the doc generator and the native image | Bump independently of the PHP-CS-Fixer version; check the tags exist on Docker Hub. |
| `build.sbt` (`codacy-engine-scala-seed` version, used in both the `root` and `doc-generator` sbt projects) | Shared Codacy engine scaffolding (config parsing, results reporting, etc.) | Check the latest published version on Maven if bumping. |
| `build.sbt` (`ujson`, `better-files` versions in `doc-generator`) | Doc-generator's own JSON/file-IO libraries | Only relevant if doc-generator itself needs updating. |
| `project/plugins.sbt` (`codacy-sbt-plugin`, `sbt-native-image`) | sbt plugins used for the Codacy build lifecycle and native-image compilation | Bump if instructed to update build tooling. |
| `project/build.properties` (`sbt.version`) | sbt version itself | Bump if instructed to update build tooling. |
| `.circleci/config.yml` → `codacy/base` orb | Shared CircleCI steps (checkout, version tagging, docker publish) | Check the latest published version. |
| `.circleci/config.yml` → `codacy/plugins-test` orb | Runs `codacy-plugins-test` in CI | Same as above. |

### 3. Step-by-step update procedure

1. **Bump the version(s)** as scoped by the task (typically `friendsofphp/php-cs-fixer` in `composer.json`).
2. **Regenerate `composer.lock`** by running `composer update friendsofphp/php-cs-fixer` (requires PHP + Composer locally) so the lock file matches the new constraint. Do not hand-edit `composer.lock`.
3. **Regenerate the docs** (they are not committed, so this step only matters for local verification, not for the git diff): run `composer install`, then `php dev-tools/dump-fixers.php > fixers-dump.json`, then `sbt "doc-generator/runMain codacy.phpcsfixer.docsgen.GeneratorMain"`. Inspect the resulting `docs/patterns.json` / `docs/description/*` for new, removed, or renamed fixers, and update `docs/tests/*` / `docs/multiple-tests/*` fixtures if a fixer they rely on changed behavior or was renamed/removed.
4. **Compile** the main engine with `sbt compile` (and `sbt doc-generator/compile` if you touched the doc generator) to catch any Scala-side breakage.
5. **Build the Docker image**: `docker build -t codacy-php-cs-fixer .` — this exercises all three build stages (`fixer-metadata`, `doc-generator`, `builder`) end to end, including the native-image compilation, and is the most reliable local signal that the bump works.
6. **Run `codacy-plugins-test` locally** before pushing — clone https://github.com/codacy/codacy-plugins-test and run its DockerTest commands against your locally built image tag, per that repo's instructions.
7. **Iterate on failures**, re-running only the relevant step above after each fix.
8. **Commit** the version bump (composer.json + composer.lock, and any other files identified in section 2) in one change. Since generated docs aren't committed, there is no docs diff to include.
9. **Push and open a PR.**
10. **Poll the PR's real CI checks until they all pass — local validation is NOT the finish line.** After every push, run `gh pr checks <pr-url>` and keep re-polling (short sleep while any check is `pending`) until all checks finish. If a check fails, fetch its actual log (don't guess), find the true root cause, fix it, push again (never `--no-verify`, never force-push), and re-poll. Repeat until every check is green. **The CI environment's toolchain can differ from your local one**, so a clean local run does not guarantee CI passes. Only stop iterating when every check passes, or you hit a genuine product/infra decision that needs a human.

### 4. Common failure modes and fixes

| Symptom | Likely cause | Fix |
|---|---|---|
| Production crash / `--config=-` related errors | PHP-CS-Fixer requires an explicit `--config` argument when no config file is discovered in the target repo (see commit `a6a3c99`) | Confirm the wrapper still passes `--config=-` explicitly when no config file is found; a new PHP-CS-Fixer version could change CLI flag behavior. |
| Config file picked up from the wrong directory | `getConfigurationFile` searching too deep or not restricting to the project root (see commits `a6ec0fe`, `efa83db`) | Verify config-file discovery in `PhpCsFixer.scala` still resolves to the repository root, not a nested directory, especially if PHP-CS-Fixer's own auto-discovery semantics changed upstream. |
| `codacy-plugins-test` failures after a fixer bump | Fixers renamed, removed, deprecated-with-successor, or default-enabled set (`@PSR12`) changed upstream | Compare the regenerated `docs/patterns.json` against the previous run; update `docs/tests/*` / `docs/multiple-tests/*` fixtures accordingly. |

### 5. Definition of done

- Version bump(s) reflected in every file identified in section 2 (`composer.json`, `composer.lock`, and any Dockerfile/build.sbt/orb versions in scope).
- `composer.lock` regenerated via Composer, never hand-edited.
- Regenerated docs reviewed locally for fixer additions/removals/renames, with any affected test fixtures under `docs/tests/` and `docs/multiple-tests/` updated.
- `sbt compile` succeeds.
- `docker build` succeeds through all three stages.
- `codacy-plugins-test` commands all pass locally against the freshly built image.
- **After pushing and opening/updating the PR, every CI check on it is green.** Poll `gh pr checks <pr-url>` and iterate on any failure until all pass.

## Configuration file

PHP-CS-Fixer can be configured by adding a `.php-cs-fixer.dist.php` (or `.php-cs-fixer.php`) file
to the source code. When Codacy's pattern configuration overrides this (i.e. specific patterns are
selected in the UI), the wrapper ignores the repository's config file and builds an equivalent
`--rules` JSON payload from the selected patterns instead.

## What is Codacy?

[Codacy](https://www.codacy.com/) is an Automated Code Review Tool that monitors your technical debt, helps you improve your code quality, teaches best practices to your developers, and helps you save time in Code Reviews.

### Among Codacy's features

- Identify new Static Analysis issues
- Commit and Pull Request Analysis with GitHub, BitBucket/Stash, GitLab (and also direct git repositories)
- Auto-comments on Commits and Pull Requests
- Integrations with Slack, HipChat, Jira, YouTrack
- Track issues in Code Style, Security, Error Proneness, Performance, Unused Code and other categories

Codacy also helps keep track of Code Coverage, Code Duplication, and Code Complexity.

Codacy supports PHP, Python, Ruby, Java, JavaScript, and Scala, among others.

### Free for Open Source

Codacy is free for Open Source projects.
