package img_applet;

import ffmpeg.FFmpeg;
import img_applet.FFmpegProcess.MediaDemuxer.Gettable;

import java.applet.Applet;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FilterInputStream;
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
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.xml.bind.DatatypeConverter;

import netscape.javascript.JSException;
import netscape.javascript.JSObject;

public class FFmpegProcess extends Observable {

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
		public boolean persistent;
		public FrameData getFrameData(byte[] bytes) throws IOException { return new FrameData(sn, bytes); }
		@Override
		public String toString() { return " # " + sn + (persistent ? " \t f/s" : " \t mem"); }
	}

	static class VideoBuffer extends Buffer {
		public volatile long timestamp;
		@Override
		public FrameData getFrameData(byte[] bytes) throws IOException { return new VideoFrameData(sn, bytes, timestamp); }
		@Override
		public String toString() { return super.toString() + " \t ts: " + timestamp; }
	}
	
	static interface BufferFactory { Buffer newBuffer(); }
	
	static interface ReaderToBuffer { int read(Buffer b) throws IOException; }

	static interface ReaderFromReaderToBuffer extends Closeable { int readToBuffer(ReaderToBuffer in) throws IOException, InterruptedException; }

	static class FrameData
	{
		public byte[] bytes;
		public int sn;
		public FrameData(int sn, byte[] bytes) { this.sn = sn; this.bytes = bytes; }
	}

	static class VideoFrameData extends FrameData {
		public long timestamp;
		public VideoFrameData(int sn, byte[] bytes, long timestamp) { super(sn, bytes); this.timestamp = timestamp; }
	}

	static abstract class MultiBuffer implements ReaderFromReaderToBuffer
	{
		protected volatile int sn;
		int getSN() { return sn; }
		protected BufferFactory bufferFactory;
		protected boolean DEBUG;
	    protected void debug(String dbg) { if (DEBUG) System.out.println(dbg); }
	    protected String name;
		protected MultiBuffer(BufferFactory bufferFactory, boolean debug, String name) { this.bufferFactory = bufferFactory; DEBUG = debug; this.name = name; }
		protected MultiBuffer(boolean debug, String name) { this(new BufferFactory() { @Override public Buffer newBuffer() { return new Buffer(); } }, debug, name); }
		abstract byte[] getBytes(Buffer b) throws IOException;
		abstract byte[] getCurrentBytes() throws IOException;
		abstract FrameData getCurrentFrameData() throws IOException;
		abstract Buffer getCurrentBuffer();
		abstract void releaseCurrentBuffer();
		abstract Buffer getCurrentBufferWait() throws InterruptedException;
		abstract void getCurrentBufferNotify();
		abstract int getQueueLength();
		// producer / consumer pattern:
		public BlockingQueue<Integer> notifyQueue;
		protected int numberOfConsumerThreads;
		protected Thread[] consumerThreads;
		public void startConsumerThreads(int numOfThreads, Runnable runnable) {
			numberOfConsumerThreads = numOfThreads;
			consumerThreads = new Thread[numOfThreads];
			for (int i = 0; i < numOfThreads; i++) {
				(consumerThreads[i] = new Thread(runnable)).start();
			}
		}
		@Override
		public void close() throws IOException {
			if (notifyQueue != null) {
				notifyQueue.clear();
				for (int i = 0; i < numberOfConsumerThreads; i++) {
					notifyQueue.offer(-200);
				}
			}
			if (consumerThreads != null)
				for (Thread thread : consumerThreads)
					try { thread.join(); } catch (InterruptedException e) { e.printStackTrace(); }
			consumerThreads = null;
			notifyQueue = null;
		}
	}
	
	static abstract class MediaReader extends FilterInputStream implements ReaderToBuffer {
		protected MediaReader(InputStream in) { super(in); }
	}

	static interface Demuxer {
		int readFragment() throws IOException, InterruptedException;
	}
	
	class AudioLinePlayer implements ReaderFromReaderToBuffer {
		protected SourceDataLine audioLine;
		protected Buffer audioLineBuffer;
		@Override
		public int readToBuffer(ReaderToBuffer in) throws IOException, InterruptedException {
			int bytesRead = in.read(audioLineBuffer);
			if (bytesRead == -1) return -1;
			return audioLine.write(audioLineBuffer.b, 0, bytesRead);
		}
		@Override
		public void close() throws IOException {
			if (audioLine != null) {
				audioLineBuffer = null;
				audioLine.drain();
				debug("Playback ended.");
				audioLine = null;
			}
		}
		AudioLinePlayer create(AudioFormat audioFormat) {
			try {
				debug("audioFormat = " + audioFormat);
				audioLine = (SourceDataLine)AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));
				audioLineBuffer = new Buffer();
				debug("applet parameter wav-audio-line-buffer-size = " + wavAudioLineBufferSize);
				if (wavAudioLineBufferSize > 0)
					audioLine.open(audioFormat, wavAudioLineBufferSize);
				else
					audioLine.open(audioFormat);
				int audioLineBufferSize = audioLine.getBufferSize();
				debug("audioLine.getBufferSize() = " + audioLineBufferSize);
				audioLine.start();
				debug("Playback started.");
				return this;
			} catch (LineUnavailableException e) {
				e.printStackTrace();
			}
			return null;
		}
	}
	
	static abstract class MediaDemuxer extends FilterInputStream implements Demuxer {
		protected ReaderFromReaderToBuffer video, audio;
		static interface Gettable { void get(Object info, MediaDemuxer self); }
		protected Gettable videoInfoCreatedCallback, audioInfoCreatedCallback;
		protected boolean DEBUG;
	    protected void debug(String dbg) { if (DEBUG) System.out.println(dbg); }
		protected MediaDemuxer(InputStream in, ReaderFromReaderToBuffer video, ReaderFromReaderToBuffer audio,
				Gettable videoInfoCreatedCallback, Gettable audioInfoCreatedCallback,
				boolean debug) {
			super(in); this.video = video; this.audio = audio;
			this.videoInfoCreatedCallback = videoInfoCreatedCallback; this.audioInfoCreatedCallback = audioInfoCreatedCallback;
			DEBUG = debug;
		}
	}

	static class DoubleBuffer extends MultiBuffer
	{
		private Buffer b1, b2;
		public DoubleBuffer(BufferFactory bufferFactory, boolean debug, String name) { super(bufferFactory, debug, name); b1 = new Buffer(); b2 = new Buffer(); }
		public DoubleBuffer(boolean debug, String name) { super(debug, name); b1 = new Buffer(); b2 = new Buffer(); }
		@Override
		public int readToBuffer(ReaderToBuffer in) throws IOException {
			int res;
			if (b1.sn <= b2.sn) {
				synchronized (b1) {
					res = b1.len = in.read(b1);
					b1.sn = ++sn;
					if (notifyQueue != null) {
						notifyQueue.offer(sn);
					}
				}
			}
			else {
				synchronized (b2) {
					res = b2.len = in.read(b2);
					b2.sn = ++sn;
					if (notifyQueue != null) {
						notifyQueue.offer(sn);
					}
				}
			}
			return res;
		}
		private int prev_sn;
		private BufferedConsoleOut errOut = new BufferedConsoleOut(System.err); 
		@Override
		byte[] getBytes(Buffer b) throws IOException { synchronized (b) { return Arrays.copyOf(b.b, b.len); } }
		@Override
		byte[] getCurrentBytes() throws IOException {
			Buffer currentBuffer = getCurrentBuffer();
			while (true) {
				synchronized (currentBuffer) {
					Buffer newCurrentBuffer = getCurrentBuffer();
					if (currentBuffer != newCurrentBuffer) {
						currentBuffer = newCurrentBuffer;
						if (DEBUG) {
							errOut.println("Re-lock buffer");
						}
						continue;
					}
					if (DEBUG) {
						if (sn - prev_sn > 1) {
							errOut.println("Dropped frame" + (sn - prev_sn == 2 ? " " + (sn - 1) : "s " + (prev_sn + 1) + " - " + (sn - 1)));
						}
						prev_sn = sn;
					}
					return getBytes(currentBuffer);
				}
			}
		}
		@Override
		FrameData getCurrentFrameData() throws IOException {
			Buffer currentBuffer = getCurrentBuffer();
			while (true) {
				synchronized (currentBuffer) {
					Buffer newCurrentBuffer = getCurrentBuffer();
					if (currentBuffer != newCurrentBuffer) {
						currentBuffer = newCurrentBuffer;
						if (DEBUG) {
							errOut.println("Re-lock buffer");
						}
						continue;
					}
					if (DEBUG) {
						if (sn - prev_sn > 1) {
							errOut.println("Dropped frame" + (sn - prev_sn == 2 ? " " + (sn - 1) : "s " + (prev_sn + 1) + " - " + (sn - 1)));
						}
						prev_sn = sn;
					}
					return currentBuffer.getFrameData(getBytes(currentBuffer));
				}
			}
		}
		@Override
		Buffer getCurrentBuffer() { return b1.sn > b2.sn ? b1 : b2; }
		@Override
		void releaseCurrentBuffer() {}
		@Override
		Buffer getCurrentBufferWait() { return getCurrentBuffer(); }
		@Override
		void getCurrentBufferNotify() {}
		@Override
		int getQueueLength() { return 2; }
	}
	
	static class BufferList extends MultiBuffer
	{
		static private class _List<T>
		{
			static protected class _Item<T> { public volatile _Item<T> next; public T obj; public _Item(T obj) { this.obj = obj; } }
			protected volatile _Item<T> head, tail;
			protected volatile int count;
			private boolean stopped;
			synchronized void add(T o) { if (head == null) head = tail = new _Item<T>(o); else tail = tail.next = new _Item<T>(o); count++; this.notify(); stopped = false; }
			synchronized T remove() { T ret = null; if (head != null) { ret = head.obj; head = head.next; count--; } return ret; }
			synchronized T removeWait() throws InterruptedException { T ret = remove(); if (ret == null && !stopped) { this.wait(); ret = remove(); } return ret; }
			synchronized void stop() { stopped = true; this.notify(); }
		}
		static private class _File implements Closeable {
			int read, write;
			File file;
			RandomAccessFile raf;
			FileChannel fc;
			ByteBuffer bb;
			_File() throws IOException {
				file = File.createTempFile("img_applet_", null, JarLib.tmpdir);
				file.deleteOnExit();
				raf = new RandomAccessFile(file, "rw");
				raf.setLength(1024 * 1024);
				fc = raf.getChannel();
				bb = fc.map(MapMode.READ_WRITE, 0, raf.length());
			}
			int write(byte[] b, int off, int len) throws IOException {
				int res = (int) Math.min(len, raf.length() - write);
				if (res > 0)
					synchronized (this) {
						bb.position(write);
						bb.put(b, off, res);
						write = bb.position();
					}
				return res;
			}
			synchronized int read(byte[] b, int off, int len) {
				int res = (int) Math.min(len, write - read);
				if (res > 0)
					synchronized (this) {
						bb.position(read);
						bb.get(b, off, res);
						read = bb.position();
						if (read == write)
							read = write = 0;
					}
				return res;
			}
			public boolean tryDelete() throws IOException {
				bb.clear();
				bb = null;
				fc.close();
				raf.close();
//				if (!file.delete())
//					throw new IOException("File " + file.getName() + " couldn't be deleted.");
//				Files.deleteIfExists(file.toPath());
				return file.delete();
			}
			@Override
			public void close() throws IOException { tryDelete(); }
			@Override
			protected void finalize() throws Throwable { file.delete(); super.finalize(); }
		}
		static private class _FileBuffer extends _List<_File> {
			_FileBuffer() throws IOException { super(); add(new _File()); write = head; }
			volatile _Item<_File> write;
			void write(byte[] b, int off, int len) throws IOException {
				int res = 0;
				while ((res += write.obj.write(b, off + res, len - res)) < len)
					synchronized (this) {
						if (write.next == null)
							add(new _File());
						write = write.next;
					}
			}
			int read(byte[] b, int off, int len) throws IOException {
				int res = 0;
				while ((res += head.obj.read(b, off + res, len - res)) < len)
					synchronized (this) {
						if (head == write)
							throw new IOException("Insufficient size");
						add(remove());
					}
				return res;
			}
		}
		private _List<Buffer> filledBufferList = new _List<Buffer>(), emptyBufferList = new _List<Buffer>();
		private _FileBuffer fileBuffer;
		private volatile Buffer currentBuffer;
		private int bufferCount;
		final private int maxMemoryBufferCount, maxBufferCount;
		private BufferedConsoleOut errOut = new BufferedConsoleOut(System.err);
		public BufferList(BufferFactory bufferFactory, int maxMemoryBufferCount, int maxBufferCount, boolean debug, String name) {
			super(bufferFactory, debug, name);
			this.maxMemoryBufferCount = maxMemoryBufferCount;
			this.maxBufferCount = maxBufferCount;
		}
		public BufferList(int maxMemoryBufferCount, int maxBufferCount, boolean debug, String name) {
			super(debug, name);
			this.maxMemoryBufferCount = maxMemoryBufferCount;
			this.maxBufferCount = maxBufferCount;
		}
		@Override
		public int readToBuffer(ReaderToBuffer in) throws IOException, InterruptedException {

			while (maxBufferCount > 0 && filledBufferList.count >= maxBufferCount) {
				Buffer buf_ = filledBufferList.remove();
				if (buf_ != null) {
					if (buf_.persistent) {
						byte[] tmp = new byte[buf_.len];
						fileBuffer.read(tmp, 0, buf_.len); // release the file buffer
					}
					if (DEBUG)
						errOut.println(" - " + name + buf_);
					releaseBuffer_(buf_);
				}
			}

			Buffer buf = emptyBufferList.remove();
			if (buf == null) {
				buf = bufferFactory.newBuffer();
				if (bufferCount < maxMemoryBufferCount) {
					bufferCount++;
				} else {
					buf.persistent = true;
				}
			}
			int res = buf.len = in.read(buf);
			buf.sn = ++sn;
			if (buf.persistent) {
				if (res != -1) {
					if (fileBuffer == null)
						fileBuffer = new _FileBuffer();
					fileBuffer.write(buf.b, 0, buf.len);
					buf.b = null;
					filledBufferList.add(buf);
					if (notifyQueue != null) {
						notifyQueue.offer(sn);
					}
				}
			} else if (res != -1) {
				filledBufferList.add(buf);
				if (notifyQueue != null) {
					notifyQueue.offer(sn);
				}
			} else {
				 emptyBufferList.add(buf);
			}
			if (DEBUG && res != -1)
				errOut.println(" + " + name + buf);
			return res;
		}
		@Override
		int getSN() { getCurrentBuffer_(); if (currentBuffer != null) return currentBuffer.sn; return sn; }
		@Override
		byte[] getBytes(Buffer b) throws IOException {
			byte[] ret = null;
			if (b.persistent) {
				ret = new byte[b.len];
				fileBuffer.read(ret, 0, b.len);
			} else
				ret = Arrays.copyOf(b.b, b.len);
			return ret;
		}
		@Override
		byte[] getCurrentBytes() throws IOException {
			getCurrentBuffer_();
			if (currentBuffer != null) {
				byte[] ret = getBytes(currentBuffer);
				releaseCurrentBuffer_();
				return ret;
			}
			return null;
		}
		@Override
		FrameData getCurrentFrameData() throws IOException {
			getCurrentBuffer_();
			if (currentBuffer != null) {
				FrameData ret = currentBuffer.getFrameData(getBytes(currentBuffer));
				releaseCurrentBuffer_();
				return ret;
			}
			return null;
		}
		private void getCurrentBuffer_() { if (currentBuffer == null) currentBuffer = filledBufferList.remove(); }
		private void getCurrentBufferWait_() throws InterruptedException { if (currentBuffer == null) currentBuffer = filledBufferList.removeWait(); }
		private void releaseBuffer_(Buffer buf) {
			if (!buf.persistent) {
				if (bufferCount > maxMemoryBufferCount / 2 && emptyBufferList.count > maxMemoryBufferCount / 2) {
					bufferCount--;
					return;
				}
				emptyBufferList.add(buf);
			}
		}
		private void releaseCurrentBuffer_() {
			Buffer buf = currentBuffer;
			currentBuffer = null;
			releaseBuffer_(buf);
		}
		@Override
		Buffer getCurrentBuffer() { getCurrentBuffer_(); return currentBuffer; }
		@Override
		void releaseCurrentBuffer() { if (currentBuffer != null) releaseCurrentBuffer_(); }
		@Override
		Buffer getCurrentBufferWait() throws InterruptedException { getCurrentBufferWait_(); return currentBuffer; }
		@Override
		void getCurrentBufferNotify() { filledBufferList.stop(); }
		@Override
		int getQueueLength() { return filledBufferList.count + (currentBuffer != null ? 1 : 0); }
		@Override
		public void close() throws IOException {
			super.close();
			if (fileBuffer != null) {
				_File _file;
				int closed = 0, deleted = 0;
				while ((_file = fileBuffer.remove()) != null) {
					debug("Closing file " + _file.file.getName());
					closed++;
					try { if (_file.tryDelete()) deleted++; } catch (Throwable e) { e.printStackTrace(); }
				}
				errOut.println("FileBuffer: closed " + closed + "; deleted " + deleted + " files");
				fileBuffer = null;
			}
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
		public String toDataUri(byte[] buf) {
			dataUriStringBuilder.setLength(dataUriPrefixLength);
			return dataUriStringBuilder.append(DatatypeConverter.printBase64Binary(buf)).toString();
		}
	}
	
	static public class TrackInfo { public int timeScale, width, height; public boolean hasAudio; }

	private class MediaStream implements Closeable
	{
		protected MediaStream(String contentType, MultiBuffer multiBuffer) { dataOut = new DataOut(contentType); this.multiBuffer = multiBuffer; }

		private MultiBuffer multiBuffer;
		private DataOut dataOut; 
		private TrackInfo trackInfo = new TrackInfo();

		public byte[] getData() throws IOException {
			if (multiBuffer.getSN() == 0)
				return null;
			return multiBuffer.getCurrentBytes();
		}
		
		public String getDataURI() throws IOException {
			if (multiBuffer.getSN() == 0)
				return null;
			return dataOut.toDataUri(multiBuffer.getCurrentBytes());
		}
		
		public void multiBufferNotify() { multiBuffer.getCurrentBufferNotify(); }
		
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
						synchronized (startLock) { debug("HTTP startLock.notify()"); startLock.notify(); }
						AccessController.doPrivileged(new PrivilegedAction<Object>() {
							@Override
							public Object run() {
								runHttpServer(serverSocket);
								debug("HTTP streaming ended.");
								return null;
							}
						}); /* doPrivileged() */
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						httpPort = 0;
//						stopPlayback();
					}
				}
	        });
			synchronized (startLock) {
				httpThread.start();
				debug("HTTP startLock.wait()");
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
				String inputLine, crlf = "\r\n", contentRange = "*/*";
				while ((inputLine = in.readLine()) != null) {
					if (inputLine.startsWith("User-Agent:") && (inputLine.contains("Chrome") || inputLine.contains("Trident")))
						contentRange = "0-9999999999/10000000000";
			        if (inputLine.equals(""))
			            break;
					input.append(inputLine).append(crlf);
				}
				if (DEBUG)
					System.out.println(input.toString());
				//charOut.write("HTTP/1.1 200 OK\r\nContent-Type: " + dataOut.contentType + "\r\n\r\n");
				String output_header = "HTTP/1.1 206 Partial Content\r\n" +
						"Content-Type: " + dataOut.contentType + "\r\n" +
						"Content-Range: bytes " + contentRange + "\r\n" +
						"Access-Control-Allow-Origin: *\r\n" +
						"\r\n";
				if (DEBUG)
					System.out.print(output_header);
				charOut.write(output_header);
				charOut.flush();
//				byte[] bytes;
//				while ((bytes = getBytes()) != null || isPlaying()) {
//					if (bytes != null) {
//						byteOut.write(bytes);
//						byteOut.flush();
//					}
//				}
				Buffer buf;
				while (isPlaying() && (buf = multiBuffer.getCurrentBufferWait()) != null) {
					byte[] res = buf.persistent ? multiBuffer.getBytes(buf) : buf.b;
					byteOut.write(res, 0, buf.len);
					multiBuffer.releaseCurrentBuffer();
					byteOut.flush();
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void close() throws IOException { if (multiBuffer != null) { multiBuffer.getCurrentBufferNotify(); multiBuffer.close(); } }
	}
	
//	private static boolean tryParseFloat(String val) { try { Float.parseFloat(val); return true; } catch (Throwable ex) { return false; } }
//	private static boolean tryParseInt(String val) { try { Integer.parseInt(val); return true; } catch (Throwable ex) { return false; } }
	private static int parseInt(String val) { try { return Integer.parseInt(val); } catch (Throwable ex) { return -1; } }
	private static double parseDouble(String val) { try { return Double.parseDouble(val); } catch (Throwable ex) { return -1.0; } }
    private static boolean strEmpty(String str) { return str == null || str.length() == 0; }
    private static boolean isNo(String str) { return str == null || "No".equalsIgnoreCase(str) || "False".equalsIgnoreCase(str); }

    private boolean DEBUG = true, DEBUG_FFMPEG = false, DEBUG_BUFFERLIST = false;
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

	private Process ffmp;
	private Thread ffmt;
//	private volatile boolean ffm_stop;

	private boolean demux_fMP4, dropUnusedFrames;
	private int bufferSize, vBufferSize, aBufferSize, maxMemoryBufferCount, maxVideoBufferCount, mp3FramesPerChunk,
		wavAudioLineBufferSize, wavIntermediateBufferSize, processFrameNumberOfConsumerThreads;
	private String wavLevelChangeCallback, processFrameCallback;
	private double bufferGrowFactor, bufferShrinkThresholdFactor;
	private MediaStream mediaStream, demuxVideoStream;
	
	private boolean useStderr;
	private ByteArrayOutputStream stderrOut;
	
	private Object params;
	private String getParameter(String name) {
		return params instanceof Applet ? ((Applet)params).getParameter(name) :
			params instanceof Map<?,?> ? ((Map<String,String>)params).get(name) :
				null;
	}
	private Applet applet;
	private int id;
	public FFmpegProcess setId(int id) { this.id = id; return this; }

	public FFmpegProcess init(Object params, Applet applet) {
		
		this.params = params;
		this.applet = applet;
		
		DEBUG = !isNo(getParameter("debug"));
		DEBUG_FFMPEG = !isNo(getParameter("debug-ffmpeg"));
		DEBUG_BUFFERLIST = !isNo(getParameter("debug-bufferlist"));
		
		demux_fMP4 = !isNo(getParameter("demux-fMP4"));
		dropUnusedFrames = !isNo(getParameter("drop-unused-frames"));
		
		vBufferSize = parseInt(getParameter("video-buffer-size"));
		aBufferSize = parseInt(getParameter("audio-buffer-size"));
		
		bufferGrowFactor = parseDouble(getParameter("buffer-grow-factor"));
		bufferShrinkThresholdFactor = parseDouble(getParameter("buffer-shrink-threshold-factor"));
		
		bufferSize = parseInt(getParameter("buffer-size"));
		
		maxMemoryBufferCount = parseInt(getParameter("max-memory-buffer-count"));
		if (maxMemoryBufferCount < 0) maxMemoryBufferCount = 30;
		maxVideoBufferCount = parseInt(getParameter("max-video-buffer-count"));
		if (maxVideoBufferCount < 0) maxVideoBufferCount = 300;
		
		mp3FramesPerChunk = parseInt(getParameter("mp3-frames-per-chunk"));

		wavAudioLineBufferSize = parseInt(getParameter("wav-audio-line-buffer-size"));
		wavIntermediateBufferSize = parseInt(getParameter("wav-intermediate-buffer-size"));

		wavLevelChangeCallback = getParameter("wav-level-change-callback");

		processFrameCallback = getParameter("process-frame-callback");
		processFrameNumberOfConsumerThreads = parseInt(getParameter("process-frame-number-of-consumer-threads"));
		if (processFrameNumberOfConsumerThreads <= 0) processFrameNumberOfConsumerThreads = 1; 
		
		return this;
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
	private void addOptNV(String param, String opt, List<String> command, String dflt) { _addOptNV(param, opt, command, getParameter(PARAM_PREFIX + param), dflt); }
	private void addOptNV(String param, String opt, List<String> command) { addOptNV(param, opt, command, null); }
	private void addOptNV(String name, List<String> command, String dflt) { addOptNV(name, name, command, dflt); }
	private void addOptNV(String name, List<String> command) { addOptNV(name, name, command); }
	private void addOpt_V(String name, List<String> command, String dflt) { addOptNV(name, null, command, dflt); }
	private void addOptN_(String name, List<String> command) { if (!isNo(getParameter(PARAM_PREFIX + name))) { String _opt = "-" + name; command.add(_opt); optName.put(name, _opt); } }
	
	private enum OutputFormat { none, mjpeg, mp3, mp4, webm, wav, other, unknown }
	private OutputFormat getOutputFormat() {
		return optValue.get("o") != null ? optValue.get("o").startsWith("pipe:") ?
				"mjpeg".equalsIgnoreCase(optValue.get("f:o")) ? OutputFormat.mjpeg :
				"mp3".equalsIgnoreCase(optValue.get("f:o")) ? OutputFormat.mp3 :
				"mp4".equalsIgnoreCase(optValue.get("f:o")) ? OutputFormat.mp4 :
				"webm".equalsIgnoreCase(optValue.get("f:o")) ? OutputFormat.webm :
				"wav".equalsIgnoreCase(optValue.get("f:o")) ? OutputFormat.wav :
				OutputFormat.other : OutputFormat.unknown : OutputFormat.none;
	}
	
	public boolean HasInput() { addOptNV("i", new ArrayList<String>()); return optValue.containsKey("i"); } 
	
	private boolean NoAudio() { return optValue.containsKey("an"); }
	private boolean NoVideo() { return optValue.containsKey("vn"); }
	
	public boolean isPlaying() { return ffmt != null && ffmt.isAlive(); }
	
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
		addOptNV("list_devices", command);
		addOptNV("analyzeduration", command);
		addOptNV("probesize", command);
		addOptNV("r", command);
		addOptN_("re", command);
		addOptNV("f:i", "f", command/*, "flv"*/);
		addOptNV("show_region", command);
		addOptNV("framerate", command);
		addOptNV("pixel_format", command);
		addOptNV("vcodec", command);
		addOptNV("offset_x", command);
		addOptNV("offset_y", command);
		addOptNV("video_size", command);
		addOptNV("audio_buffer_size", command);
		addOptNV("list_options", command);
		addOptNV("flv_metadata", command);
		addOptNV("rtmp_buffer", command);
		addOptNV("rtmp_live", command);
		addOptNV("i", command);
		addOptNV("frames:d", command);
		addOptNV("map", command);
		addOptNV("map1", "map", command);
		addOptNV("map2", "map", command);
		addOptN_("an", command);
		addOptNV("c:a", command);
		addOptNV("q:a", command);
		addOptNV("b:a", command);
		addOptNV("async", command);
		addOptN_("vn", command);
		addOptNV("c:v", command, demux_fMP4 ? "mjpeg" : null);
		addOptNV("q:v", command);
		addOptNV("b:v", command);
		addOptNV("preset", command);
		addOptNV("tune", command);
		addOptNV("pix_fmt", command);
		addOptNV("g", command);
		addOptNV("vsync", command);
		addOptNV("f:o", "f", command, demux_fMP4 ? "mp4" : null);
		addOptNV("movflags", command, demux_fMP4 ? "frag_keyframe+empty_moov" : null);
		addOptN_("y", command);
		addOpt_V("o", command, "pipe:1");
		addOptNV("muxdelay", command);
		addOptNV("muxpreload", command);
		addOptNV("loglevel", command);

		useStderr = (getOutputFormat() == OutputFormat.none);
		
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
		if (!DEBUG_FFMPEG && !useStderr)
			pb.redirectError(ProcessBuilder.Redirect.INHERIT);
		try {
			ffmp = pb.start();
			debug(">" + command/*, "FFMPEG process started."*/);
			(ffmt = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						ffmp.waitFor();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			})).start();
			new Thread(new Runnable() {
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
			}).start();
		} catch (IOException e) {
			e.printStackTrace();
		}
//		assert ffmp.getInputStream().read() == -1;
		if (DEBUG_FFMPEG && !useStderr)
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

	public enum Event { START, STOP }
	
	private void playMediaReader() throws InterruptedException {
		setChanged(); notifyObservers(Event.START);
		MediaReader in_ = null;
		try {
			String contentType = "application/octet-stream";
			boolean video = false;
			switch (getOutputFormat()) {
			case mjpeg:
				in_ = new MjpegInputStream(ffmp.getInputStream(), vBufferSize, bufferGrowFactor);
				contentType = "image/jpeg";
				video = true;
				break;
			case mp3:
//				in_ = new GenericBufferWriter(ffmp.getInputStream(), aBufferSize > 0 ? aBufferSize : 400);
				in_ = new MP3InputStream(ffmp.getInputStream(), aBufferSize, mp3FramesPerChunk, bufferGrowFactor)/*.setSkipTags()*/;
				contentType = "audio/mpeg";
				video = false;
				break;
			case wav:
				mediaStream = null;
				demuxVideoStream = null;
				playWavAudio(ffmp.getInputStream());
				return; 
			case mp4:
				in_ = new fMP4InputStream(ffmp.getInputStream(), bufferGrowFactor);
				contentType = "video/mp4";
				video = true;
				break;
			case webm:
				in_ = new GenericBufferWriter(ffmp.getInputStream(), bufferSize);
				contentType ="video/webm";
				video = true;
				break; 
			case other: case unknown:
				in_ = new GenericBufferWriter(ffmp.getInputStream(), bufferSize);
				break; 
			case none:
				in_ = new GenericBufferWriter(ffmp.getErrorStream(), bufferSize);
				stderrOut = new ByteArrayOutputStream();
				useStderr = true;
				break; 
			}
			mediaStream = new MediaStream(contentType, dropUnusedFrames ? new DoubleBuffer(DEBUG, "Frame") :
					new BufferList(maxMemoryBufferCount, video ? maxVideoBufferCount : 0, DEBUG_BUFFERLIST, "Frame"));
			demuxVideoStream = null;
			final boolean processFrameCallbackSet = !strEmpty(processFrameCallback);
			if (processFrameCallbackSet) {
				final BlockingQueue<Integer> _notifyQueue = mediaStream.multiBuffer.notifyQueue = new ArrayBlockingQueue<Integer>(processFrameNumberOfConsumerThreads + 1);
				final String _contentType = contentType; 
				mediaStream.multiBuffer.startConsumerThreads(processFrameNumberOfConsumerThreads, new Runnable() {
					@Override
					public void run() {
						JSObject jsWindow = JSObject.getWindow(applet);
						DataOut dataOut = new DataOut(_contentType);
						int attempts = 0; 
						try {
							while (_notifyQueue.take() != -200)
								try {
									FrameData fd = mediaStream.multiBuffer.getCurrentFrameData();
									jsWindow.call(processFrameCallback, new Object[] { id, fd.sn, dataOut.toDataUri(fd.bytes) });
									if (attempts > 0)
										attempts = 0; // counts consecutive failures; reset for success
								} catch (JSException | IOException e) {
									e.printStackTrace();
									if (++attempts >= 10)
										break;
								}
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				});
			}
			while (true/* && !ffm_stop*/)
				try {
					int res = mediaStream.multiBuffer.readToBuffer(in_);
					if (res != -1) {
						if (useStderr)
							stderrOut.write(mediaStream.getData());
//						debug("fragment of " + res + " bytes");
					} else
						break;
				} catch (IOException e) {
					e.printStackTrace();
				}
			debug("FFMPEG output thread is ending...");
		}
		finally {
			if (in_ != null) try { in_.close(); } catch (IOException e) { e.printStackTrace(); }
			if (mediaStream != null) { try { mediaStream.close(); } catch (IOException e) { e.printStackTrace(); } mediaStream = null; }
			setChanged(); notifyObservers(Event.STOP);
			debug("FFMPEG output thread ended."/*, "FFMPEG process terminated."*/);
		}
	}

	private void playWavAudio(InputStream inputStream) {
		//final int BUFFER_SIZE = 32;
		try (	RIFFInputStream riffInputStream = new RIFFInputStream(inputStream, 400);
				AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(riffInputStream)	) {
			
//			debug("inputStream position: " + inputStream.getPosition());
//			debug("AudioFormat audioFormat = audioInputStream.getFormat();");
			AudioFormat audioFormat = audioInputStream.getFormat();
			debug("audioFormat = " + audioFormat);
//			debug("inputStream position: " + inputStream.getPosition());
			SourceDataLine audioLine = (SourceDataLine)AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));
//			debug("audioLine.open(audioFormat);");
			debug("applet parameter wav-audio-line-buffer-size = " + wavAudioLineBufferSize);
			if (wavAudioLineBufferSize > 0)
				audioLine.open(audioFormat, wavAudioLineBufferSize);
			else
				audioLine.open(audioFormat);
			int audioLineBufferSize = audioLine.getBufferSize();
			debug("audioLine.getBufferSize() = " + audioLineBufferSize);
			final int BUFFER_SIZE = wavIntermediateBufferSize > 0 ? wavIntermediateBufferSize : audioLineBufferSize;
//			debug("audioLine.start();");
			audioLine.start();
			debug("Playback started.");
			byte[] bytesBuffer = new byte[BUFFER_SIZE];
			ShortBuffer shortBuffer = ByteBuffer.wrap(bytesBuffer).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
			int bytesRead = -1, cnt = 0;
//			long count = 0;
			long sum = 0;
			double sumSquare = 0, minRootMeanSquare = 200;
			int prevSignalLevel = 0;
			final boolean calcSignalLevel = !strEmpty(wavLevelChangeCallback);
			int calcSignalLevelThreshold = 44100; // 44100 bytes / 4 bytes/frame / 44100 frames/sec = 1/4 sec = 250 ms
			ArrayBlockingQueue<Integer> signalLevelQueue = null;
			if (calcSignalLevel) {
				final double calcSignalLevelInterval = 0.1; // sampling interval in seconds: 0.1 sec = 100 ms
				calcSignalLevelThreshold = (int)(calcSignalLevelInterval * audioFormat.getFrameSize() * audioFormat.getFrameRate()); // 17640 bytes = 0.1 sec * 4 bytes/frame * 44100 frames/sec
				final ArrayBlockingQueue<Integer> _signalLevelQueue = new ArrayBlockingQueue<Integer>(10); // ~ 1 sec. worth = 100 ms * 10
				signalLevelQueue = _signalLevelQueue;
				new Thread(new Runnable() {
					@Override
					public void run() {
						int _signalLevel;
						JSObject jsWindow = JSObject.getWindow(applet);
						int attempts = 0; 
						try {
							do {
								_signalLevel = _signalLevelQueue.take();
								try {
									jsWindow.call(wavLevelChangeCallback, new Object[] { id, _signalLevel });
									if (attempts > 0)
										attempts = 0; // counts consecutive failures; reset for success
								} catch (JSException e) {
									e.printStackTrace();
									if (++attempts >= 10)
										break;
								}
							} while (_signalLevel != -200);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}).start();
				// start playing
				signalLevelQueue.offer(-100);
			}
			while ((bytesRead = riffInputStream.read(bytesBuffer, 0, BUFFER_SIZE)) != -1) {
//				debug("read " + bytesRead + " bytes");
//				debug("inputStream position: " + inputStream.getPosition());
				audioLine.write(bytesBuffer, 0, bytesRead);
//				count += bytesRead;
				if (calcSignalLevel) {
					cnt += bytesRead;
					final int shortsRead = bytesRead / 2;
					for (int i = 0; i < shortsRead; i++) {
						final short val = shortBuffer.get(i);
						sum += val;
						sumSquare += val * val;
					}				
					if (cnt >= /*44100*/calcSignalLevelThreshold) { // 44100 bytes / 4 bytes/frame / 44100 frames/sec = 1/4 sec = 250 ms
						final int shortCnt = cnt / 2;
						final double avg = sum / shortCnt, rootMeanSquare = Math.sqrt( sumSquare / shortCnt - avg * avg );
						if (rootMeanSquare < minRootMeanSquare)
							minRootMeanSquare = rootMeanSquare;
						final double signal = 10 * Math.log10( rootMeanSquare / minRootMeanSquare );
						final int signalLevel = (int)Math.floor(signal);
						if (signalLevel != prevSignalLevel) {
							signalLevelQueue.offer(prevSignalLevel = signalLevel);
						}
						sumSquare = sum = cnt = 0;
//						final int available = audioLine.available();
//						debug("audioLine.available(): " + available + "\t\tplayed: " + (count - (audioLineBufferSize - available)) + " bytes; " + audioLine.getLongFramePosition() + " frames; " + audioLine.getMicrosecondPosition() + " µs");
//						debug("signal " + signal);
					}
				}
			}
			if (calcSignalLevel) {
				signalLevelQueue.clear();
				// end playing
				signalLevelQueue.offer(-200);
			}
//			debug("inputStream position: " + inputStream.getPosition());
//			debug("audioLine.drain();");
			audioLine.drain();
			debug("Playback ended.");
		} catch (IOException | UnsupportedAudioFileException | LineUnavailableException e) {
			e.printStackTrace();
		}
	}

	private void playMediaDemuxer() throws InterruptedException {
		setChanged(); notifyObservers(Event.START);
		MediaDemuxer in_ = null;
		boolean hasAudio = !NoAudio(), hasVideo = !NoVideo();
		try {
			mediaStream = hasAudio ? new MediaStream("audio/mpeg", dropUnusedFrames ?
					new DoubleBuffer(DEBUG, "Audio") :
						new BufferList(maxMemoryBufferCount, 0, DEBUG_BUFFERLIST, "Audio")) : null;
			demuxVideoStream = hasVideo ? new MediaStream("image/jpeg", dropUnusedFrames ?
					new DoubleBuffer(new BufferFactory() { @Override public Buffer newBuffer() { return new VideoBuffer(); } }, DEBUG, "Video") :
						new BufferList(new BufferFactory() { @Override public Buffer newBuffer() { return new VideoBuffer(); } },
								maxMemoryBufferCount, maxVideoBufferCount, DEBUG_BUFFERLIST, "Video")) : null;
			in_ = new fMP4DemuxerInputStream(/*new FileBackedAutoReadingInputStream(*/ffmp.getInputStream()/*)*/,
					bufferGrowFactor, bufferShrinkThresholdFactor,
					hasVideo ? demuxVideoStream.multiBuffer : null, hasAudio ? mediaStream.multiBuffer : null,
					hasVideo ? new Gettable() { @Override public void get(Object info, MediaDemuxer self) {
						demuxVideoStream.trackInfo.timeScale = ((fMP4DemuxerInputStream.Trak)info).timeScale;
						demuxVideoStream.trackInfo.width = ((fMP4DemuxerInputStream.Trak)info).width;
						demuxVideoStream.trackInfo.height = ((fMP4DemuxerInputStream.Trak)info).height;
					} } : null,
					hasAudio ? new Gettable() { @Override public void get(Object info, MediaDemuxer self) {
						demuxVideoStream.trackInfo.hasAudio = true;
						fMP4DemuxerInputStream.Trak trak = (fMP4DemuxerInputStream.Trak)info;
						if (trak.sampleSizeInBits > 0 && trak.channels > 0) {
							AudioFormat audioFormat = new AudioFormat(trak.timeScale, trak.sampleSizeInBits, trak.channels, trak.signed, trak.bigEndian);
							self.audio = new AudioLinePlayer().create(audioFormat); // play AudioLine instead of MultiBuffer
						}
					} } : null,
					DEBUG);
			final boolean processFrameCallbackSet = !strEmpty(processFrameCallback);
			if (demuxVideoStream != null && processFrameCallbackSet) {
				final BlockingQueue<Integer> _notifyQueue = demuxVideoStream.multiBuffer.notifyQueue = new ArrayBlockingQueue<Integer>(processFrameNumberOfConsumerThreads + 1);
				final String _contentType = "image/jpeg"; 
				demuxVideoStream.multiBuffer.startConsumerThreads(processFrameNumberOfConsumerThreads, new Runnable() {
					@Override
					public void run() {
						JSObject jsWindow = JSObject.getWindow(applet);
						DataOut dataOut = new DataOut(_contentType);
						int attempts = 0; 
						try {
							while (_notifyQueue.take() != -200)
								try {
									FrameData fd = demuxVideoStream.multiBuffer.getCurrentFrameData();
									jsWindow.call(processFrameCallback, new Object[] { id, fd.sn, dataOut.toDataUri(fd.bytes) });
									if (attempts > 0)
										attempts = 0; // counts consecutive failures; reset for success
								} catch (JSException | IOException e) {
									e.printStackTrace();
									if (++attempts >= 10)
										break;
								}
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				});
			}
			int res = 0;
			while (res != -1/* && !ffm_stop*/)
				try {
					res = in_.readFragment();
//					debug("readFragment() result: " + res);
				} catch (IOException e) {
					e.printStackTrace();
				}
			debug("FFMPEG output thread is ending...");
		}
		finally {
			if (in_ != null) try { in_.close(); } catch (IOException e) { e.printStackTrace(); }
			if (mediaStream != null) { try { mediaStream.close(); } catch (IOException e) { e.printStackTrace(); } mediaStream = null; }
			if (demuxVideoStream != null) { try { demuxVideoStream.close(); } catch (IOException e) { e.printStackTrace(); } demuxVideoStream = null; } 
			setChanged(); notifyObservers(Event.STOP);
			debug("FFMPEG output thread ended.", "FFMPEG process terminated.");
		}
	}

	public byte[] getData() throws IOException { return mediaStream != null ? mediaStream.getData() : null; }

	public String getDataURI() throws IOException { return mediaStream != null ? mediaStream.getDataURI() : null; }
	
	public int getSN() { return mediaStream != null ? mediaStream.multiBuffer.getSN() : 0; }
	
	int getQueueLength() { return mediaStream != null ? mediaStream.multiBuffer.getQueueLength() : 0; }
	
	public boolean isStreaming() { return mediaStream != null ? mediaStream.isStreaming() : false; }
	
	public String startHttpServer() throws InterruptedException { return mediaStream != null ? mediaStream.startHttpServer() : null; } 

	public String getVideoDataURI() throws IOException { return demuxVideoStream != null ? demuxVideoStream.getDataURI() : null; }
	
	public int getVideoSN() { return demuxVideoStream != null ? demuxVideoStream.multiBuffer.getSN() : 0; }

	public int getVideoQueueLength() { return demuxVideoStream != null ? demuxVideoStream.multiBuffer.getQueueLength() : 0; }

	public long getVideoTimestamp() { VideoBuffer cvb = demuxVideoStream != null ? (VideoBuffer)demuxVideoStream.multiBuffer.getCurrentBuffer() : null; return cvb != null ? cvb.timestamp : 0L; }
	
	public TrackInfo getVideoTrackInfo() { return demuxVideoStream != null ? demuxVideoStream.trackInfo : null; }

	public String getStderrData() throws IOException { return stderrOut != null ? stderrOut.toString("UTF-8") : null; }
	
	public boolean isDebug() { return DEBUG; }

	private void quitProcess() {
		if (useStderr)
			return;
		try {
//			debug("quitProcess() call...");
			if (isPlaying()) {
				OutputStream out_ = ffmp.getOutputStream();
				if (out_ != null) {
					debug("Signal to quit FFMPEG process.");
					out_.write('q');
					out_.flush();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void killProcess() {

//		debug("killProcess() call...");
		
//		this.ffm_stop = true;
//		if (this.ffmt != null)
//			this.ffmt.interrupt();

		if (isPlaying()) {
			debug("Killing FFMPEG process.");
			ffmp.destroy();
		}
	}

	public void stopPlayback() {

		if (isPlaying()) {
			quitProcess();
			try {
				ffmt.join(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if (isPlaying())
			killProcess();

		if (mediaStream != null)
			mediaStream.multiBufferNotify();
	}
	
	public void kill() {
		
		killProcess();

		if (mediaStream != null)
			mediaStream.multiBufferNotify();
	}

//	public static void main(String[] args) {
//
//	}

}
