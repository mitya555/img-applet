package img_applet;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class RIFFInputStream extends FilterInputStream {

	private long _RIFFFileSize, pos = 0, mark = 0;
	
	public RIFFInputStream(InputStream in, long _RIFFFileSize) {
		super(in);
		this._RIFFFileSize = _RIFFFileSize;
	}
	
	public long getPosition() { return pos; }

	@Override
	public int read() throws IOException {
		int b = super.read();
		if (b >= 0) {
//			System.out.println("1 at " + pos);
			if (pos > 3 && pos < 8) {
				b = (int)fileSizeByte(pos);
			}
			pos++;
		}
		return b;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int n = super.read(b, off, len);
		if (n > 0) {
//			System.out.println(n + " at " + pos);
			if ((pos + n - 1) > 3 && pos < 8) {
				int p0 = Math.max((int)pos, 4), p1 = Math.min((int)(pos + n), 8);
				for (int p = p0; p < p1; p++) {
					b[(int)(p - pos) + off] = (byte)fileSizeByte(p);
				}
			}
			pos += n;
		}
		return n;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	private long fileSizeByte(long p) {
		// File Size in RIFF is in reverse byte order
		long r = _RIFFFileSize << (32 + (7 - p) * 8) >>> 56;
//		System.out.println("output " + hex(r) + " at " + p);
		return r;
	}

	@Override
	public long skip(long n) throws IOException {
		n = super.skip(n);
		if (n > 0)
			pos += n;
		return n;
	}

	@Override
	public void mark(int readlimit) {
		super.mark(readlimit);
		mark = pos;
//		System.out.println("mark at " + mark);
	}

	@Override
	public void reset() throws IOException {
		/* A call to reset can still succeed if mark is not supported, but the
		 * resulting stream position is undefined, so it's not allowed here. */
		if (!markSupported())
			throw new IOException("Mark not supported.");
		super.reset();
		pos = mark;
//		System.out.println("reset to " + mark);
	}
	
//	public static String hex(long n) {
//	    // call toUpperCase() if that's required
//	    return String.format("0x%16s", Long.toHexString(n)).replace(' ', '0');
//	}

}
