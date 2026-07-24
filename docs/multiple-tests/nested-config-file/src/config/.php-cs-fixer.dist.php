<?php

$finder = (new PhpCsFixer\Finder())->in(__DIR__);

return (new PhpCsFixer\Config())
    ->setRules([
        'constant_case' => true,
    ])
    ->setFinder($finder)
;
