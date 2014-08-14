package img_applet;

import java.awt.Color;
import java.awt.TextArea;
//import java.io.File;
import java.io.IOException;
//import java.io.InputStreamReader;
import java.util.Arrays;
//import java.util.Map;

import javax.swing.JApplet;
import javax.swing.SwingUtilities;
import javax.xml.bind.DatatypeConverter;

import ffmpeg.FFmpeg;


@SuppressWarnings("serial")
public class ImgApplet extends JApplet implements Runnable {
	
	public TextArea console;
	private String rtmp;
	private Process ffmp;
	private Thread ffmt; // , errt;
	private volatile boolean ffm_stop;
	private static final int MAX_FRAME_SIZE = 100000;
	private volatile byte[] b1 = new byte[MAX_FRAME_SIZE], b2 = new byte[MAX_FRAME_SIZE];
	private volatile int sn, sn1, sn2, l1, l2;

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
		
		ProcessBuilder pb = new ProcessBuilder("\"" + FFmpeg.exe.getAbsolutePath() + "\"", /*"-f", "h264",*/ "-i", rtmp, "-c:v", "mjpeg", "-f", "mjpeg", "pipe:1");
//		Map<String, String> env = pb.environment();
//		env.put("VAR1", "myValue");
//		env.remove("OTHERVAR");
//		env.put("VAR2", env.get("VAR1") + "suffix");
		pb.directory(FFmpeg.exe.getParentFile());
//		File log = new File("log");
//		pb.redirectErrorStream(true);
//		pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
		pb.redirectError(ProcessBuilder.Redirect.INHERIT);
		try {
			this.ffmp = pb.start();
			this.ffmt = new Thread(new Runnable() {
				@Override
				public void run() {
					int res = 0;
					MjpegInputStream in_ = null;
					try {
						in_ = new MjpegInputStream(ffmp.getInputStream());
						while (res != -1 && !ffm_stop)
							try {
								if (sn1 <= sn2) {
									res = l1 = in_.readFrame(b1);
									sn1 = ++sn;
								}
								else {
									res = l2 = in_.readFrame(b2);
									sn2 = ++sn;
								}
							} catch (IOException e) {
								e.printStackTrace();
							}
					}
					finally {
						if (in_ != null)
							try {
								in_.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
					}
				}
			});
			this.ffmt.start();
//			this.errt = new Thread(new Runnable() {
//				@Override
//				public void run() {
//					int res = 0;
//					InputStreamReader err_ = null;
//					try {
//						err_ = new InputStreamReader(ffmp.getErrorStream());
//						while (res != -1 && !ffm_stop)
//							try {
//								res = err_.read();
//								if (res != -1)
//									System.out.print((char)res);
//							} catch (IOException e) {
//								e.printStackTrace();
//							}
//					}
//					finally {
//						if (err_ != null)
//							try {
//								err_.close();
//							} catch (IOException e) {
//								e.printStackTrace();
//							}
//					}
//				}
//			});
//			this.errt.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
//		assert pb.redirectInput() == ProcessBuilder.Redirect.PIPE;
//		assert pb.redirectOutput().file() == log;
//		assert p.getInputStream().read() == -1;
	}

	private void stopProcessing() {
		this.ffm_stop = true;
		if (this.ffmt != null)
			this.ffmt.interrupt();
//		if (this.errt != null)
//			this.errt.interrupt();
		this.ffmp.destroy();
	}
	
	private StringBuilder sb = new StringBuilder("data:image/jpeg;base64,");
	private int sb_len = sb.length();
	
	public String getDataURI() {
		if (sn1 == 0 && sn2 == 0)
			return null;
		sb.setLength(sb_len);
		return sb.append(DatatypeConverter.printBase64Binary(Arrays.copyOf(sn1 > sn2 ? b1 : b2, sn1 > sn2 ? l1 : l2))).toString();
	}

	@Override
	public void stop() {
		
		stopProcessing();
		
		super.stop();
	}

	@Override
	public void destroy() {
		
		stopProcessing();
		
		super.destroy();
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
