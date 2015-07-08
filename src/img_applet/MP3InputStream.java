package img_applet;

import img_applet.FFmpegProcess.Buffer;
import img_applet.FFmpegProcess.MediaReader;

import java.io.IOException;
import java.io.InputStream;

public class MP3InputStream extends MediaReader {

	public MP3InputStream(InputStream in, int initBufferSize, int dataFramesInFragment, double growFactor) {
		super(in);
		this.dataFramesInFragment = dataFramesInFragment > 0 ? dataFramesInFragment : 10;
		this.initBufferSize = initBufferSize > 0 ? initBufferSize  : 400 * this.dataFramesInFragment;
		this.growFactor = growFactor < 1.0 ? 1.333333333 : growFactor;
	}
	
	protected int initBufferSize;
	protected double growFactor;
	
	private boolean skipTags;
	public MP3InputStream setSkipTags() { skipTags = true; return this; }
	
	private int dataFramesInFragment;
	
	private CircularBuffer cb = new CircularBuffer(3);
	
//	private int byteCount;
	
	private static int getInt(byte[] b, int off) {
		return b[off] << 24 >>> 3 | b[off + 1] << 24 >>> 10 | b[off + 2] << 24 >>> 17 | b[off + 3] << 24 >>> 24;
	}

	@Override
	public int read(Buffer buf) throws IOException {
		int len = buf.size;
		byte[] b = buf.b;
		int b_, off_ = 0;
		int readingData = 0;
		while (true) {
			if (cb.get(0) == (byte)0xFF && cb.get(1) == (byte)0xFB) { // data
				if (off_ > 0 && readingData == dataFramesInFragment)
					return off_;
				readingData++;
			} else if (cb.get(0) == (byte)'I' && cb.get(1) == (byte)'D' && cb.get(2) == (byte)'3') { // ID3 tag
				if (off_ > 0 && (!skipTags || readingData == dataFramesInFragment))
					return off_;
				if (skipTags) {
					cb.clear();
					byte[] tmp = new byte[7];
					int len_ = in.read(tmp, 0, 7);
//					byteCount += len_;
					if (len_ == -1 || len_ < 7)
						return -1;
					len_ = getInt(tmp, 3);
					long res = in.skip(len_);
//					byteCount += res;
					if (res == -1 || res < len_)
						return -1;
				} else {
					if (len < 10) { len = buf.alloc(initBufferSize); b = buf.b; }
					cb.clear();
					System.arraycopy(new byte[] { (byte)'I', (byte)'D', (byte)'3' }, 0, b, 0, 3);
					int len_ = in.read(b, 3, 7);
//					byteCount += len_;
					if (len_ == -1 || len_ < 7)
						return -1;
					len_ = getInt(b, 6);
					if (len < 10 + len_) { len = buf.grow(10 + len_, growFactor); System.arraycopy(b, 0, buf.b, 0, 10); b = buf.b; }
					int res = in.read(b, 10, len_);
//					byteCount += res;
					return res == -1 || res < len_ ? -1 : 10 + res;
				}
			} else  if (cb.get(0) == (byte)'T' && cb.get(1) == (byte)'A' && cb.get(2) == (byte)'G') { // TAG
				if (off_ > 0 && (!skipTags || readingData == dataFramesInFragment))
					return off_;
				if (skipTags) {
					cb.clear();
					long res = in.skip(125);
//					byteCount += res;
					if (res == -1 || res < 125)
						return -1;
				} else {
					if (len < 128) { len = buf.alloc(initBufferSize > 128 ? initBufferSize : 128); b = buf.b; }
					cb.clear();
					System.arraycopy(new byte[] { (byte)'T', (byte)'A', (byte)'G' }, 0, b, 0, 3);
					int res = in.read(b, 3, 125);
//					byteCount += res;
					return res == -1 || res < 125 ? -1 : 128;
				}
			}
			if (readingData > 0)
			{
				if (off_ == len) { if (off_ == 0) len = buf.alloc(initBufferSize); else { len = buf.grow(off_ + 1, growFactor); System.arraycopy(b, 0, buf.b, 0, off_); } b = buf.b; }
				b[off_++] = cb.get(0);
			}
			b_ = in.read();
//			byteCount++;
			if (b_ == -1)
				return -1;
			cb.put(b_);
		}
	}

//	public static void main(String[] args) throws IOException {
//		final int BUFLEN = 300000;
//		byte[] buf = new byte[BUFLEN];
//		File file = new File("C:\\Documents and Settings\\Mitya\\Local Settings\\Temp\\img_applet\\output.mp3");
//		FileInputStream reader = new FileInputStream(file);
//		MP3InputStream mp3reader = new MP3InputStream(reader);
//		int res;
//		while ((res = mp3reader.read(buf, 0, BUFLEN)) != -1)
//			System.out.println("Read " + res + " bytes");
//		mp3reader.close();
//	}

}
