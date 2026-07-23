<?php

use Acme\Bar;
use Bar1;
use Acme;
use function Acme\baz;

class sample extends \ArrayObject implements \Countable
{
    const FOO = 'bar';
    var $prop1;
    private $prop2 = array();

    public function __construct($a, $b = TRUE)
    {
        if($a == $b) {
            echo "equal";
        }
        elseif ($a<>$b) {
            echo 'not equal';
        }
        else {
            echo "unknown";
        }

        for($i=0;$i<10;$i++) {
            print($i);
        }

        try {
            $this->doSomething();
        } catch (\Exception $e) {
            throw $e;
        }
    }

    /**
     * @param  $x
     * @return void
     */
    function doSomething($x = NULL) {
        $arr = array(1,2,3,);
        $result = create_function('$a', 'return $a;');
        switch ($x) {
            case 1:
                break;
            default:
                break;
        }
        return;
    }

    function count(): int {
        return count($this->prop2);
    }
}

function helper_function($a,$b)
{
    return $a+$b;
}

$obj = new sample( 1, 2 );
$x = (boolean) $obj;
$y = (Integer)"5";
