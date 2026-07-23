<?php

/**
 * Dumps metadata for every built-in php-cs-fixer fixer as JSON on stdout.
 *
 * This is the single source of truth the doc-generator (Generator.scala) reads from to build
 * docs/patterns.json + docs/description/*, instead of cloning/parsing multiple external repos
 * the way codacy-codesniffer's doc-generator has to for PHP_CodeSniffer's plugin ecosystem -
 * php-cs-fixer exposes all of this through its own PHP API in one package.
 */

declare(strict_types=1);

require __DIR__ . '/../vendor/autoload.php';

use PhpCsFixer\Fixer\ConfigurableFixerInterface;
use PhpCsFixer\Fixer\DeprecatedFixerInterface;
use PhpCsFixer\FixerFactory;
use PhpCsFixer\RuleSet\RuleSets;

$factory = (new FixerFactory())->registerBuiltInFixers();

$fixers = [];
foreach ($factory->getFixers() as $fixer) {
    $definition = $fixer->getDefinition();

    $configuration = [];
    if ($fixer instanceof ConfigurableFixerInterface) {
        foreach ($fixer->getConfigurationDefinition()->getOptions() as $option) {
            $configuration[] = [
                'name' => $option->getName(),
                'description' => $option->getDescription(),
                'hasDefault' => $option->hasDefault(),
                'default' => $option->hasDefault() ? $option->getDefault() : null,
            ];
        }
    }

    $successors = $fixer instanceof DeprecatedFixerInterface ? $fixer->getSuccessorsNames() : [];

    // Fixer FQCN is PhpCsFixer\Fixer\<Category>\<Name>Fixer - the category is the source of truth
    // for grouping (Alias, Whitespace, Phpdoc, ...).
    $namespaceParts = explode('\\', get_class($fixer));
    $category = $namespaceParts[count($namespaceParts) - 2] ?? 'Basic';

    $codeSamples = [];
    foreach ($definition->getCodeSamples() as $sample) {
        $codeSamples[] = [
            'code' => $sample->getCode(),
            'configuration' => $sample->getConfiguration(),
        ];
    }

    $fixers[] = [
        'name' => $fixer->getName(),
        'summary' => $definition->getSummary(),
        'description' => $definition->getDescription(),
        'riskyDescription' => $definition->getRiskyDescription(),
        'category' => $category,
        'isRisky' => $fixer->isRisky(),
        'isDeprecated' => $fixer instanceof DeprecatedFixerInterface,
        'successors' => $successors,
        'configuration' => $configuration,
        'codeSamples' => $codeSamples,
    ];
}

// @PSR12 is used as the "enabled by default" baseline in Generator.scala, mirroring how
// codacy-codesniffer's DefaultPatterns.scala sources its default-enabled list from phpcs.xml.dist.
$psr12Rules = array_values(array_filter(
    array_keys(RuleSets::getSetDefinition('@PSR12')->getRules()),
    static fn (string $ruleName): bool => strpos($ruleName, '@') !== 0
));

echo json_encode(
    ['fixers' => $fixers, 'psr12Rules' => $psr12Rules],
    JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE
);
