<?php

use \Jacwright\RestServer\RestException;

class RootController
{
	/**
	 * Returns a JSON string object to the browser when hitting the root of the domain
	 *
	 * @url GET /
	 */
	public function test()
	{
		//return "Hello World";
		echo "<pre>" .
			"time(): \t\t\t" . time() . "\r\n" .
			"microtime(): \t\t\t" . microtime() . "\r\n" .
			"microtime(true): \t\t" . microtime(true) . "\r\n" . 
			"round(microtime(true)*1000): \t" . round(microtime(true)*1000) . "\r\n" . 
			"round(microtime(true)*1000)+1: \t" . (round(microtime(true)*1000)+1) . "\r\n" . 
			"<script>document.write(\"new Date().getTime(): \\t\\t\" + new Date().getTime() + \"\\r\\n</pre>\");</script>";
	}

	/**
	 * Logs in a user with the given username and password POSTed. Though true
	 * REST doesn't believe in sessions, it is often desirable for an AJAX server.
	 *
	 * @url POST /login
	 */
	public function login()
	{
		$username = $_POST['username'];
		$password = $_POST['password']; //@todo remove since it is not needed anywhere
		return array("success" => "Logged in " . $username);
	}

	/**
	 * Gets the user by id or current user
	 *
	 * @url GET /users/$id
	 * @url GET /users/current
	 */
	public function getUser($id = null)
	{
		//if ($id) {
		//	$user = User::load($id); // possible user loading method
		//} else {
		//	$user = $_SESSION['user'];
		//}

		return array("id" => $id, "name" => null); // serializes object into JSON
	}

	/**
	 * Saves a user to the database
	 *
	 * @url POST /users
	 * e.g. $ curl -d '{"id":123,"name":"john doe","age":45}' http://server88/rest/users
	 *
	 * @url PUT /users/$id
	  * e.g. $ echo '{"id":123,"name":"john doe","age":45}' | curl -T - http://server88/rest/users/123
	*/
	public function saveUser($id = null, $data)
	{
		// ... validate $data properties such as $data->username, $data->firstName, etc.
		// $data->id = $id;
		// $user = User::saveUser($data); // saving the user to the database
		$user = array("id" => $id, "data" => $data);
		return $user; //  (array)$data; // returning the updated or newly created user object
	}

	/**
	 * Get Charts
	 * 
	 * @url GET /charts
	 * @url GET /charts/$id
	 * @url GET /charts/$id/$date
	 * @url GET /charts/$id/$date/$interval/
	 * @url GET /charts/$id/$date/$interval/$interval_months
	 */
	public function getCharts($id=null, $date=null, $interval = 30, $interval_months = 12)
	{
		echo "$id, $date, $interval, $interval_months";
	}

	/**
	 * Get SID
	 * 
	 * @url GET /session
	 */
	public function getSessionId()
	{
		session_start();

		if (!isset($_SESSION['sid'])) {
			$_SESSION['sid'] = session_id();
		}

		session_write_close();

		return session_id();
	}

	/**
	 * Throws an error
	 * 
	 * @url GET /error
	 */
	public function throwError() {
		throw new RestException(401, "Empty password not allowed");
	}

	/**
	 * Video Chat Room handling
	 * 
	 * @url GET /vchat
	 * @url GET /vchat/$screen_name/$screen_id
	 */
	public function videoChat($screen_name = null, $screen_id = null) {
		$TTL = 5;
		$KEYNAME_PREFIX = 'VCHAT_NAME_';
		$KEYNAME_PREFIX_LEN = strlen($KEYNAME_PREFIX);
		if ($screen_name && $screen_id) {
			$keyname = $KEYNAME_PREFIX . $screen_name;
			if ($fp = fopen("/var/lib/php5/vchat.lock", "c")) {
				$lock_ex = false;
				try {
					relock:
					if (flock($fp, $lock_ex ? LOCK_EX : LOCK_SH)) {
						$id = apc_fetch($keyname);
						if ($id === $screen_id) {
							// refresh cache
							apc_store($keyname, $screen_id, $TTL);
						} else if ($id === false) {
							if ($lock_ex) {
								// add new
								apc_store($keyname, $screen_id, $TTL);
							} else {
								$lock_ex = true;
								goto relock;
							}
						} else {
							// conflict!!!
							throw new RestException(409, "This user already exists");
						}
					}
				} finally {
					flock($fp, LOCK_UN);
					fclose($fp);
				}
			}
		}
		$res = [];
		foreach (new APCIterator('user', '/^' . $KEYNAME_PREFIX . '/') as $pair) {
			$res[] = substr($pair['key'], $KEYNAME_PREFIX_LEN);
		}
		return $res;
	}

	/**
	 * Text Chat
	 * 
	 * @url GET /tchat
	 * @url GET /tchat/$time_msec/$item_num
	 */
	public function textChatGet($time_msec = null, $item_num = null) {
		return RootController::textChat($time_msec, $item_num);
	}
	/**
	 * Text Chat
	 * 
	 * @url PUT /tchat
	 * @url PUT /tchat/$time_msec/$item_num
	 */
	public function textChatPut($time_msec = null, $item_num = null, $data) {
		return RootController::textChat($time_msec, $item_num, $data);
	}
	private static function textChat($time_msec = null, $item_num = null, $data = null) {
		$TTL = 0;
		$KEYNAME_PREFIX = 'TCHAT_ITEM_';
		$KEYNAME_PREFIX_LEN = strlen($KEYNAME_PREFIX);
		if ($data) {
			retry:
			$num = apc_inc('TCHAT_CURRENT_NUM');
			if ($num === false) {
				$max = 0;
				foreach (new APCIterator('user', '/^' . $KEYNAME_PREFIX . '/') as $pair) {
					$num = substr($pair['key'], $KEYNAME_PREFIX_LEN);
					if ($max < $num) {
						$max = $num;
					}
				}
				apc_store('TCHAT_CURRENT_NUM', $num = $max + 1);
			}
			$data->msec = round(microtime(true) * 1000);
			$data->num = $num;
			if (apc_add($KEYNAME_PREFIX . $num, $data) === false) {
				goto retry;
			}
			$min = -1;
			foreach (new APCIterator('user', '/^' . $KEYNAME_PREFIX . '/') as $pair) {
				$tmp = substr($pair['key'], $KEYNAME_PREFIX_LEN);
				if ($min > $tmp || $min < 0) {
					$min = $tmp;
				}
			}
			if ($min >= 0) {
				for ($i = $min; $i <= $num - 100; $i++) {
					apc_delete($KEYNAME_PREFIX . $i);
				}
			}
		}
		$res = [];
		foreach (new APCIterator('user', '/^' . $KEYNAME_PREFIX . '/') as $pair) {
			$val = $pair['value'];
			if (!$time_msec || !$item_num || $time_msec < $val->msec || 
				($time_msec == $val->msec && $item_num < $val->num)) {
					$res[] = $val;
			}
		}
		return $res;
	}
}
