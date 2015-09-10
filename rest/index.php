<?php

define('DEBUG', true);

//spl_autoload_register(); // don't load our classes unless we use them
require 'source/theredhead/red.web/src/lib/bootstrap.php'; // git clone https://github.com/theredhead/red.web.git
require /*__DIR__ .*/ 'source/Jacwright/RestServer/RestServer.php';
require 'RootController.php';

use \Jacwright\RestServer\RestServer;

$mode = DEBUG ? 'debug' : 'production'; // 'debug' or 'production'
$server = new RestServer($mode);
// $server->refreshCache(); // uncomment momentarily to clear the cache if classes change in production mode

$server->addClass('RootController');
//$server->addClass('ProductsController', '/products'); // adds this as a base to all the URLs in this class

$server->handle();

//echo "Hi There!";
