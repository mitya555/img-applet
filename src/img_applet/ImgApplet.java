package img_applet;

import java.awt.Button;
import java.awt.Color;
import java.awt.FlowLayout;
//import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
//import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
//import java.io.OutputStreamWriter;
//import java.io.InputStreamReader;
import java.util.Arrays;
//import java.util.Map;



import java.util.List;

import javax.swing.JApplet;
import javax.swing.SwingUtilities;
import javax.xml.bind.DatatypeConverter;

import ffmpeg.FFmpeg;

@SuppressWarnings("serial")
public class ImgApplet extends JApplet implements Runnable {
	
//	private String rtmp, qscale, vsync;
//	private boolean re; 
	private Process ffmp;
	private Thread ffmt;
//	private volatile boolean ffm_stop;
	private static final int MAX_FRAME_SIZE = 300000;
	private volatile byte[] b1 = new byte[MAX_FRAME_SIZE], b2 = new byte[MAX_FRAME_SIZE];
	private volatile int sn, sn1, sn2, l1, l2;
	
	private void addButton(String label, ActionListener click) {
		Button button = new Button();
		getContentPane().add(button);
		button.addActionListener(click);
		button.setLabel(label);
		button.setEnabled(true);
	}

	@Override
	public void run() {
		
		FlowLayout cont = new FlowLayout(FlowLayout.CENTER, 10, 10);
		getContentPane().setLayout(cont);
		
		addButton("Stop", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) { stop(); }
		});
		
		addButton("Start", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) { start(); }
		});
		
		getContentPane().setBackground(Color.WHITE);
//		System.out.println(FFmpeg.exe.getAbsolutePath());
//		console.append(FFmpeg.exe.getAbsolutePath() + "\n");
	}
	
//	private static boolean tryParseFloat(String val) { try { Float.parseFloat(val); return true; } catch (Throwable ex) { return false; } }
    private static boolean strEmpty(String str) { return str == null || str.length() == 0; }
    private static boolean isNo(String str) { return str == null || "No".equalsIgnoreCase(str) || "False".equalsIgnoreCase(str); }

    private static boolean DEBUG = true;
    private static void debug(String dbg) { if (DEBUG) System.out.println(dbg); }
    private static void debug(String dbg, String inf) { if (DEBUG) System.out.println(dbg); else System.out.println(inf); }

    private class BufferedConsoleOut
    {
    	public BufferedConsoleOut(OutputStream out, int len) { this.out = out; buf = new byte[len]; }
    	public BufferedConsoleOut(OutputStream out) { this(out, 500); }
    	public BufferedConsoleOut() { this(System.out); }    	
    	private OutputStream out;
		private byte[] buf;
		private int len = 0;
		public void flush() throws IOException { if (len > 0) out.write(buf, 0, len); len = 0; }
		public void write(int b) throws IOException { buf[len++] = (byte)b; if (b == 10 || len == 500) flush(); }
		public void println(String s) throws IOException { for (byte b : s.getBytes()) write(b); write(13); write(10); }
    }

	@Override
	public void init() {
		
		super.init();
		
		DEBUG = !isNo(getParameter("debug"));
		
//		this.rtmp = getParameter("rtmp");
//		this.qscale = getParameter("qscale");
//		if (!tryParseFloat(this.qscale))
//			this.qscale = "0.0";
//		this.vsync = getParameter("vsync");
//		if (strEmpty(this.vsync))
//			this.vsync = "-1"; // "auto"
//		this.re = !isNo(getParameter("re"));
		
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

	private static final String PARAM_PREFIX = "ffmpeg-"; 
	private void addOptNV(String param, String opt, List<String> command, String dflt) {
		String _param = getParameter(PARAM_PREFIX + param);
		if (!strEmpty(_param) || (_param == null && !strEmpty(dflt))) {
			if (!strEmpty(opt))
				command.add("-" + opt);
			command.add(!strEmpty(_param) ? _param : dflt);
		}
	}
	private void addOptNV(String param, String opt, List<String> command) { addOptNV(param, opt, command, null); }
	private void addOptNV(String name, List<String> command, String dflt) { addOptNV(name, name, command, dflt); }
	private void addOptNV(String name, List<String> command) { addOptNV(name, name, command); }
	private void addOpt_V(String name, List<String> command, String dflt) { addOptNV(name, null, command, dflt); }
	private void addOptN_(String name, List<String> command) { if (!isNo(getParameter(PARAM_PREFIX + name))) command.add("-" + name); }
	
	@Override
	public void start() {
		
		super.start();
		
		if (this.ffmt != null && this.ffmt.isAlive())
			return;
		
		List<String> command = new ArrayList<String>();
		command.add(FFmpeg.exe.getAbsolutePath());
//		command.addAll(Arrays.asList(new String[] {
//				/*"-analyzeduration", "1000", "-probesize", "1000",*/
//				"-f", "flv", /*"-flv_metadata", "1",*/ "-i", rtmp,
//				"-an", "-c:v", "mjpeg", "-q:v", qscale, "-vsync", vsync,
//				"-f", "mjpeg", "pipe:1"
//		}));
		addOptNV("analyzeduration", command);
		addOptNV("probesize", command);
		addOptN_("re", command);
		addOptNV("f:i", "f", command, "flv");
		addOptNV("flv_metadata", command);
		addOptNV("i", command);
		addOptN_("an", command);
		addOptN_("vn", command);
		addOptNV("c:v", command, "mjpeg");
		addOptNV("q:v", command);
		addOptNV("vsync", command);
		addOptNV("f:o", "f", command, "mjpeg");
		addOptNV("muxdelay", command);
		addOptNV("muxpreload", command);
		addOpt_V("o", command, "pipe:1");
		addOptNV("loglevel", command);

		ProcessBuilder pb = new ProcessBuilder(command);
//		Map<String, String> env = pb.environment();
//		env.put("VAR1", "myValue");
//		env.remove("OTHERVAR");
//		env.put("VAR2", env.get("VAR1") + "suffix");
		pb.directory(FFmpeg.exe.getParentFile());
//		File log = new File("log");
//		pb.redirectErrorStream(true);
//		pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
//		assert pb.redirectInput() == ProcessBuilder.Redirect.PIPE;
//		assert pb.redirectOutput().file() == log;
		if (!DEBUG)
			pb.redirectError(ProcessBuilder.Redirect.INHERIT);
		try {
			this.ffmp = pb.start();
			debug(">" + command, "FFMPEG process started.");
			this.ffmt = new Thread(new Runnable() {
				@Override
				public void run() {
					sn = sn1 = sn2 = prev_sn = 0;
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
						debug("Thread processing output from ffmpeg is ending...");
					}
					finally {
						if (in_ != null)
							try {
								in_.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						debug("Thread processing output from ffmpeg has ended.", "FFMPEG process terminated.");
					}
				}
			});
			this.ffmt.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
//		assert ffmp.getInputStream().read() == -1;
		if (DEBUG)
			new Thread(new Runnable() {
				@Override
				public void run() {
					int res, prev = 0;
					BufferedConsoleOut conOut = new BufferedConsoleOut();
					InputStream in_ = ffmp.getErrorStream();
					try {
						while ((res = in_.read()) != -1) {
							if (prev == 13 && res != 10) {
//								System.out.write(10);
								conOut.write(10);
							}
//							System.out.write(res);
							conOut.write(res);
							prev = res;
						}
						conOut.flush();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}).start();
	}

	private void quitProcess() {
		try {
			OutputStream out_ = ffmp.getOutputStream();
			if (out_ != null) {
				out_.write('q');
				out_.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
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
	private int prev_sn;
	private BufferedConsoleOut getDataUriErr = new BufferedConsoleOut(System.err); 
	public String getDataURI() throws IOException {
		if (sn1 == 0 && sn2 == 0)
			return null;
		if (DEBUG) {
			int cur_sn = sn1 > sn2 ? sn1 : sn2;
			if (cur_sn - prev_sn > 1) {
				getDataUriErr.println("Dropped frame" + (cur_sn - prev_sn == 2 ? " " + (cur_sn - 1) : "s " + (prev_sn + 1) + " - " + (cur_sn - 1)));
			}
			prev_sn = cur_sn;
		}
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
