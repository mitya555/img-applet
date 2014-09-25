package img_applet;

import img_applet.ImgApplet.MediaDemuxer.Gettable;

import java.awt.Button;
import java.awt.Color;
import java.awt.FlowLayout;
//import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FilterInputStream;
//import java.io.BufferedInputStream;
//import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.security.AccessController;
import java.security.PrivilegedAction;
//import java.net.SocketAddress;
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
	
	static class Buffer
	{
		public volatile byte[] b;
		public volatile int sn, len;
		public int size;
		int alloc(int size) { b = new byte[this.size = size]; return this.size; }
		int grow(int size, double factor) throws IOException {
			if (factor < 1.0) throw new IOException("Grow factor < 1.0: Buffer won't grow");
			b = new byte[this.size = (int) (size * factor)];
//			System.out.println("Buffer grew to " + this.size);
			return this.size;
		}
	}

	static class VideoBuffer extends Buffer { public volatile long timestamp; }
	
	static interface BufferFactory {
		Buffer newBuffer();
	}
	
	static interface ReaderToBuffer {
		int read(Buffer b) throws IOException;
	}
	
	static abstract class MediaReader extends FilterInputStream implements ReaderToBuffer {
		protected MediaReader(InputStream in) { super(in); }
	}

	static abstract class MultiBuffer
	{
		protected volatile int sn;
		int getSN() { return sn; }
		protected BufferFactory bufferFactory;
		protected boolean DEBUG;
	    protected void debug(String dbg) { if (DEBUG) System.out.println(dbg); }
	    protected String name;
		protected MultiBuffer(BufferFactory bufferFactory, boolean debug, String name) { this.bufferFactory = bufferFactory; DEBUG = debug; this.name = name; }
		protected MultiBuffer(boolean debug, String name) { this(new BufferFactory() { @Override public Buffer newBuffer() { return new Buffer(); } }, debug, name); }
		abstract int readToBuffer(ReaderToBuffer in) throws IOException, InterruptedException;
		abstract byte[] getBytes() throws IOException;
		abstract Buffer getCurrentBuffer();
		abstract void releaseCurrentBuffer();
		abstract Buffer getCurrentBufferWait() throws InterruptedException;
		abstract void getCurrentBufferNotify();
	}

	static interface Demuxer {
		int readFragment() throws IOException, InterruptedException;
	}
	
	static abstract class MediaDemuxer extends FilterInputStream implements Demuxer {
		protected MultiBuffer video, audio;
		protected Runnable videoReadCallback, audioReadCallback;
		static interface Gettable { void get(Object info); }
		protected Gettable videoInfoCreatedCallback, audioInfoCreatedCallback;
		protected boolean DEBUG;
	    protected void debug(String dbg) { if (DEBUG) System.out.println(dbg); }
		protected MediaDemuxer(InputStream in, MultiBuffer video, MultiBuffer audio,
				Gettable videoInfoCreatedCallback, Gettable audioInfoCreatedCallback,
				Runnable videoReadCallback, Runnable audioReadCallback,
				boolean debug) {
			super(in); this.video = video; this.audio = audio;
			this.videoInfoCreatedCallback = videoInfoCreatedCallback; this.audioInfoCreatedCallback = audioInfoCreatedCallback;
			this.videoReadCallback = videoReadCallback; this.audioReadCallback = audioReadCallback;
			DEBUG = debug;
		}
	}

	static class DoubleBuffer extends MultiBuffer
	{
		private Buffer b1, b2;
		public DoubleBuffer(boolean debug, String name) { super(debug, name); b1 = new Buffer(); b2 = new Buffer(); }
		@Override
		public int readToBuffer(ReaderToBuffer in) throws IOException {
			int res;
			if (b1.sn <= b2.sn) {
				res = b1.len = in.read(b1);
				b1.sn = ++sn;
			}
			else {
				res = b2.len = in.read(b2);
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
			Buffer currentBuffer = getCurrentBuffer();
			return Arrays.copyOf(currentBuffer.b, currentBuffer.len);
		}
		@Override
		Buffer getCurrentBuffer() {
			return b1.sn > b2.sn ? b1 : b2;
		}
		@Override
		void releaseCurrentBuffer() {}
		@Override
		Buffer getCurrentBufferWait() {
			return getCurrentBuffer();
		}
		@Override
		void getCurrentBufferNotify() {}
	}
	
	static class BufferList extends MultiBuffer
	{
		static private class _List<T>
		{
			static protected class _Item<T> { public volatile _Item<T> next; public T obj; public _Item(T obj) { this.obj = obj; } }
			protected _Item<T> head, tail;
			protected int count;
			synchronized void add(T o) { if (head == null) head = tail = new _Item<T>(o); else tail = tail.next = new _Item<T>(o); count++; this.notify(); }
			synchronized T remove() { T ret = null; if (head != null) { ret = head.obj; head = head.next; count--; } return ret; }
			synchronized T removeWait() throws InterruptedException { T ret = remove(); if (ret == null) { this.wait(); ret = remove(); } return ret; }
		}
		static private class _File {
			int read, write;
			RandomAccessFile file;
			ByteBuffer bb;
			_File() throws IOException {
				File temp = File.createTempFile("img_applet_", null, JarLib.tmpdir);
				temp.deleteOnExit();
				file = new RandomAccessFile(temp, "rw");
				file.setLength(1024 * 1024);
				bb = file.getChannel().map(MapMode.READ_WRITE, 0, file.length());
			}
			_File reset() { read = write = 0; return this; }
			boolean atEndForWrite() throws IOException { return write == file.length(); }
			synchronized int write(byte[] b, int off, int len) throws IOException {
				int res = (int) Math.min(len, file.length() - write);
				if (res > 0) {
					bb.position(write);
					bb.put(b, off, res);
					write = bb.position();
				}
				return res;
			}
			synchronized int read() { return read < write ? bb.get(read++) << 24 >>> 24 : -1; }
			synchronized int read(byte[] b, int off, int len) {
				int res = (int) Math.min(len, write - read);
				if (res > 0) {
					bb.position(read);
					bb.get(b, off, res);
					read = bb.position();
				}
				return res;
			}
		}
		static private class _FileBuffer extends _List<_File> {
			_FileBuffer() throws IOException { super(); this.add(new _File()); write = head; }
			volatile _Item<_File> write;
			void write(byte[] b, int off, int len) {
				int res = 0;
//				boolean readLockNotified = false;
				while ((res += write.obj.write(b, off + res, len - res)) < len) {
//					if (res > 0 && !readLockNotified) { synchronized (readLock) { readLock.notify(); } readLockNotified = true; }
					advanceWrite();
				}
				if (res > 0 && !readLockNotified) { synchronized (readLock) { readLock.notify(); } }
				if (write.buf.atEndForWrite()) advanceWrite();
			}

		}
		private _List<Buffer> filledBufferList = new _List<Buffer>(), emptyBufferList = new _List<Buffer>();
		private volatile Buffer currentBuffer;
		private int bufferCount, maxBufferCount;
		private BufferedConsoleOut errOut = new BufferedConsoleOut(System.err);
		private int dropFrameFirst, dropFrameLast;
		public BufferList(BufferFactory bufferFactory, int maxBufferCount, boolean debug, String name) { super(bufferFactory, debug, name); this.maxBufferCount = maxBufferCount; }
		public BufferList(int maxBufferCount, boolean debug, String name) { super(debug, name); this.maxBufferCount = maxBufferCount; }
		@Override
		int readToBuffer(ReaderToBuffer in) throws IOException, InterruptedException {
			Buffer buf = emptyBufferList.remove();
			if (buf == null) {
				if (bufferCount < maxBufferCount) {
					buf = bufferFactory.newBuffer();
					bufferCount++;
					if (DEBUG)
						errOut.println(name + " buffer # " + bufferCount + " allocated");
				} else {
//					buf = filledBufferList.remove();
//					if (DEBUG) {
//						if (dropFrameFirst == 0)
//							dropFrameFirst = buf.sn;
//						dropFrameLast = buf.sn;
//					}
					buf = emptyBufferList.removeWait();
				}
			}
			if (dropFrameFirst > 0 && dropFrameLast < buf.sn) {
				errOut.println("Dropped frame" + (dropFrameFirst == dropFrameLast ? " " + dropFrameFirst : "s " + dropFrameFirst + " - " + dropFrameLast));
				dropFrameFirst = dropFrameLast = 0; 
			}
			int res = buf.len = in.read(buf);
			buf.sn = ++sn;
			(res != -1 ? filledBufferList : emptyBufferList).add(buf);
			return res;
		}
		@Override
		int getSN() {
			getCurrentBuffer_();
			if (currentBuffer != null)
				return currentBuffer.sn; 
			return sn;
		}
		@Override
		byte[] getBytes() throws IOException {
			getCurrentBuffer_();
			if (currentBuffer != null) {
				byte[] ret = Arrays.copyOf(currentBuffer.b, currentBuffer.len);
				releaseCurrentBuffer_();
				return ret;
			}
			return null;
		}
		private void getCurrentBuffer_() {
			if (currentBuffer == null)
				currentBuffer = filledBufferList.remove();
		}
		private void getCurrentBufferWait_() throws InterruptedException {
			if (currentBuffer == null)
				currentBuffer = filledBufferList.removeWait();
		}
		private void releaseCurrentBuffer_() {
			Buffer buf = currentBuffer;
			currentBuffer = null;
			if (bufferCount > maxBufferCount / 2 && emptyBufferList.count > maxBufferCount / 2) {
				if (DEBUG)
					try { errOut.println(name + " buffer # " + bufferCount + " discarded"); } catch (IOException e) { e.printStackTrace(); }
				bufferCount--;
				return;
			}
			emptyBufferList.add(buf);
		}
		@Override
		Buffer getCurrentBuffer() {
			getCurrentBuffer_();
			return currentBuffer;
		}
		@Override
		void releaseCurrentBuffer() {
			if (currentBuffer != null)
				releaseCurrentBuffer_();
		}
		@Override
		Buffer getCurrentBufferWait() throws InterruptedException {
			getCurrentBufferWait_();
			return currentBuffer;
		}
		@Override
		void getCurrentBufferNotify() {
			synchronized (filledBufferList) { filledBufferList.notify(); }
		}
	}

	static private class DataOut {
		public String contentType;
		public StringBuilder dataUriStringBuilder;
		private int dataUriPrefixLength;
		public DataOut(String contentType) {
			this.contentType = contentType;
			dataUriStringBuilder = new StringBuilder("data:" + contentType + ";base64,");
			dataUriPrefixLength = dataUriStringBuilder.length();
		}
		public void resetStringBuilder() { dataUriStringBuilder.setLength(dataUriPrefixLength); }
	}
	
	private class DataStream
	{
		protected DataStream(String contentType, MultiBuffer multiBuffer) { dataOut = new DataOut(contentType); this.multiBuffer = multiBuffer; }

		private MultiBuffer multiBuffer;
		private DataOut dataOut; 

		private int timeScale;
		
		public String getDataURI() throws IOException {
			if (multiBuffer.getSN() == 0)
				return null;
			dataOut.resetStringBuilder();
			return dataOut.dataUriStringBuilder.append(DatatypeConverter.printBase64Binary(multiBuffer.getBytes())).toString();
		}
		
		public void httpLockNotify() { multiBuffer.getCurrentBufferNotify(); }
		
		private volatile int httpPort;
		private InetAddress httpAddress;
		
		public boolean isStreaming() { return httpPort > 0; }
		
		private Object startLock = new Object();
		public /*int*/String startHttpServer() throws InterruptedException {
	        Thread httpThread = new Thread(new Runnable() {
				@Override
				public void run() {
					try (ServerSocket serverSocket = new ServerSocket()) {
						serverSocket.setReuseAddress(true);
						serverSocket.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }), 0), 1);
						httpPort = serverSocket.getLocalPort();
						httpAddress = serverSocket.getInetAddress();
						synchronized (startLock) { startLock.notify(); }
						AccessController.doPrivileged(new PrivilegedAction<Object>() {
							@Override
							public Object run() {
								runHttpServer(serverSocket);
								return null;
							}
						}); /* doPrivileged() */
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						httpPort = 0;
						stopPlayback();
					}
				}
	        });
			synchronized (startLock) {
				httpThread.start();
				startLock.wait();
			}
			String res = "http://" + httpAddress.getHostAddress() + ":" + httpPort;
	        debug(res);
			/*return httpPort;*/return res;
		}

		private void runHttpServer(ServerSocket serverSocket) {
			try (	Socket clientSocket = serverSocket.accept();
					OutputStream out = clientSocket.getOutputStream();
					PrintWriter charOut = new PrintWriter(out);
					BufferedOutputStream byteOut = new BufferedOutputStream(out);
					BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
				) {
				StringBuilder input = new StringBuilder();
				String crlf = "\r\n", inputLine;
				while ((inputLine = in.readLine()) != null) {
			        if (inputLine.equals(""))
			            break;
					input.append(inputLine).append(crlf);
				}
				if (DEBUG)
					System.out.println(input.toString());
				//charOut.write("HTTP/1.1 200 OK\r\nContent-Type: " + dataOut.contentType + "\r\n\r\n");
				charOut.write("HTTP/1.1 206 Partial Content\r\nContent-Type: " + dataOut.contentType + "\r\nAccess-Control-Allow-Origin: *\r\n\r\n");
				charOut.flush();
//				byte[] bytes;
//				while ((bytes = getBytes()) != null || isPlaying()) {
//					if (bytes != null) {
//						byteOut.write(bytes);
//						byteOut.flush();
//					}
//				}
				Buffer buf;
				while ((buf = multiBuffer.getCurrentBufferWait()) != null || isPlaying()) {
					if (buf != null) {
						byteOut.write(buf.b, 0, buf.len);
						multiBuffer.releaseCurrentBuffer();
						byteOut.flush();
					}
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

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
	private static int parseInt(String val) { try { return Integer.parseInt(val); } catch (Throwable ex) { return -1; } }
	private static double parseDouble(String val) { try { return Double.parseDouble(val); } catch (Throwable ex) { return -1.0; } }
    private static boolean strEmpty(String str) { return str == null || str.length() == 0; }
    private static boolean isNo(String str) { return str == null || "No".equalsIgnoreCase(str) || "False".equalsIgnoreCase(str); }

    private boolean DEBUG = true;
    private void debug(String dbg) { if (DEBUG) System.out.println(dbg); }
    private void debug(String dbg, String inf) { if (DEBUG) System.out.println(dbg); else System.out.println(inf); }

    static private class BufferedConsoleOut
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

//	private String rtmp, qscale, vsync;
//	private boolean re;
	private Process ffmp;
	private Thread ffmt;
//	private volatile boolean ffm_stop;

	private boolean demux_fMP4;
	private int bufferSize, vBufferSize, aBufferSize, maxBufferCount, mp3FramesPerChunk;
	private double bufferGrowFactor, bufferShrinkThresholdFactor;
	private DataStream dataStream, demuxVideoDataStream;

	@Override
	public void init() {
		
		super.init();
		
		DEBUG = !isNo(getParameter("debug"));
		
		demux_fMP4 = !isNo(getParameter("demux-fMP4"));
		
		vBufferSize = parseInt(getParameter("video-buffer-size"));
		aBufferSize = parseInt(getParameter("audio-buffer-size"));
		
		bufferGrowFactor = parseDouble(getParameter("buffer-grow-factor"));
		bufferShrinkThresholdFactor = parseDouble(getParameter("buffer-shrink-threshold-factor"));
		
		bufferSize = parseInt(getParameter("buffer-size"));
		
		maxBufferCount = parseInt(getParameter("max-buffer-count"));
		if (maxBufferCount < 0) maxBufferCount = 30;
		
		mp3FramesPerChunk = parseInt(getParameter("mp3-frames-per-chunk"));
		
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
	private void _addOptNV(String param, String opt, List<String> command, String value, String dflt) {
		if (!strEmpty(value) || (value == null && !strEmpty(dflt))) {
			if (!strEmpty(opt)) {
				String _opt = "-" + opt;
				command.add(_opt);
				optName.put(param, _opt);
			}
			String _val = (!strEmpty(value) ? value : dflt);
			command.add(_val);
			optValue.put(param, _val);
		}
	}
	private void addOptNV(String param, String opt, List<String> command, String dflt) {
		_addOptNV(param, opt, command, getParameter(PARAM_PREFIX + param), dflt);
	}
	private void addOptNV(String param, String opt, List<String> command) { addOptNV(param, opt, command, null); }
	private void addOptNV(String name, List<String> command, String dflt) { addOptNV(name, name, command, dflt); }
	private void addOptNV(String name, List<String> command) { addOptNV(name, name, command); }
	private void addOpt_V(String name, List<String> command, String dflt) { addOptNV(name, null, command, dflt); }
	private void addOptN_(String name, List<String> command) { if (!isNo(getParameter(PARAM_PREFIX + name))) command.add("-" + name); }
	
	private enum OutputFormat { none, mjpeg, mp3, mp4, webm, unknown }
	private OutputFormat pipeOutputFormat() {
		return optValue.get("o").startsWith("pipe:") ?
				"mjpeg".equalsIgnoreCase(optValue.get("f:o")) ? OutputFormat.mjpeg :
				"mp3".equalsIgnoreCase(optValue.get("f:o")) ? OutputFormat.mp3 :
				"mp4".equalsIgnoreCase(optValue.get("f:o")) ? OutputFormat.mp4 :
				"webm".equalsIgnoreCase(optValue.get("f:o")) ? OutputFormat.webm :
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
		addOptNV("f:i", "f", command/*, "flv"*/);
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
		addOptNV("c:v", command, demux_fMP4 ? "mjpeg" : null);
		addOptNV("q:v", command);
		addOptNV("b:v", command);
		addOptNV("g", command);
		addOptNV("vsync", command);
		addOptNV("f:o", "f", command, demux_fMP4 ? "mp4" : null);
		addOptNV("movflags", command, demux_fMP4 ? "frag_keyframe+empty_moov" : null);
		addOptN_("y", command);
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
					try {
						if (demux_fMP4)
							playMediaDemuxer();
						else
							playMediaReader();
					} catch (InterruptedException e) {
						e.printStackTrace();
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

	private void playMediaReader() throws InterruptedException {
		setUIForPlaying(true);
		MediaReader in_ = null;
		try {
			String contentType;
			switch (pipeOutputFormat()) {
			case mjpeg:
				in_ = new MjpegInputStream(ffmp.getInputStream(), vBufferSize, bufferGrowFactor);
				contentType = "image/jpeg";
				break;
			case mp3:
				in_ = new MP3InputStream(ffmp.getInputStream(), aBufferSize, mp3FramesPerChunk, bufferGrowFactor)/*.setSkipTags()*/;
				contentType = "audio/mpeg";
				break;
			case mp4:
				in_ = new fMP4InputStream(ffmp.getInputStream(), bufferGrowFactor);
				contentType = "video/mp4";
				break;
			case webm:
				in_ = new GenericBufferWriter(ffmp.getInputStream(), bufferSize);
				contentType ="video/webm";
				break; 
			case unknown: case none: default:
				in_ = new GenericBufferWriter(ffmp.getInputStream(), bufferSize);
				contentType = "application/octet-stream";
				break; 
			}
			dataStream = new DataStream(contentType, new BufferList(maxBufferCount, DEBUG, "Frame"));
			demuxVideoDataStream = null;
			int res = 0;
			while (res != -1/* && !ffm_stop*/)
				try {
					res = dataStream.multiBuffer.readToBuffer(in_);
//					debug("fragment of " + res + " bytes");
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

	private void playMediaDemuxer() throws InterruptedException {
		setUIForPlaying(true);
		MediaDemuxer in_ = null;
		try {
			dataStream = new DataStream("audio/mpeg", new BufferList(maxBufferCount, DEBUG, "Audio"));
			demuxVideoDataStream = new DataStream("image/jpeg", new BufferList(
					new BufferFactory() { @Override public Buffer newBuffer() { return new VideoBuffer(); } }, maxBufferCount, DEBUG, "Video"));
			in_ = new fMP4DemuxerInputStream(/*new FileBackedAutoReadingInputStream(*/ffmp.getInputStream()/*)*/,
					bufferGrowFactor, bufferShrinkThresholdFactor,
					demuxVideoDataStream.multiBuffer, dataStream.multiBuffer,
					new Gettable() { @Override public void get(Object info) { demuxVideoDataStream.timeScale = ((fMP4DemuxerInputStream.Trak)info).timeScale; } }, null,
					null, null,
					DEBUG);
			int res = 0;
			while (res != -1/* && !ffm_stop*/)
				try {
					res = in_.readFragment();
//					debug("readFragment() result: " + res);
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

	private void quitProcess() {
		try {
			debug("quitProcess() call...");
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

		debug("killProcess() call...");
		
//		this.ffm_stop = true;
//		if (this.ffmt != null)
//			this.ffmt.interrupt();

		if (this.ffmp != null)
			this.ffmp.destroy();
	}

	public String getDataURI() throws IOException { return dataStream != null ? dataStream.getDataURI() : null; }
	
	public int getSN() { return dataStream != null ? dataStream.multiBuffer.getSN() : 0; }
	
	public boolean isStreaming() { return dataStream != null ? dataStream.isStreaming() : false; }
	
	public String startHttpServer() throws InterruptedException { return dataStream != null ? dataStream.startHttpServer() : null; } 

	public String getVideoDataURI() throws IOException { return demuxVideoDataStream != null ? demuxVideoDataStream.getDataURI() : null; }
	
	public int getVideoSN() { return demuxVideoDataStream != null ? demuxVideoDataStream.multiBuffer.getSN() : 0; }

	public long getVideoTimestamp() { VideoBuffer cvb = demuxVideoDataStream != null ? (VideoBuffer)demuxVideoDataStream.multiBuffer.getCurrentBuffer() : null; return cvb != null ? cvb.timestamp : 0L; }

	public long getVideoTimeScale() { return demuxVideoDataStream != null ? demuxVideoDataStream.timeScale : 0; }

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

		if (dataStream != null) dataStream.httpLockNotify();
	}

	@Override
	public void stop() {
		
		stopPlayback();
		
		super.stop();
	}

	@Override
	public void destroy() {
		
		killProcess();

		if (dataStream != null) dataStream.httpLockNotify();
		
		super.destroy();
	}

//	public static void main(String[] args) {
//
//	}

}
