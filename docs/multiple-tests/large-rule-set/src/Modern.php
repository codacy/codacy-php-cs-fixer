<?php

class Modern
{
    public static function make($a, $b)
    {
        $fn = function($x) use ($a) {
            return $x + $a;
        };

        $arrow = fn($x) => $x * 2;

        $data = [
            'one'   => 1,
            'two' => 2,
        ];

        $heredoc = <<<EOT
Hello {$a}
EOT;

        return [$fn($b), $arrow($b), $data, $heredoc];
    }

    public function chain()
    {
        return self::make(1, 2);
    }

    private function unused($a, $a, $c) {
        $unused_var = 5;
        return $c;
    }
}

$closures = array_map(function ($x) {
    return $x + 1;
}, [1, 2, 3]);

echo(implode(',', $closures));
