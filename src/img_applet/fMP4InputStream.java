package img_applet;

import java.io.IOException;
import java.io.InputStream;

public class fMP4InputStream extends ImgApplet.MediaReader {

	public fMP4InputStream(InputStream in, double growFactor) {
		super(in);
		this.growFactor = growFactor;
	}
	
	protected double growFactor;

//	private int byteCount;
	
	@Override
	public int read(ImgApplet.Buffer buf) throws IOException {
		int b_;
		CircularBuffer cb = new CircularBuffer(8);
		while (true) {
			if ((b_ = in.read()) == -1)
				return -1;
//			byteCount++;
			cb.put(b_);
			if (	(cb.get(4) == (byte)'m' && ((cb.get(5) == (byte)'d' && cb.get(6) == (byte)'a' && cb.get(7) == (byte)'t') || // mdat
					((cb.get(5) == (byte)'o' && cb.get(6) == (byte)'o') && (cb.get(7) == (byte)'f' || // moof
					cb.get(7) == (byte)'v')))) || // moov
					(cb.get(4) == (byte)'f' && cb.get(5) == (byte)'t' && cb.get(6) == (byte)'y' && cb.get(7) == (byte)'p')) { // ftyp
				int len_ = cb.getInt(0);
				if (len_ < 8)
					throw new IOException("Improper box size: " + len_);
				if (buf.size < len_) { buf.grow(len_, growFactor); }
				cb.read(buf.b, 0, 8);
				int res = in.read(buf.b, 8, len_ - 8);
//				if (res > 0)
//					byteCount += res;
				return res == -1 || res < len_ - 8 ? -1 : len_;
			}
		}
	}
	
//	public static void main(String[] args) throws IOException {
//		final int BUFLEN = 3000000;
//		byte[] buf = new byte[BUFLEN];
//		File file = new File("C:\\Users\\dmitriy.mukhin\\AppData\\Local\\Temp\\img_applet\\output.mp4");
//		FileInputStream reader = new FileInputStream(file);
//		fMP4InputStream mp4reader = new fMP4InputStream(reader);
//		int res;
//		while ((res = mp4reader.read(buf, 0, BUFLEN)) != -1)
//			System.out.println("Frame: " + res + " bytes; Total: " + mp4reader.byteCount + " bytes");
//		System.out.println("Total: " + mp4reader.byteCount + " bytes");
//		mp4reader.close();
//	}

}
