package img_applet;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class fMP4InputStream extends FilterInputStream {

	public fMP4InputStream(InputStream in) { super(in); }
	
	private class CircularBuffer
	{
		private int len, ptr = 0;
		private byte[] buf;
		public CircularBuffer(int len) { this.len = len; buf = new byte[len]; }
		public void put(int b) { buf[ptr++] = (byte)b; if (ptr == len) ptr = 0; }
		public int get(int i) { return buf[(ptr + i) % len]; }
		public boolean check(int i, char... cs) { boolean res = true; for (int j = 0; j < cs.length; j++) res &= (get(i + j) == (byte)cs[j]); return res; }
		public int getInt(int i) { return get(i) << 24 | get(i + 1) << 16 | get(i + 2) << 8 | get(i + 3); }
		public int read(byte[] b, int off, int len) {
			int len_ = len < this.len ? len : this.len;
			for (int i = 0; i < len_; i++)
				b[off + i] = (byte)get(i);
			return len_;
		}
	}
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int b_;
		CircularBuffer buf = new CircularBuffer(8);
		while (true) {
			if ((b_ = in.read()) == -1)
				return -1;
			buf.put(b_);
			if (	buf.check(4, 'm', 'd', 'a', 't') ||
					buf.check(4, 'm', 'o', 'o', 'f') ||
					buf.check(4, 'm', 'o', 'o', 'v') ||
					buf.check(4, 'f', 't', 'y', 'p')) {
				int len_ = buf.getInt(0);
				if (len < len_)
					throw new IOException("Insufficient buffer length");
				buf.read(b, off, 8);
				return in.read(b, off + 8, len_ - 8) == -1 ? -1 : len_;
			}
		}
	}

	@Override
	public int read(byte[] b) throws IOException { return read(b, 0, b.length); }
}
