package img_applet;

import java.awt.Button;
import java.awt.Color;
import java.awt.FlowLayout;
//import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
//import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
//import java.io.OutputStreamWriter;
//import java.io.InputStreamReader;
import java.util.Arrays;
//import java.util.Map;


import javax.swing.JApplet;
import javax.swing.SwingUtilities;
import javax.xml.bind.DatatypeConverter;

import ffmpeg.FFmpeg;


@SuppressWarnings("serial")
public class ImgApplet extends JApplet implements Runnable {
	
	private String rtmp, qscale;
	private Process ffmp;
	private Thread ffmt;
//	private volatile boolean ffm_stop;
	private static final int MAX_FRAME_SIZE = 100000;
	private volatile byte[] b1 = new byte[MAX_FRAME_SIZE], b2 = new byte[MAX_FRAME_SIZE];
	private volatile int sn, sn1, sn2, l1, l2;

	@Override
	public void run() {
		
		FlowLayout cont = new FlowLayout(FlowLayout.CENTER, 10, 10);
		getContentPane().setLayout(cont);
		
		Button button = new Button();
		getContentPane().add(button);
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) { stop(); }
		});
		button.setLabel("Stop");
		
		button = new Button();
		getContentPane().add(button);
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) { start(); }
		});
		button.setLabel("Start");
		
		getContentPane().setBackground(Color.WHITE);
//		System.out.println(FFmpeg.exe.getAbsolutePath());
//		console.append(FFmpeg.exe.getAbsolutePath() + "\n");
	}
	
	private boolean tryParseFloat(String val) { try { Float.parseFloat(val); return true; } catch (Throwable ex) { return false; } }

	@Override
	public void init() {
		
		super.init();
		
		this.rtmp = getParameter("rtmp");
		this.qscale = getParameter("qscale");
		if (!tryParseFloat(this.qscale))
			this.qscale = "0.0";
		
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
		
		if (this.ffmt != null && this.ffmt.isAlive())
			return;
		
		ProcessBuilder pb = new ProcessBuilder(FFmpeg.exe.getAbsolutePath(),
				/*"-analyzeduration", "1000", "-probesize", "1000",*/
				"-f", "flv", /*"-flv_metadata", "1",*/ "-i", rtmp,
				"-an", "-c:v", "mjpeg", "-q:v", qscale, "-f", "mjpeg",
				"pipe:1");
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
					sn = sn1 = sn2 = 0;
					int res = 0;
					MjpegInputStream in_ = null;
					try {
						in_ = new MjpegInputStream(ffmp.getInputStream());
						while (res != -1/* && !ffm_stop*/)
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
						System.out.println("Thread processing output from ffmpeg is ending...");
					}
					finally {
						if (in_ != null)
							try {
								in_.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						System.out.println("Thread processing output from ffmpeg has ended.");
					}
				}
			});
			this.ffmt.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
//		assert pb.redirectInput() == ProcessBuilder.Redirect.PIPE;
//		assert pb.redirectOutput().file() == log;
//		assert p.getInputStream().read() == -1;
	}

	private void quitProcess() {
		try {
			OutputStream out_ = ffmp.getOutputStream();
			if (out_ != null) {
				out_.write('q');
				out_.flush();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	private void killProcess() {
		
//		this.ffm_stop = true;
//		if (this.ffmt != null)
//			this.ffmt.interrupt();

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
	
	public int getSN() { return sn; }

	@Override
	public void stop() {
		
		if (this.ffmt.isAlive()) {
			quitProcess();
			try {
				this.ffmt.join(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if (this.ffmt.isAlive())
			killProcess();
		
		super.stop();
	}

	@Override
	public void destroy() {
		
		killProcess();
		
		super.destroy();
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
