package img_applet;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class fMP4InputStream extends FilterInputStream {

	public fMP4InputStream(InputStream in) { super(in); }

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int b_;
		CircularBuffer buf = new CircularBuffer(8);
		while (true) {
			if ((b_ = in.read()) == -1)
				return -1;
			buf.put(b_);
			if (	(buf.get(4) == (byte)'m' && ((buf.get(5) == (byte)'d' && buf.get(6) == (byte)'a' && buf.get(7) == (byte)'t') || // mdat
					((buf.get(5) == (byte)'o' && buf.get(6) == (byte)'o') && (buf.get(7) == (byte)'f' || // moof
					buf.get(7) == (byte)'v')))) || // moov
					(buf.get(4) == (byte)'f' && buf.get(5) == (byte)'t' && buf.get(6) == (byte)'y' && buf.get(7) == (byte)'p')) { // ftyp
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
