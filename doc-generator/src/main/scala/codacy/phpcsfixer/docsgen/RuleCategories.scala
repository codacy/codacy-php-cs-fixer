package codacy.phpcsfixer.docsgen

import com.codacy.plugins.api.results.Pattern

object RuleCategories {


  val byPatternId: Map[String, Pattern.Category] = Map(
    // Security
    "random_api_migration" -> Pattern.Category.Security,
    "no_homoglyph_names" -> Pattern.Category.Security,
    "non_printable_character" -> Pattern.Category.Security,

    // ErrorProne
    "error_suppression" -> Pattern.Category.ErrorProne,
    "is_null" -> Pattern.Category.ErrorProne,
    "strict_comparison" -> Pattern.Category.ErrorProne,
    "strict_param" -> Pattern.Category.ErrorProne,
    "no_unreachable_default_argument_value" -> Pattern.Category.ErrorProne,
    "no_unset_on_property" -> Pattern.Category.ErrorProne,
    "no_php4_constructor" -> Pattern.Category.ErrorProne,
    "date_time_create_from_format_call" -> Pattern.Category.ErrorProne,
    "php_unit_strict" -> Pattern.Category.ErrorProne,

    // Performance
    "function_to_constant" -> Pattern.Category.Performance,
    "pow_to_exponentiation" -> Pattern.Category.Performance,
    "combine_nested_dirname" -> Pattern.Category.Performance,
    "string_length_to_empty" -> Pattern.Category.Performance,
    "static_lambda" -> Pattern.Category.Performance,
    "static_private_method" -> Pattern.Category.Performance,

    // Compatibility
    "ereg_to_preg" -> Pattern.Category.Compatibility,
    "modernize_strpos" -> Pattern.Category.Compatibility,
    "modernize_types_casting" -> Pattern.Category.Compatibility,

    // BestPractice
    "declare_strict_types" -> Pattern.Category.BestPractice,
    "final_class" -> Pattern.Category.BestPractice,
    "final_internal_class" -> Pattern.Category.BestPractice,
    "final_public_method_for_abstract_class" -> Pattern.Category.BestPractice,
    "native_function_invocation" -> Pattern.Category.BestPractice,
    "native_constant_invocation" -> Pattern.Category.BestPractice,
    "no_alias_functions" -> Pattern.Category.BestPractice,
    "no_alias_language_construct_call" -> Pattern.Category.BestPractice,
    "get_class_to_class_keyword" -> Pattern.Category.BestPractice,
    "psr_autoloading" -> Pattern.Category.BestPractice,
    "self_accessor" -> Pattern.Category.BestPractice,
    "implode_call" -> Pattern.Category.BestPractice,
    "fopen_flags" -> Pattern.Category.BestPractice,
    "fopen_flag_order" -> Pattern.Category.BestPractice,

    // UnusedCode
    "no_unused_imports" -> Pattern.Category.UnusedCode,
    "lambda_not_used_import" -> Pattern.Category.UnusedCode,

    // Complexity
    "no_unneeded_final_method" -> Pattern.Category.Complexity,
    "no_superfluous_elseif" -> Pattern.Category.Complexity,
    "no_useless_else" -> Pattern.Category.Complexity,
    "no_useless_return" -> Pattern.Category.Complexity,

    // Documentation
    "header_comment" -> Pattern.Category.Documentation,
    "comment_to_phpdoc" -> Pattern.Category.Documentation,
    "general_phpdoc_annotation_remove" -> Pattern.Category.Documentation,
    "general_phpdoc_tag_rename" -> Pattern.Category.Documentation,
    "php_unit_test_class_requires_covers" -> Pattern.Category.Documentation
  )
}