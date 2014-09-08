package img_applet;

import java.util.Arrays;

public class CircularBuffer {
	private int len, ptr = 0;
	private byte[] buf;
	public CircularBuffer(int len) { this.len = len; buf = new byte[len]; }
	public void put(int b) { buf[ptr++] = (byte)b; if (ptr == len) ptr = 0; }
	public byte get(int i) { return buf[(ptr + i) % len]; }
	public int getInt(int i) { return get(i) << 24 | get(i + 1) << 24 >>> 8 | get(i + 2) << 24 >>> 16 | get(i + 3) << 24 >>> 24; }
	public int read(byte[] b, int off, int len) {
		int len_ = len < this.len ? len : this.len;
		for (int i = 0; i < len_; i++)
			b[off + i] = get(i);
		return len_;
	}
	public void clear() { Arrays.fill(buf, (byte)0); }
}
