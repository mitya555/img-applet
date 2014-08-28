package img_applet;

//import java.io.File;
//import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MP3InputStream extends FilterInputStream {

	public MP3InputStream(InputStream in) { super(in); }
	
	private boolean skipTags;
	public MP3InputStream setSkipTags() { skipTags = true; return this; }
	
	private int readThisManyDataFramesAtOnce = 20;
	public MP3InputStream setNumberOfDataFramesToReadAtOnce(int num) { readThisManyDataFramesAtOnce = num; return this; } 
	
	private CircularBuffer buf = new CircularBuffer(3);
	
//	private int byteCount;

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int b_, off_ = off;
		int readingData = 0;
		while (true) {
			if (buf.get(0) == (byte)0xFF && buf.get(1) == (byte)0xFB) { // data
				if (off_ - off > 0 && readingData == 20)
					return off_ - off;
				readingData++;
			} else if (buf.get(0) == (byte)'I' && buf.get(1) == (byte)'D' && buf.get(2) == (byte)'3') { // ID3 tag
				if (off_ - off > 0 && (!skipTags || readingData == 20))
					return off_ - off;
				if (len < 10)
					throw new IOException("Insufficient buffer length");
				buf.clear();
				System.arraycopy(new byte[] { (byte)'I', (byte)'D', (byte)'3' }, 0, b, off, 3);
				int len_ = in.read(b, off + 3, 7);
//				byteCount += len_;
				if (len_ == -1 || len_ < 7)
					return -1;
				len_ = 0x00000000 | b[off + 6] << 21 | b[off + 7] << 14 | b[off + 8] << 7 | b[off + 9];
				if (len < 10 + len_)
					throw new IOException("Insufficient buffer length");
				int res = in.read(b, off + 10, len_);
//				byteCount += res;
				return res == -1 || res < len_ ? -1 : 10 + res;
			} else  if (buf.get(0) == (byte)'T' && buf.get(1) == (byte)'A' && buf.get(2) == (byte)'G') { // TAG
				if (off_ - off > 0)
					return off_ - off;
				if (len < 128)
					throw new IOException("Insufficient buffer length");
				buf.clear();
				System.arraycopy(new byte[] { (byte)'T', (byte)'A', (byte)'G' }, 0, b, off, 3);
				int res = in.read(b, off + 3, 125);
//				byteCount += res;
				return res == -1 || res < 125 ? -1 : 128;
			}
			if (readingData > 0)
			{
				if (off_ - off == len)
					throw new IOException("Insufficient buffer length");
				b[off_++] = (byte)buf.get(0);
			}
			b_ = in.read();
//			byteCount++;
			if (b_ == -1)
				return -1;
			buf.put(b_);
		}
	}

	@Override
	public int read(byte[] b) throws IOException { return read(b, 0, b.length); }

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
