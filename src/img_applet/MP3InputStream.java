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
				b[off_++] = (byte)'I';
				b[off_++] = (byte)'D';
				b[off_++] = (byte)'3';
				int len_ = in.read(b, off_, 7);
				if (len_ == -1 || len_ < 7)
					return -1;
				len_ = 0x00000000 | b[off_ + 3] << 21 | b[off_ + 4] << 14 | b[off_ + 5] << 7 | b[off_ + 6];
				if (len < 10 + len_)
					throw new IOException("Insufficient buffer length");
				len_ = in.read(b, off_ + 7, len_);
				return len_ == -1 ? -1 : 10 + len_;
			} else  if (buf.get(0) == 'T' && buf.get(1) == 'A' && buf.get(2) == 'G') { // TAG
				if (off_ - off > 0)
					return off_ - off;
				if (len < 128)
					throw new IOException("Insufficient buffer length");
				buf.clear();
				b[off_++] = (byte)'T';
				b[off_++] = (byte)'A';
				b[off_++] = (byte)'G';
				int len_ = in.read(b, off_, 125);
				if (len_ == -1 || len_ < 125)
					return -1;
				return 128;
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
