package img_applet;

import java.io.IOException;
import java.io.InputStream;

public class MjpegInputStream extends ImgApplet.MediaReader {

	public MjpegInputStream(InputStream in, int initBufferSize, double growFactor) {
		super(in);
		this.initBufferSize = initBufferSize > 0 ? initBufferSize  : 5000;
		this.growFactor = growFactor < 1.0 ? 1.333333333 : growFactor;
	}
	
	protected int initBufferSize;
	protected double growFactor;
	
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
		if (len < 2) { len = buf.alloc(initBufferSize); b = buf.b; }
		b[off_++] = (byte)0xFF;
		b[off_++] = (byte)0xD8;
		b_prev = b_;
		while ((b_ = in.read()) != 0xD9 || b_prev != 0xFF) {
			if (b_ == -1)
				return -1;
			if (len < off_ + 1) { len = buf.grow(off_ + 1, growFactor); System.arraycopy(b, 0, buf.b, 0, off_); b = buf.b; }
			b[off_++] = (byte)(b_prev = b_);
		}
		if (len < off_ + 1) { len = buf.grow(off_ + 1, growFactor); System.arraycopy(b, 0, buf.b, 0, off_); b = buf.b; }
		b[off_++] = (byte)0xD9;
		return off_;
	}
}
