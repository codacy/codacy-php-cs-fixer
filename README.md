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
