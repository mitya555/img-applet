package img_applet;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MP3InputStream extends FilterInputStream {

	public MP3InputStream(InputStream in) { super(in); }
	
	private CircularBuffer buf = new CircularBuffer(3);

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int b_, off_ = off;
		boolean readingData = false;
		while (true) {
			if (buf.get(0) == 0xFF && buf.get(1) == 0xFB) {
				if (off_ - off > 0)
					return off_ - off;
				readingData = true;
			} else if (buf.get(0) == 'I' && buf.get(1) == 'D' && buf.get(2) == '3') {
				if (off_ - off > 0)
					return off_ - off;
				if (len < 10)
					throw new IOException("Insufficient buffer length");
				b[off_++] = (byte)'I';
				b[off_++] = (byte)'D';
				b[off_++] = (byte)'3'; 
			} else  if (buf.get(0) == 'T' && buf.get(1) == 'A' && buf.get(2) == 'G') {
				if (off_ - off > 0)
					return off_ - off;
				
			}
			if (readingData)
			{
				if (off_ - off == len)
					throw new IOException("Insufficient buffer length");
				b[off_++] = (byte)buf.get(0);
//				int len_ = buf.getInt(0);
//				if (len < len_)
//					throw new IOException("Insufficient buffer length");
//				buf.read(b, off, 8);
//				return in.read(b, off + 8, len_ - 8) == -1 ? -1 : len_;
			}
			if ((b_ = in.read()) == -1)
				return -1;
			buf.put(b_);
		}
	}

	@Override
	public int read(byte[] b) throws IOException { return read(b, 0, b.length); }
}
