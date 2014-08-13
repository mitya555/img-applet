package img_applet;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MjpegInputStream extends FilterInputStream {

	public MjpegInputStream(InputStream in) { super(in); }
	
	public int readFrame(byte[] b, int off, int len) throws IOException {
		int off_ = off;
		int b_, b_prev = 0;
		while ((b_ = in.read()) != 0xD8 || b_prev != 0xFF) {
			if (b_ == -1)
				return -1;
			b_prev = b_; 
		}
		if (len < 2)
			throw new IOException("Insufficient buffer length");
		b[off_++] = (byte)0xFF;
		b[off_++] = (byte)0xD8;
		b_prev = b_;
		while ((b_ = in.read()) != 0xD9 || b_prev != 0xFF) {
			if (b_ == -1)
				return -1;
			if (len < off_ - off + 1)
				throw new IOException("Insufficient buffer length");
			b[off_++] = (byte)(b_prev = b_);
		}
		if (len < off_ - off + 1)
			throw new IOException("Insufficient buffer length");
		b[off_++] = (byte)0xD9;
		return off_ - off;
	}

	public int readFrame(byte[] b) throws IOException { return readFrame(b, 0, b.length); }
}
