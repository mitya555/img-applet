package img_applet;

import java.awt.Color;
import java.awt.TextArea;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.swing.JApplet;
import javax.swing.SwingUtilities;

import ffmpeg.FFmpeg;


@SuppressWarnings("serial")
public class ImgApplet extends JApplet implements Runnable {
	
	public TextArea console;
	private String rtmp;
	private Process ffmp;
	//private InputStr

	@Override
	public void run() {
		getContentPane().add(this.console = new TextArea());
		this.console.setEditable(false);
		getContentPane().setBackground(Color.WHITE);
//		System.out.println(FFmpeg.exe.getAbsolutePath());
//		console.append(FFmpeg.exe.getAbsolutePath() + "\n");
	}

	@Override
	public void init() {
		
		super.init();
		
		this.rtmp = getParameter("rtmp");
		
        //Execute a job on the event-dispatching thread:
        //creating this applet's GUI.
//        try {
//            javax.swing.SwingUtilities.invokeAndWait(this);
//        } catch (Exception e) {
//            System.err.println("Failed to create GUI");
//            e.printStackTrace();
//        }
		SwingUtilities.invokeLater(this);
	}

	@Override
	public void start() {
		
		super.start();
		
		ProcessBuilder pb = new ProcessBuilder(FFmpeg.exe.getName(), "-i", rtmp, "-vcodec mjpeg", "-");
//		Map<String, String> env = pb.environment();
//		env.put("VAR1", "myValue");
//		env.remove("OTHERVAR");
//		env.put("VAR2", env.get("VAR1") + "suffix");
		pb.directory(FFmpeg.exe.getParentFile());
//		File log = new File("log");
//		pb.redirectErrorStream(true);
//		pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
		try {
			this.ffmp = pb.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		assert pb.redirectInput() == ProcessBuilder.Redirect.PIPE;
//		assert pb.redirectOutput().file() == log;
//		assert p.getInputStream().read() == -1;
	}

	@Override
	public void stop() {
		
		this.ffmp.destroy();
		
		super.stop();
	}

	@Override
	public void destroy() {
		
		this.ffmp.destroy();
		
		super.destroy();
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
