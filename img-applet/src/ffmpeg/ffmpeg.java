package ffmpeg;

import img_applet.JarLib;

import java.io.File;
import java.io.IOException;

public class ffmpeg {
	
	static public File ffmpeg;
	
	static {
		// win : load file 'ffmpeg.exe'
		try {
			ffmpeg = JarLib.loadFile(ffmpeg.class, "ffmpeg.exe");
		} catch (UnsatisfiedLinkError | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
