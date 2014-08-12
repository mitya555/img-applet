package ffmpeg;

import img_applet.JarLib;

import java.io.File;
import java.io.IOException;

import javax.xml.bind.DatatypeConverter;

public class FFmpeg {
	
	static public File exe;
	
	static public String base64(byte[] buf) {
		return DatatypeConverter.printBase64Binary(buf);
	}
	
	static {
		// win : load file 'ffmpeg.exe'
		try {
			exe = JarLib.loadFile(FFmpeg.class, "ffmpeg.exe");
		} catch (UnsatisfiedLinkError | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
