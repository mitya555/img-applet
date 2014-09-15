package img_applet;

import java.util.Arrays;

public class CircularBuffer {
	public int len, ptr = 0;
	public byte[] buf;
	public CircularBuffer(int len) { this.len = len; buf = new byte[len]; }
	public void put(int b) { buf[ptr++] = (byte)b; if (ptr == len) ptr = 0; }
	public byte get(int i) { return buf[(ptr + i) % len]; }
	public int getInt(int i) { return ptr > 0 ?
			get(i) << 24 | get(i + 1) << 24 >>> 8 | get(i + 2) << 24 >>> 16 | get(i + 3) << 24 >>> 24 :
			buf[i] << 24 | buf[i + 1] << 24 >>> 8 | buf[i + 2] << 24 >>> 16 | buf[i + 3] << 24 >>> 24; }
	public int getLong(int i) { return ptr > 0 ?
			get(i) << 56 | get(i + 1) << 56 >>> 8 | get(i + 2) << 56 >>> 16 | get(i + 3) << 56 >>> 24 | get(i + 4) << 56 >>> 32 | get(i + 5) << 56 >>> 40 | get(i + 6) << 56 >>> 48 | get(i + 7) << 56 >>> 56 :
			buf[i] << 56 | buf[i + 1] << 56 >>> 8 | buf[i + 2] << 56 >>> 16 | buf[i + 3] << 56 >>> 24 | buf[i + 4] << 56 >>> 32 | buf[i + 5] << 56 >>> 40 | buf[i + 6] << 56 >>> 48 | buf[i + 7] << 56 >>> 56; }
	public boolean check(int i, char ... cs) {
		if (ptr > 0) {
			for (int j = 0; j < cs.length; j++)
				if (get(i + j) != (byte)cs[j])
					return false;
		} else {
			for (int j = 0; j < cs.length; j++)
				if (buf[i + j] != (byte)cs[j])
					return false;
		}
		return true;
	}
	public boolean check4(int i, char c0, char c1, char c2, char c3) {
		if (ptr > 0)
			return get(i) == (byte)c0 && get(i + 1) == (byte)c1 && get(i + 2) == (byte)c2 && get(i + 3) == (byte)c3;
		else
			return buf[i] == (byte)c0 && buf[i + 1] == (byte)c1 && buf[i + 2] == (byte)c2 && buf[i + 3] == (byte)c3;
	}
	public int read(byte[] b, int off, int len) {
		int len_ = len < this.len ? len : this.len;
		for (int i = 0; i < len_; i++)
			b[off + i] = get(i);
		return len_;
	}
	public void clear() { Arrays.fill(buf, (byte)0); ptr = 0; }
}
