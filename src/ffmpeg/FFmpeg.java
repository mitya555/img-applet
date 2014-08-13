package ffmpeg;

import img_applet.JarLib;

import java.io.File;
import java.io.IOException;

public class FFmpeg {
	
	static public File exe;
	
	static {
		// win : load file 'ffmpeg.exe'
		try {
			exe = JarLib.loadFile(FFmpeg.class, "ffmpeg.exe", true);
		} catch (UnsatisfiedLinkError | IOException e) {
			e.printStackTrace();
		}
	}
}
