package img_applet;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;

public class FileBackedAutoReadingInputStream extends FilterInputStream {

	protected FileBackedAutoReadingInputStream(InputStream in) {
		super(in);
	}
	
	static private abstract class _Buf {
		int read, write;
		byte[] buf;
		int write(byte[] b, int off, int len) throws IOException {
			int res = Math.min(len, buf.length - write);
			if (res > 0) {
				System.arraycopy(b, off, buf, write, res);
				write += res;
			}
			return res;
		}
		int read() {
			return hasData() ? buf[read++] << 24 >>> 24 : -1;
		}
		boolean hasData() { return read < write; }
		_Buf reset() { read = write = 0; return this; }
		boolean atEndForWrite() { return write == buf.length; }
		boolean isMem() { return this instanceof _Mem; }
		boolean isFile() { return this instanceof _File; }
	}
	
	static private class _File extends _Buf {
		@Override
		int write(byte[] b, int off, int len) throws IOException {
			int res = (int) Math.min(len, file.length() - write);
			if (res > 0) {
				bb.position(write);
				bb.put(b, off, res);
				write = bb.position();
			}
			return res;
		}
		@Override
		int read() {
			return hasData() ? bb.get(read++) << 24 >>> 24 : -1;
		}
		RandomAccessFile file;
		ByteBuffer bb;
		_File() throws IOException {
			File temp = File.createTempFile("img_applet_", null);
			temp.deleteOnExit();
			file = new RandomAccessFile(temp, "rw");
			file.setLength(1024 * 1024);
			bb = file.getChannel().map(MapMode.READ_WRITE, 0, file.length());
		}
	}
	
	static private class _Mem extends _Buf {
		_Mem() { buf = new byte[100000]; }
	}
	
	static private class _BufList {
		static class _Item { volatile _Item next; _Buf buf; _Item(_Buf buf) { this.buf = buf; } }
		volatile _Item beforeRead, write;
		synchronized void addAfterWrite(_Buf buf) {
			_Item _i = new _Item(buf);
			if (beforeRead == null)
				write.next = beforeRead = write = _i;
			else {
				_i.next = write.next;
				write.next = _i;
				if (beforeRead == write)
					beforeRead = _i;
			}
		}
		synchronized void moveReadToWrite() {
			if (beforeRead != null) {
				_Buf buf = beforeRead.next.buf;
				beforeRead.next = beforeRead.next.next;
				addAfterWrite(buf.reset());
			}
		}
		synchronized void advanceWrite() throws IOException { if (write == beforeRead) addAfterWrite(new _File()); write = write.next; }
		void write(byte[] b, int off, int len) throws IOException {
			if (write == null) { addAfterWrite(new _Mem()); addAfterWrite(new _Mem()); synchronized (this) { this.notify(); } } // 2 memory buffers at the beginning
			else if (write.buf.isFile() && write != beforeRead && write.next.buf.isMem()) write = write.next;
			else if (write.buf.atEndForWrite()) advanceWrite();
			int res = 0;
			boolean notified = false;
			while ((res += write.buf.write(b, off + res, len - res)) < len) {
				if (res > 0 && !notified) { synchronized (this) { this.notify(); } notified = true; }
				advanceWrite();
			}
			if (res > 0 && !notified) { synchronized (this) { this.notify(); } }
			if (write.buf.atEndForWrite()) advanceWrite();
		}
		int read() throws InterruptedException {
			while (!beforeRead.next.buf.hasData() && beforeRead.next != write)
				moveReadToWrite();
			_Buf buf = beforeRead.next.buf;
			synchronized (this) { if (!buf.hasData()) { if (readingThread.isAlive()) this.wait(); else return -1; } }
			int res = buf.read();
			if (!buf.hasData() && beforeRead.next != write)
				moveReadToWrite();
			return res;
		}
		Thread readingThread;
		_BufList(Runnable runnable) { this.readingThread = new Thread(runnable); }
	}

	private _BufList bufList = new _BufList(new Runnable() {
			@Override
			public void run() {
				final int tmp_size = 8192;
				byte[] tmp = new byte[tmp_size];
				int res;
				try {
					while ((res = in.read(tmp, 0, tmp_size)) != -1)
						bufList.write(tmp, 0, res);
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					synchronized (bufList) { bufList.notify(); }
					try {
						in.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		});
	private boolean neverStarted = true;
	
	@Override
	public int read() throws IOException {
		if (neverStarted)
			synchronized (bufList) { 
				bufList.readingThread.start();
				neverStarted = false;
				try { bufList.wait(); } catch (InterruptedException e) { throw new IOException(e); }
			}
		try { return bufList.read(); } catch (InterruptedException e) { throw new IOException(e); }
	}
}
