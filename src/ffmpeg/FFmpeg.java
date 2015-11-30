package ffmpeg;

import img_applet.JarLib;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class FFmpeg {
	
	static public File exe;
	
	static public void load(URL baseUrl) {
		try {
			String localVer = JarLib.getLocal("ffmpeg.build.txt"), remoteVer = null;
			if (localVer != null)
				remoteVer = JarLib.getUrl(new URL(baseUrl, "ffmpeg.build.txt"));
			if (localVer == null || remoteVer.compareToIgnoreCase(localVer) != 0) {
				JarLib.deleteLocal("ffmpeg.build.txt");
				JarLib.deleteLocal("ffmpeg.exe");
			}
			exe = JarLib.loadFile(new URL(baseUrl, "ffmpeg.zip"), "ffmpeg.exe", true);
			JarLib.loadFile(new URL(baseUrl, "ffmpeg.build.txt"), "ffmpeg.build.txt", true);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
//	static {
//		// win : load file 'ffmpeg.exe'
//		try {
//			exe = JarLib.loadFile(FFmpeg.class, "ffmpeg.exe", true);
//		} catch (UnsatisfiedLinkError | IOException e) {
//			e.printStackTrace();
//		}
//	}
}
