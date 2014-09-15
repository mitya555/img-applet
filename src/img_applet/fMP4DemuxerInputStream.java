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
	private boolean dirty = true;
	private long skip_(long n) throws IOException {
		if (n < 0) throw new IOException("Negative skip");
		long res = in.skip(n);
		if (res > 0) pos += res;
		dirty = true;
		return res < n ? -1 : res;
	}
	private int read_() throws IOException {
		int res = in.read();
		if (res != -1) { pos++; buf.put(res); }
		return res;
	}
	private byte byte_(int i) { return buf.get(i); }
	private int int_(int i) { return buf.getInt(i); }
//	private boolean check_(int i, char ... cs) { return buf.check(i, cs); }
	private boolean check4_(int i, char c0, char c1, char c2, char c3) { return buf.check4(i, c0, c1, c2, c3); }
	private boolean check4box_(char c0, char c1, char c2, char c3) { return (dirty = buf.check4(4, c0, c1, c2, c3)); }
	private int read_(int nSkip, int nRead) throws IOException {
		if (nSkip < 0) throw new IOException("Negative skip");
		else if (nSkip > 0) if (skip_(nSkip) == -1) return -1;
		buf.ptr = 0;
		int res = in.read(buf.buf, 0, nRead);
		if (res > 0) pos += res;
		dirty = true;
		return res < nRead ? -1 : res;
	}
	private int readNext() throws IOException {
		if (dirty) {
			int res = read_(0, 8);
			dirty = false;
			return res;
		} else
			return read_();
	}
	
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
	private enum TrakType { unknown, video, audio, other }
	private class Trak extends Box {
		TrakType type = TrakType.unknown;
		int id, width, height, timeScale;
		boolean done() { return type == TrakType.other || ((type == TrakType.video || type == TrakType.audio) && (traksByType.containsKey(type) || (id > 0 && timeScale > 0))); }
		long finish(Box moov) throws IOException {
			if ((type == TrakType.video || type == TrakType.audio) && !traksByType.containsKey(type)) {
				traksById.put(id, this);
				traksByType.put(type, this);
				if (traksByType.size() == 2)
					return moov.skip();
			}			
			return skip();
		}
	}
	private Map<Integer,Trak> traksById = new HashMap<Integer,Trak>();
	private Map<TrakType,Trak> traksByType = new HashMap<TrakType,Trak>();

	@Override
	public int read(MultiBuffer video, MultiBuffer audio) {
		while (true) {
			if (readNext() == -1) return -1;
			if (check4box_('m', 'o', 'o', 'f')) { // moof
				
			} else if (check4box_('m', 'o', 'o', 'v')) { // moov
				if (read_moov(new Box()) == -1) return -1;
			} else { // skip all other boxes
				if (new Box().skip() == -1) return -1;
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

	public int read_moov(Box moov) throws IOException {
		while (true) {
			if (readNext() == -1) return -1;
			if (check4box_('t', 'r', 'a', 'k')) { // trak
				if (read_trak(moov, new Trak()) == -1) return -1;
			} else { // skip all other boxes
				if (new Box().skip() == -1) return -1;
			}
			if (pos >= moov.start + moov.size)
				return pos - (moov.start + moov.size);
		}
	}

	public int read_trak(Box moov, Trak trak) throws IOException {
		while (true) {
			if (readNext() == -1) return -1;
			if (check4box_('m', 'd', 'i', 'a')) { // mdia
				if (read_mdia(moov, trak, new Box()) == -1) return -1;
			} else if (check4box_('t', 'k', 'h', 'd')) { // tkhd
				Box tkhd = new Box();
				if (read_(12, 4) == -1) return -1;
				trak.id = int_(0);
				if (read_(60, 8) == -1) return -1;
				trak.width = int_(0) >> 16;
				trak.height = int_(4) >> 16;
				if (trak.done()) { if (trak.finish(moov) == -1) return -1; }
				else if (tkhd.skip() == -1) return -1;
			} else { // skip all other boxes
				if (new Box().skip() == -1) return -1;
			}
			if (pos >= trak.start + trak.size)
				return pos - (trak.start + trak.size);
		}
	}

	public int read_mdia(Box moov, Trak trak, Box mdia) throws IOException {
		while (true) {
			if (readNext() == -1) return -1;
			if (check4box_('m', 'd', 'h', 'd')) { // mdhd
				Box mdhd = new Box();
				if (read_(12, 4) == -1) return -1;
				trak.timeScale = int_(0);
				if (trak.done()) { if (trak.finish(moov) == -1) return -1; }
				else if (mdhd.skip() == -1) return -1;
			} else if (check4box_('h', 'd', 'l', 'r')) { // hdlr
				Box hdlr = new Box();
				if (read_(8, 4) == -1) return -1;
				trak.type = check4_(0, 'v', 'i', 'd', 'e') ? TrakType.video : check4_(0, 's', 'o', 'u', 'n') ? TrakType.audio : TrakType.other;
				if (trak.done()) { if (trak.finish(moov) == -1) return -1; }
				else if (hdlr.skip() == -1) return -1;
			} else { // skip all other boxes
				if (new Box().skip() == -1) return -1;
			}
			if (pos >= mdia.start + mdia.size)
				return pos - (mdia.start + mdia.size);
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
