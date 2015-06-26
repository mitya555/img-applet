REM F:
REM cd \Distr\ffmpeg-20150610-git-913685f-win32-static\bin
pushd \Distr\ffmpeg-20150610-git-913685f-win32-static\bin

REM ffmpeg -list_devices true -f dshow -i dummy
REM ffmpeg -sample_fmts
REM pause

REM ffmpeg -y -f dshow -r 30 -i video="Logitech QuickCam Communicate STX" -pix_fmt yuv420p -c:v libx264 -an -f flv rtmp://localhost/rtmp/webcam

REM https://trac.ffmpeg.org/wiki/EncodingForStreamingSites
REM ffmpeg -y -f dshow -r 25 -i video="Logitech QuickCam E3500" -c:v libx264 -preset veryfast -maxrate 1500k -bufsize 3000k -pix_fmt yuv420p -g 50 -an -f flv rtmp://localhost/rtmp/webcam

REM https://trac.ffmpeg.org/wiki/StreamingGuide
IF "%1" == "v" (
ffmpeg -r 25 ^
	-f dshow ^
	-i video="Logitech QuickCam E3500" ^
	-c:v libx264 -preset ultrafast -tune zerolatency -pix_fmt yuv420p -g 50 ^
	-an ^
	-f flv rtmp://10.44.40.80/rtmp/v
) ELSE IF "%1" == "a" (
ffmpeg ^
	-f dshow -audio_buffer_size 50 ^
	-i audio="Microphone (USB Audio Device)" ^
	-vn ^
	-c:a libmp3lame -async 1 ^
	-f flv rtmp://10.44.40.80/rtmp/a
REM	-c:a libspeex -async 1 -ar 16000 -ac 1 ^
REM libopus doesn't work with FLV::	-c:a libopus -ar 16000 ^
) ELSE (
ffmpeg -r 25 ^
	-f dshow -audio_buffer_size 50 ^
	-i video="Logitech QuickCam E3500":audio="Microphone (USB Audio Device)" ^
	-c:v libx264 -preset ultrafast -tune zerolatency -pix_fmt yuv420p -g 60 ^
	-c:a libmp3lame -async 1 -ab 24k -ar 22050 ^
	-maxrate 750k -bufsize 3000k -rtbufsize 5000k ^
	-f flv rtmp://10.44.40.80/rtmp/av
)

REM http://x264dev.multimedia.cx/archives/249
REM http://www.maartenbaert.be/simplescreenrecorder/live-streaming/

REM cd /D %userprofile%\Desktop
popd
