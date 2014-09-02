package img_applet;

import java.awt.Button;
import java.awt.Color;
import java.awt.FlowLayout;
//import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FilterInputStream;
import java.io.BufferedInputStream;
//import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
//import java.io.OutputStreamWriter;
//import java.io.InputStreamReader;
import java.util.Arrays;
//import java.util.Map;



import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JApplet;
import javax.swing.SwingUtilities;
import javax.xml.bind.DatatypeConverter;

import ffmpeg.FFmpeg;

@SuppressWarnings("serial")
public class ImgApplet extends JApplet implements Runnable {
	
	private class Buffer
	{
		public volatile byte[] b;
		public volatile int sn, len;
		public final int size;
		public Buffer(int size) { b = new byte[this.size = size]; }
	}

	private abstract class MultiBuffer
	{
		protected volatile int sn;
		int getSN() { return sn; }
		abstract void reset();
		abstract int read(InputStream in) throws IOException;
		abstract byte[] getBytes() throws IOException;
	}
	
	private class DoubleBuffer extends MultiBuffer
	{
		private Buffer b1, b2;
		public DoubleBuffer(int bufferSize) { b1 = new Buffer(bufferSize); b2 = new Buffer(bufferSize); }
		@Override
		public void reset() { b1.sn = b2.sn = sn = prev_sn = 0; }
		@Override
		public int read(InputStream in) throws IOException {
			int res;
			if (b1.sn <= b2.sn) {
				res = b1.len = in.read(b1.b);
				b1.sn = ++sn;
			}
			else {
				res = b2.len = in.read(b2.b);
				b2.sn = ++sn;
			}
			return res;
		}
		private int prev_sn;
		private BufferedConsoleOut errOut = new BufferedConsoleOut(System.err); 
		@Override
		public byte[] getBytes() throws IOException {
			if (DEBUG) {
				if (sn - prev_sn > 1) {
					errOut.println("Dropped frame" + (sn - prev_sn == 2 ? " " + (sn - 1) : "s " + (prev_sn + 1) + " - " + (sn - 1)));
				}
				prev_sn = sn;
			}
			return Arrays.copyOf(b1.sn > b2.sn ? b1.b : b2.b, b1.sn > b2.sn ? b1.len : b2.len);
		}
	}
	
	private class BufferList extends MultiBuffer
	{
		private class _List
		{
			private class _ListItem { public volatile _ListItem next; public Object obj; public _ListItem(Object obj) { this.obj = obj; } }
			private _ListItem head, tail;
			public synchronized void add(Object o) { if (head == null) head = tail = new _ListItem(o); else { tail.next = new _ListItem(o); tail = tail.next;  } }
			public synchronized Object remove() { Object ret = null; if (head != null) { ret = head.obj; head = head.next; } return ret; }
		}
		private _List filledBufferList = new _List(), emptyBufferList = new _List();
		private volatile Buffer currentBuffer;
		private int bufferCount, maxBufferCount;
		private int bufferSize;
		private BufferedConsoleOut errOut = new BufferedConsoleOut(System.err);
		private int dropFrameFirst, dropFrameLast;
		public BufferList(int bufferSize, int maxBufferCount) { this.bufferSize = bufferSize; this.maxBufferCount = maxBufferCount; }
		@Override
		void reset() {
			while (filledBufferList.remove() != null) {}
			while (emptyBufferList.remove() != null) {}
			currentBuffer = null;
			sn = bufferCount = 0;
			dropFrameFirst = dropFrameLast = 0; 
		}
		@Override
		int read(InputStream in) throws IOException {
			Buffer buf = (Buffer)emptyBufferList.remove();
			if (buf == null) {
				if (bufferCount < maxBufferCount) {
					buf = new Buffer(bufferSize);
					bufferCount++;
					if (DEBUG)
						errOut.println("Buffer # " + bufferCount + " allocated");
				} else {
					buf = (Buffer)filledBufferList.remove();
					if (DEBUG) {
						if (dropFrameFirst == 0)
							dropFrameFirst = buf.sn;
						dropFrameLast = buf.sn;
					}
				}
			}
			if (dropFrameFirst > 0 && dropFrameLast < buf.sn) {
				errOut.println("Dropped frame" + (dropFrameFirst == dropFrameLast ? " " + dropFrameFirst : "s " + dropFrameFirst + " - " + dropFrameLast));
				dropFrameFirst = dropFrameLast = 0; 
			}
			int res = buf.len = in.read(buf.b);
			buf.sn = ++sn;
			filledBufferList.add(buf);
			return res;
		}
		@Override
		int getSN() {
			if (currentBuffer == null)
				currentBuffer = (Buffer)filledBufferList.remove();
			if (currentBuffer != null)
				return currentBuffer.sn; 
			return sn;
		}
		@Override
		byte[] getBytes() throws IOException {
			if (currentBuffer == null)
				currentBuffer = (Buffer)filledBufferList.remove();
			if (currentBuffer != null) {
				byte[] ret = Arrays.copyOf(currentBuffer.b, currentBuffer.len);
				Buffer buf = currentBuffer;
				currentBuffer = null;
				emptyBufferList.add(buf);
				return ret;
			}
			return null;
		}
	}

//	private String rtmp, qscale, vsync;
//	private boolean re; 
	private Process ffmp;
	private Thread ffmt;
//	private volatile boolean ffm_stop;

	private int frameBufferSize, maxBufferCount, mp3FramesPerChunk;
	private MultiBuffer multiBuffer;

	private void setButton(Button button, String label, ActionListener click, boolean active) {
		getContentPane().add(button);
		button.addActionListener(click);
		button.setLabel(label);
		button.setEnabled(active);
//		button.setVisible(active);
	}
	
	private Button playButton = new Button(), stopButton = new Button();
	
	private void setUIForPlaying(boolean playing) { stopButton.setEnabled(playing); /*stopButton.setVisible(playing);*/ playButton.setEnabled(!playing); /*startButton.setVisible(!playing);*/ }

	@Override
	public void run() {
		
		FlowLayout cont = new FlowLayout(FlowLayout.CENTER, 10, 10);
		getContentPane().setLayout(cont);
		
		setButton(stopButton, "Stop", new ActionListener() { @Override public void actionPerformed(ActionEvent e) { stopPlayback(); } }, isPlaying());
		setButton(playButton, "Play", new ActionListener() { @Override public void actionPerformed(ActionEvent e) { play(); } }, !isPlaying());
		
		getContentPane().setBackground(Color.WHITE);
//		System.out.println(FFmpeg.exe.getAbsolutePath());
//		console.append(FFmpeg.exe.getAbsolutePath() + "\n");
	}
	
//	private static boolean tryParseFloat(String val) { try { Float.parseFloat(val); return true; } catch (Throwable ex) { return false; } }
//	private static boolean tryParseInt(String val) { try { Integer.parseInt(val); return true; } catch (Throwable ex) { return false; } }
	private static int parseInt(String val, int dflt) { try { return Integer.parseInt(val); } catch (Throwable ex) { return dflt; } }
    private static boolean strEmpty(String str) { return str == null || str.length() == 0; }
    private static boolean isNo(String str) { return str == null || "No".equalsIgnoreCase(str) || "False".equalsIgnoreCase(str); }

    private boolean DEBUG = true;
    private void debug(String dbg) { if (DEBUG) System.out.println(dbg); }
    private void debug(String dbg, String inf) { if (DEBUG) System.out.println(dbg); else System.out.println(inf); }

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
		frameBufferSize = parseInt(getParameter("frame-buffer-size"), 300000);
		maxBufferCount = parseInt(getParameter("max-buffer-count"), 50);
		mp3FramesPerChunk = parseInt(getParameter("mp3-frames-per-chunk"), 10);

		multiBuffer = new BufferList(frameBufferSize, maxBufferCount); // new DoubleBuffer(frameBufferSize);
		
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
	private Map<String,String> optName = new HashMap<String,String>(), optValue = new HashMap<String,String>();
	private void addOptNV(String param, String opt, List<String> command, String dflt) {
		String _param = getParameter(PARAM_PREFIX + param);
		if (!strEmpty(_param) || (_param == null && !strEmpty(dflt))) {
			if (!strEmpty(opt)) {
				String _opt = "-" + opt;
				command.add(_opt);
				optName.put(param, _opt);
			}
			String _val = (!strEmpty(_param) ? _param : dflt);
			command.add(_val);
			optValue.put(param, _val);
		}
	}
	private void addOptNV(String param, String opt, List<String> command) { addOptNV(param, opt, command, null); }
	private void addOptNV(String name, List<String> command, String dflt) { addOptNV(name, name, command, dflt); }
	private void addOptNV(String name, List<String> command) { addOptNV(name, name, command); }
	private void addOpt_V(String name, List<String> command, String dflt) { addOptNV(name, null, command, dflt); }
	private void addOptN_(String name, List<String> command) { if (!isNo(getParameter(PARAM_PREFIX + name))) command.add("-" + name); }
	
	private enum OutputFormat { none, mjpeg, mp3, unknown }
	private OutputFormat pipeOutputFormat() {
		return optValue.get("o").startsWith("pipe:") ?
				"mjpeg".equalsIgnoreCase(optValue.get("f:o")) ? OutputFormat.mjpeg :
				"mp3".equalsIgnoreCase(optValue.get("f:o")) ? OutputFormat.mp3 :
				OutputFormat.unknown : OutputFormat.none;
	}
	
	public boolean isPlaying() { return this.ffmt != null && this.ffmt.isAlive(); }
	
	public void play() {
		
		if (isPlaying())
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
		addOptNV("frames:d", command);
		addOptNV("map", command);
		addOptNV("map1", "map", command);
		addOptNV("map2", "map", command);
		addOptN_("an", command);
		addOptNV("c:a", command);
		addOptNV("q:a", command);
		addOptNV("b:a", command);
		addOptN_("vn", command);
		addOptNV("c:v", command, "mjpeg");
		addOptNV("q:v", command);
		addOptNV("b:v", command);
		addOptNV("g", command);
		addOptNV("vsync", command);
		addOptNV("f:o", "f", command, "mjpeg");
		addOptNV("movflags", command);
		addOpt_V("o", command, "pipe:1");
		addOptNV("muxdelay", command);
		addOptNV("muxpreload", command);
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
					setUIForPlaying(true);
					multiBuffer.reset();
					int res = 0;
					FilterInputStream in_ = null;
					try {
						switch (pipeOutputFormat()) {
						case mjpeg:
							in_ = new MjpegInputStream(ffmp.getInputStream());
							dataUriStringBuilder = new StringBuilder("data:image/jpeg;base64,");
							dataUriPrefixLength = dataUriStringBuilder.length();
							break;
						case mp3:
							in_ = new MP3InputStream(ffmp.getInputStream()).setSkipTags().setDataFramesInFragment(mp3FramesPerChunk);
							dataUriStringBuilder = new StringBuilder("data:audio/mpeg;base64,");
							dataUriPrefixLength = dataUriStringBuilder.length();
							break;
						case unknown: case none: default:
							in_ = new BufferedInputStream(ffmp.getInputStream(), 1);
							dataUriStringBuilder = new StringBuilder("data:application/octet-stream;base64,");
							dataUriPrefixLength = dataUriStringBuilder.length();
							break; 
						}
						while (res != -1/* && !ffm_stop*/)
							try {
								res = multiBuffer.read(in_);
//								debug("fragment of " + res + " bytes");
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
						setUIForPlaying(false);
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

		if (this.ffmp != null)
			this.ffmp.destroy();
	}
	
	private StringBuilder dataUriStringBuilder = new StringBuilder("data:image/jpeg;base64,");
	private int dataUriPrefixLength = dataUriStringBuilder.length();

	public String getDataURI() throws IOException {
		if (multiBuffer.getSN() == 0)
			return null;
		dataUriStringBuilder.setLength(dataUriPrefixLength);
		return dataUriStringBuilder.append(DatatypeConverter.printBase64Binary(multiBuffer.getBytes())).toString();
	}
	
	public int getSN() { return multiBuffer.getSN(); }

	public void stopPlayback() {

		if (isPlaying()) {
			quitProcess();
			try {
				this.ffmt.join(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if (isPlaying())
			killProcess();
	}

	@Override
	public void stop() {
		
		stopPlayback();
		
		super.stop();
	}

	@Override
	public void destroy() {
		
		killProcess();
		
		super.destroy();
	}

//	public static void main(String[] args) {
//		// TODO Auto-generated method stub
//
//	}

}
