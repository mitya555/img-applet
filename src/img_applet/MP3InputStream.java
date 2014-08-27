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
			if (buf.get(0) == 0xFF && buf.get(1) == 0xFB) { // data
				if (off_ - off > 0)
					return off_ - off;
				readingData = true;
			} else if (buf.get(0) == 'I' && buf.get(1) == 'D' && buf.get(2) == '3') { // ID3 tag
				if (off_ - off > 0)
					return off_ - off;
				if (len < 10)
					throw new IOException("Insufficient buffer length");
				buf.clear();
				System.arraycopy(new byte[] { 'I', 'D', '3' }, 0, b, off, 3);
				int len_ = in.read(b, off + 3, 7);
				if (len_ == -1 || len_ < 7)
					return -1;
				len_ = 0x00000000 | b[off + 6] << 21 | b[off + 7] << 14 | b[off + 8] << 7 | b[off + 9];
				if (len < 10 + len_)
					throw new IOException("Insufficient buffer length");
				int res = in.read(b, off + 10, len_);
				return res == -1 || res < len_ ? -1 : 10 + res;
			} else  if (buf.get(0) == 'T' && buf.get(1) == 'A' && buf.get(2) == 'G') { // TAG
				if (off_ - off > 0)
					return off_ - off;
				if (len < 128)
					throw new IOException("Insufficient buffer length");
				buf.clear();
				System.arraycopy(new byte[] { 'T', 'A', 'G' }, 0, b, off, 3);
				int res = in.read(b, off + 3, 125);
				return res == -1 || res < 125 ? -1 : 128;
			}
			if (readingData)
			{
				if (off_ - off == len)
					throw new IOException("Insufficient buffer length");
				b[off_++] = (byte)buf.get(0);
			}
			if ((b_ = in.read()) == -1)
				return -1;
			buf.put(b_);
		}
	}

	@Override
	public int read(byte[] b) throws IOException { return read(b, 0, b.length); }
}
