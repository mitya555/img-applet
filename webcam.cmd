F:
cd F:\Distr\ffmpeg-20140810-git-e18d9d9-win32-static\bin

REM ffmpeg -list_devices true -f dshow -i dummy
REM ffmpeg -sample_fmts
REM pause

REM ffmpeg -y -f dshow -r 30 -i video="Logitech QuickCam Communicate STX" -pix_fmt yuv420p -c:v libx264 -an -f flv rtmp://localhost/rtmp/webcam

REM https://trac.ffmpeg.org/wiki/EncodingForStreamingSites
REM ffmpeg -y -f dshow -r 25 -i video="Logitech QuickCam E3500" -c:v libx264 -preset veryfast -maxrate 1500k -bufsize 3000k -pix_fmt yuv420p -g 50 -an -f flv rtmp://localhost/rtmp/webcam

REM https://trac.ffmpeg.org/wiki/StreamingGuide
ffmpeg -y -f dshow -r 25 -i video="Logitech QuickCam E3500" -c:v libx264 -preset ultrafast -tune zerolatency -pix_fmt yuv420p -g 50 -an -f flv rtmp://localhost/rtmp/webcam

REM http://x264dev.multimedia.cx/archives/249
REM http://www.maartenbaert.be/simplescreenrecorder/live-streaming/

cd "C:\Documents and Settings\Mitya\Desktop"
c:
