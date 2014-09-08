package img_applet;

//import java.io.File;
//import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class fMP4InputStream extends FilterInputStream {

	public fMP4InputStream(InputStream in) { super(in); }

//	private int byteCount;
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int b_;
		CircularBuffer buf = new CircularBuffer(8);
		while (true) {
			if ((b_ = in.read()) == -1)
				return -1;
//			byteCount++;
			buf.put(b_);
			if (	(buf.get(4) == (byte)'m' && ((buf.get(5) == (byte)'d' && buf.get(6) == (byte)'a' && buf.get(7) == (byte)'t') || // mdat
					((buf.get(5) == (byte)'o' && buf.get(6) == (byte)'o') && (buf.get(7) == (byte)'f' || // moof
					buf.get(7) == (byte)'v')))) || // moov
					(buf.get(4) == (byte)'f' && buf.get(5) == (byte)'t' && buf.get(6) == (byte)'y' && buf.get(7) == (byte)'p')) { // ftyp
				int len_ = buf.getInt(0);
				if (len_ < 0)
					throw new IOException("Negative length: " + len_);
				if (len < len_)
					throw new IOException("Insufficient buffer length");
				buf.read(b, off, 8);
				int res = in.read(b, off + 8, len_ - 8);
//				if (res > 0)
//					byteCount += res;
				return res == -1 || res < len_ - 8 ? -1 : len_;
			}
		}
	}

	@Override
	public int read(byte[] b) throws IOException { return read(b, 0, b.length); }
	
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
