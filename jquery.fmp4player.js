/**
 * Provides requestAnimationFrame in a cross browser way.
 * http://paulirish.com/2011/requestanimationframe-for-smart-animating/
 */
if ( !window.requestAnimationFrame ) {
	window.requestAnimationFrame = (function() {
		return window.webkitRequestAnimationFrame ||
		window.mozRequestAnimationFrame ||
		window.oRequestAnimationFrame ||
		window.msRequestAnimationFrame ||
		function( /* function FrameRequestCallback */ callback, /* DOMElement Element */ element ) {
			window.setTimeout( callback, 1000 / 60 );
		};
	})();
}

/**
 * fmp4player jQuery plug-in.
 */
(function ($) {

var defaults = {
  appletParams: {
	//"ffmpeg-i": "rtmp://10.44.41.97/rtmp/webcam",
	"ffmpeg-i": "rtmp://europaplus.cdnvideo.ru:1935/europaplus-live/eptv_main.sdp",
	//"ffmpeg-i": "rtmp://85.132.78.6:1935/live/muztv.stream",
	//"ffmpeg-i": "http://83.139.104.101/Content/HLS/Live/Channel(Sk_1)/index.m3u8",
	//"ffmpeg-map": "0:6",
	"ffmpeg-c:a": "libmp3lame",
	"ffmpeg-c:v": "mjpeg",
	"ffmpeg-q:v": "0.0",
/* �-vsync parameter�
    Video sync method. For compatibility reasons old values can be specified as numbers. 
    Newly added values will have to be specified as strings always.
    �0, passthrough� - Each frame is passed with its timestamp from the demuxer to the muxer. 
    �1, cfr� - Frames will be duplicated and dropped to achieve exactly the requested constant frame rate. 
    �2, vfr� - Frames are passed through with their timestamp or dropped so as to prevent 2 frames from having the same timestamp. 
    �drop� - As passthrough but destroys all timestamps, making the muxer generate fresh timestamps based on frame-rate. 
    �-1, auto� - Chooses between 1 and 2 depending on muxer capabilities. This is the default method.
*/
	"ffmpeg-vsync": "0",
	"ffmpeg-movflags": "frag_keyframe+empty_moov",
	"ffmpeg-f:o": "mov", // "mp4", // .MOV can contain PCM audio (unlike .MP4) and plays better as well :)
	"demux-fMP4": "yes",
	"process-frame-callback": "showVideoFrame",
	"process-frame-number-of-consumer-threads": "4",
	"max-video-buffer-count": "0", // no limit on filesystem buffering
/*	"ffmpeg-muxpreload": "10",
	"ffmpeg-muxdelay": "10",
	"ffmpeg-loglevel": "warning",*/
	"debug": "yes",
	"debug-ffmpeg": "yes",
/* 
	"ffmpeg-map": "0:0",
	"ffmpeg-an": "",

	"ffmpeg-re": "",
	"ffmpeg-frames:d": "3",
	"ffmpeg-c:a": "copy",
	"ffmpeg-c:v": "copy",
	"ffmpeg-f:o": "mp4",
	"ffmpeg-y": "",
	"ffmpeg-o": "output.mp4",
	"buffer-grow-factor": "1.0",
	"max-memory-buffer-count": "30",
	"max-video-buffer-count": "300",
	"max-video-buffer-count": "0"
*/
  },
  audioControls: "no",
  debug: "yes"
};

$.fn.fmp4player = function (options) {

  var opts = $.extend(true, {}, $.fn.fmp4player.defaults, options);
	
  var objParams = [], embedParams = [];
  for (var i in opts.appletParams) {
	objParams.push('<param name="' + i + '" value="' + opts.appletParams[i] + '">');
	embedParams.push(i + '="' + opts.appletParams[i] + '"');
  }

  return this.each(function () {

	var $cont = $(this), idPrefix = (this.id ? this.id : 'e' + Math.floor(Math.random() * 1000000000000000)) + '-';

	$cont.append('<object id="' + idPrefix + 'img-applet-ie"\
  classid="clsid:8AD9C840-044E-11D1-B3E9-00805F499D93"\
  width="100" height="40">\
  <param name="archive" value="img-applet.jar">\
  <param name="code" value="img_applet.ImgApplet">\
  ' + objParams.join('\r\n  ') + '\
  <comment>\
    <embed id="' + idPrefix + 'img-applet"\
      type="application/x-java-applet"\
      width="100" height="40" \
      archive="img-applet.jar"\
      code="img_applet.ImgApplet"\
      pluginspage="http://java.com/download/"\
      ' + embedParams.join('\r\n      ') + ' />\
    </comment>\
  </object>\
<img id="' + idPrefix + 'image0" width="640" height="480" style="visibility: hidden; display: none;" />\
<img id="' + idPrefix + 'image1" width="640" height="480" style="visibility: hidden; display: none;" />\
<br />\
<canvas id="' + idPrefix + 'videoImage" width="640" height="480"></canvas>\
<audio id="' + idPrefix + 'audio" autoplay="autoplay" crossorigin="anonymous"' + (!isNo(opts['audioControls']) ? ' controls="controls"' : '') + '>Your browser does not support the <code>audio</code> element.</audio>\
<div id="' + idPrefix + 'msg" style="font-family:Courier New;"></div>\
'); // <audio id="audio" autoplay="autoplay" crossorigin="anonymous">Your browser does not support the <code>audio</code> element.</audio><!-- controls="controls" -->
	
	// global variables
	var audio, image, videoImage, videoImageContext, applet, applet_ie, applet_, prev_sn = 0, last_img = -1;

	// assign variables to HTML elements
	audio = document.getElementById( idPrefix + 'audio' );
	image = [ document.getElementById( idPrefix + 'image0' ), document.getElementById( idPrefix + 'image1' ) ];
	videoImage = document.getElementById( idPrefix + 'videoImage' );
	videoImageContext = videoImage.getContext( '2d' );
	applet = document.getElementById( idPrefix + 'img-applet' );
	applet_ie = document.getElementById( idPrefix + 'img-applet-ie' );

	function checkApplet() {
		if (!applet_) {
			if (applet && applet.getSN)
				applet_ = applet;
			else if (applet_ie && typeof(applet_ie.getSN) === "unknown")
				applet_ = applet_ie;
		}
		return applet_;
	}

	//background color if no video present
	videoImageContext.fillStyle = '#005337';
	videoImageContext.fillRect( 0, 0, videoImage.width, videoImage.height );				

	image[0].onload = image[1].onload = function() {
		videoImageContext.drawImage( this, 0, 0, videoImage.width, videoImage.height );
	};

	function tns(ns) { switch (ns) { case 0: return "NETWORK_EMPTY"; case 1: return "NETWORK_IDLE"; case 2: return "NETWORK_LOADING"; case 3: return "NETWORK_NO_SOURCE"; default: return "UNKNOWN"; } }
	function trs(rs) { switch (rs) { case 0: return "HAVE_NOTHING"; case 1: return "HAVE_METADATA"; case 2: return "HAVE_CURRENT_DATA"; case 3: return "HAVE_FUTURE_DATA"; case 4: return "HAVE_ENOUGH_DATA"; default: return "UNKNOWN"; } }
	function terr(err) { switch (err) { case 1: return "MEDIA_ERR_ABORTED"; case 2: return "MEDIA_ERR_NETWORK"; case 3: return "MEDIA_ERR_DECODE"; case 4: case 5: return "MEDIA_ERR_SRC_NOT_SUPPORTED"; default: return "UNKNOWN"; } }
	function conLog(e, t) {
		var timeranges = "";
		switch (t) {
		case 1: // buffered
			timeranges = " buffered:";
			for (var i = 0; i < e.buffered.length; i++)
				timeranges += " time range " + i + ": start=" + e.buffered.start(i) + "; end=" + e.buffered.end(i) + (i == e.buffered.length - 1 ? "" : ";");
			break;
		case 2: // played
			timeranges = " played:";
			if (e.played != null)
				for (var i = 0; i < e.played.length; i++)
					timeranges += " time range " + i + ": start=" + e.played.start(i) + "; end=" + e.played.end(i) + (i == e.played.length - 1 ? "" : ";");
			break;
		}
		return " readyState: " + e.readyState + "(" + trs(e.readyState) + "); networkState: " + e.networkState + "(" + tns(e.networkState) + ");" + timeranges; 
	}
	function conErr(e) { return " Error: " + e.error.code + "(" + terr(e.error.code) + ");" + conLog(e); }
	function isNo(str) { return !str || str.toLowerCase() === 'no' || str.toLowerCase() === 'false'; }
	if (!isNo(opts['debug'])) {
		audio.onabort             = function() { console.log("event: abort;            " + conLog(this)); };
		audio.oncanplay           = function() { console.log("event: canplay;          " + conLog(this)); };
		audio.oncanplaythrough    = function() { console.log("event: canplaythrough;   " + conLog(this)); };
		audio.ondurationchange    = function() { console.log("event: durationchange;   " + conLog(this, 1)); }; // buffered
		audio.onemptied           = function() { console.log("event: emptied;          " + conLog(this)); };
		audio.onended             = function() { console.log("event: ended;            " + conLog(this)); };
		audio.onerror             = function() { console.error("event: error;          " + conErr(this)); };
		audio.onloadeddata        = function() { console.log("event: loadeddata;       " + conLog(this)); };
		audio.onloadedmetadata    = function() { console.log("event: loadedmetadata;   " + conLog(this)); };
		audio.onloadstart         = function() { console.log("event: loadstart;        " + conLog(this)); };
		audio.onmozaudioavailable = function() { console.log("event: mozaudioavailable;" + conLog(this)); };
		audio.onpause             = function() { console.log("event: pause;            " + conLog(this)); };
		audio.onplay              = function() { console.log("event: play;             " + conLog(this)); };
		audio.onplaying           = function() { console.log("event: playing;          " + conLog(this)); };
		audio.onprogress          = function() { console.log("event: progress;         " + conLog(this, 1)); }; // buffered
		audio.onratechange        = function() { console.log("event: ratechange;       " + conLog(this)); };
		audio.onstalled           = function() { console.log("event: stalled;          " + conLog(this)); };
		audio.onseeked            = function() { console.log("event: seeked;           " + conLog(this)); };
		audio.onseeking           = function() { console.log("event: seeking;          " + conLog(this)); };
		audio.onsuspend           = function() { console.log("event: suspend;          " + conLog(this)); };
		audio.ontimeupdate        = function() { console.log("event: timeupdate;       " + conLog(this, 2)); }; // played
		audio.onwaiting           = function() { console.log("event: waiting;          " + conLog(this)); };
		audio.onvolumechange      = function() { console.log("event: volumechange;     " + conLog(this)); };
	}

	var cnt = 0, playing = false, timeScale = 0, timeLine = 0, initial_ts = 0, prev_ts = 0, videoInfo, hasAudio = false;

	var perf_prev = 0, perf_msg = document.getElementById(idPrefix + "msg"), perf_msg_lines = 10;
	function add_perf_msg(txt) {
		if (perf_msg.childNodes.length >= perf_msg_lines)
			perf_msg.removeChild(perf_msg.firstChild);
		perf_msg.insertAdjacentHTML('beforeend', "<div>" + txt + "</div>");
	}
	function check_performance() {
		var perf_now = performance.now();
		if (perf_prev > 0 && perf_now - perf_prev > 20)
			add_perf_msg("# " + cnt + "; " + (perf_now - perf_prev).toFixed(2));
		perf_prev = perf_now;
	}

	var videoConsumerThreadsInJava = !!opts.appletParams["process-frame-callback"];
	if (videoConsumerThreadsInJava) {
		// frame consumer threads in Java with the callback function below.
		// has to be configured in the applet parameters
		var init = true;
		window[opts.appletParams["process-frame-callback"]] = function(ffmpeg_id, frame_sn, dataUri) {
			image[last_img = (last_img + 1) % 2].src = dataUri;
			if (init && checkApplet()) {
				init = false;
				getVideoTrackInfo();
			}
		};
	}

	// frame consumer in javascript:
	// start the loop
	animate();

	function animate() {
		requestAnimationFrame( animate );
		render();
	}

	var old_ontimeupdate = audio.ontimeupdate;
	audio.ontimeupdate = function() {
		if (this.played != null && this.played.length > 0 && timeLine > 0)
			timeLine = performance.now() - (this.played.end(0) - this.played.start(0)) * 1000;
		if (old_ontimeupdate)
			old_ontimeupdate.call(this);
	};

	function startAudioIfAppletIsPlaying() {
		if (applet_.isPlaying() && applet_.getSN() > 0) {
			playing = true;
			audio.src = applet_.startHttpServer();
		}	
	}

	function getVideoTrackInfo() {
		videoInfo = applet_.getVideoTrackInfo();
		timeScale = videoInfo.timeScale;
		if (videoInfo.width > 0)
			image[0].width = image[1].width = videoImage.width = videoInfo.width;
		if (videoInfo.height > 0)
			image[0].height = image[1].height = videoImage.height = videoInfo.height;
		hasAudio = videoInfo.hasAudio;
	}

	function render() {
		cnt++;
		//check_performance();
		if (!videoConsumerThreadsInJava && checkApplet()) {
			try {
				var sn_ = applet_.getVideoSN();
				if (/*sn_ != prev_sn &&*/ sn_ > 0) {
					if (!videoInfo)
						getVideoTrackInfo();
					if (hasAudio) {
						var ts_ = applet_.getVideoTimestamp(),
							tl_ = performance.now(),
							dt_ = (ts_ - initial_ts) * 1000 / timeScale;
						//if (prev_ts > 0 && (ts_ - prev_ts) * 1000 / timeScale > 100)
						//	add_perf_msg("sn = " + sn_ + "; ts = " + (ts_ - prev_ts));
						if (timeScale > 0 && (ts_ == 0 || timeLine == 0 || dt_ <= tl_ - timeLine)) {
							if (ts_ == 0 || timeLine == 0) {
								timeLine = tl_;
								initial_ts = ts_;
							}
							prev_ts = ts_;
							prev_sn = sn_;
							//add_perf_msg("sn = " + sn_ + "; ts = " + ts_);
							image[last_img = (last_img + 1) % 2].src = applet_.getVideoDataURI();
						}
					} else if (sn_ > prev_sn) {
						prev_sn = sn_;
						image[last_img = (last_img + 1) % 2].src = applet_.getVideoDataURI();
					}
				} else if (videoInfo) {
					timeScale = timeLine = initial_ts = prev_ts = 0;
					videoInfo = null;
				}
			} catch (ex) {
				console.error("render video", ex.message);
			}
		}
		if (playing) {
			try {
				if (!(playing = applet_.isStreaming()))
					startAudioIfAppletIsPlaying();
			} catch (ex) {
				console.error("render audio", ex.message);
			}
		} else if (checkApplet()) {
			try {
				if (applet_.isPlaying() && applet_.getSN() > 0)
					startAudioIfAppletIsPlaying();
			} catch (ex) {
				console.error("render audio", ex.message);
			}
		}
	}
  });

};

$.fn.fmp4player.defaults = defaults;

})(jQuery);
