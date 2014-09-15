package img_applet;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MjpegInputStream extends FilterInputStream implements BufferWriter {

	public MjpegInputStream(InputStream in) { super(in); }
	
	@Override
	public int read(ImgApplet.Buffer buf) throws IOException {
		int off_ = 0;
		int b_, b_prev = 0;
		while ((b_ = in.read()) != 0xD8 || b_prev != 0xFF) {
			if (b_ == -1)
				return -1;
			b_prev = b_; 
		}
		int len = buf.size;
		byte[] b = buf.b;
		if (len < 2) { len = buf.alloc(5000); b = buf.b; }
		b[off_++] = (byte)0xFF;
		b[off_++] = (byte)0xD8;
		b_prev = b_;
		while ((b_ = in.read()) != 0xD9 || b_prev != 0xFF) {
			if (b_ == -1)
				return -1;
			if (len < off_ + 1) { len = buf.alloc(off_ + 1); System.arraycopy(b, 0, buf.b, 0, off_); b = buf.b; }
			b[off_++] = (byte)(b_prev = b_);
		}
		if (len < off_ + 1) { len = buf.alloc(off_ + 1); System.arraycopy(b, 0, buf.b, 0, off_); b = buf.b; }
		b[off_++] = (byte)0xD9;
		return off_;
	}
}
