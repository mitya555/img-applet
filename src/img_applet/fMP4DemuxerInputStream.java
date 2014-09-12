package img_applet;

//import java.io.File;
//import java.io.FileInputStream;
import img_applet.ImgApplet.MultiBuffer;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class fMP4DemuxerInputStream extends FilterInputStream implements Demuxer {

	public fMP4DemuxerInputStream(InputStream in) { super(in); }

	private int pos;
	private CircularBuffer buf = new CircularBuffer(8);
	private long skip_(long n) throws IOException {
		if (n <= 0) throw new IOException("Non-positive argument");
		long res = in.skip(n);
		if (res > 0) { pos += res; buf.clear(); }
		return res < n ? -1 : res;
	}
	private int read_() throws IOException { int res = in.read(); if (res != -1) { pos++; buf.put(res); } return res; }
	private byte byte_(int i) { return buf.get(i); }
	private int int_(int i) { return buf.getInt(i); }
	private int readInt_(int _skip) throws IOException {
		if (_skip < 0) throw new IOException("Negative argument");
		else if (_skip > 0) if (skip_(_skip) == -1) return -1;
		buf.ptr = 0;
		int res = in.read(buf.buf, 0, 4);
		if (res > 0)
			pos += res;
		return res < 4 ? -1 : res;
	}
	private int int_() { int res = buf.getInt(0); buf.clear(); return res; }
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return in.read(b, off, len);
	}

	@Override
	public int read(byte[] b) throws IOException { return read(b, 0, b.length); }
	
	private class Box {
		int start, size;
		public Box() { start = pos - 8; size = int_(0); }
		public long skip() throws IOException { return skip_(start + size - pos); }
	}
	private Box moov;
	private enum TrakType { unknown, video, audio }
	private class Trak extends Box { TrakType type; int id, width, height, timeScale; }
	private Trak trak;
	private Map<Integer,Trak> traks = new HashMap<Integer,Trak>();

	@Override
	public int read(MultiBuffer video, MultiBuffer audio) {
		while (true) {
			if (read_() == -1) return -1;
			switch (byte_(4)) {
			case (byte)'f': if (byte_(5) == (byte)'t' && byte_(6) == (byte)'y' && byte_(7) == (byte)'p') // ftyp
				if (new Box().skip() == -1) return -1;
			break;
			case (byte)'m': if (byte_(5) == (byte)'o' && byte_(6) == (byte)'o') { if (byte_(7) == (byte)'v') // moov
				moov = new Box();
			else if (byte_(7) == (byte)'f') { // moof
				
			} } else if (byte_(5) == (byte)'d' && byte_(6) == (byte)'h' && byte_(7) == (byte)'d') { // mdhd
				Box mdhd = new Box();
				if (read_(20) == -1) return -1;
				trak.timeScale = int_(0);
				if (mdhd.skip() == -1) return -1;
			} 
			break;
			case (byte)'t': if (byte_(5) == (byte)'r' && byte_(6) == (byte)'a' && byte_(7) == (byte)'k') // trak
				trak = new Trak();
			else if (byte_(5) == (byte)'k' && byte_(6) == (byte)'h' && byte_(7) == (byte)'d') { // tkhd
				Box tkhd = new Box();
				if (read_(20) == -1) return -1;
				trak.id = int_(0);
				if (read_(64) == -1) return -1;
				trak.width = int_(0) >> 16;
				trak.height = int_(4) >> 16;
				if (tkhd.skip() == -1) return -1;
			}
			break;
			}
			if (	(byte_(4) == (byte)'m' && ((byte_(5) == (byte)'d' && byte_(6) == (byte)'a' && byte_(7) == (byte)'t') || // mdat
					((byte_(5) == (byte)'o' && byte_(6) == (byte)'o') && (byte_(7) == (byte)'f' || // moof
					byte_(7) == (byte)'v')))) || // moov
					(byte_(4) == (byte)'f' && byte_(5) == (byte)'t' && byte_(6) == (byte)'y' && byte_(7) == (byte)'p')) { // ftyp
				int len_ = int_(0);
				if (len_ < 0)
					throw new IOException("Negative length: " + len_);
				if (len < len_)
					throw new IOException("Insufficient buffer length");
				buf.read(b, off, 8);
				int res = in.read(b, off + 8, len_ - 8);
				if (res > 0)
					pos += res;
				return res == -1 || res < len_ - 8 ? -1 : len_;
			}
		}
		return 0;
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
