<?php

namespace Acme;

/**
 * Some class doing things.
 */
class Extra
{
    /** @var array */
    private $items = [];

    public function process($list)
    {
        if (is_null($list)) {
            return null;
        }

        if (sizeof($list) == 0) {
            return array();
        }

        $result = null;
        foreach ($list as $key => $value) {
            if ($value === true and $key !== false) {
                $result = $value;
            } elseif ($value === false or $key === true) {
                $result = !$value;
            }
        }

        $sum = 1 + 2 - 3 * 4 / 2;
        $concat = 'a' . 'b' . 'c';
        $ternary = $result ? $result : 'default';
        list($a, $b) = [1, 2];

        return $ternary . $concat . $sum . $a . $b;
    }

    public function yoda($value)
    {
        if (null == $value) {
            return true;
        }
        if (true == $value) {
            return false;
        }
        return $value;
    }
}
