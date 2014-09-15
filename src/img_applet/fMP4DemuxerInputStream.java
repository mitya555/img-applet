package img_applet;

//import java.io.File;
//import java.io.FileInputStream;
import img_applet.ImgApplet.MultiBuffer;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class fMP4DemuxerInputStream extends FilterInputStream implements Demuxer {

	public fMP4DemuxerInputStream(InputStream in) { super(in); }

	private long pos;
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
//	private byte byte_(int i) { return buf.get(i); }
	private int int_(int i) { return buf.getInt(i); }
	private long long_() { return buf.getLong(0); }
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
	
	private class Box {
		long start;
		int size;
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
	private class Traf extends Box { int trakId, dfltSampleDuration, dfltSampleSize, dataOffset, duration, size; long baseDataOffset; }
	private class Moof extends Box { int sn; Traf traf0, traf1; }

	@Override
	public int read(MultiBuffer video, MultiBuffer audio) throws IOException {
		while (true) {
			if (readNext() == -1) return -1;
			if (check4box_('m', 'o', 'o', 'f')) { // moof
				if (read_moof(new Moof()) == -1) return -1;
			} else if (check4box_('m', 'o', 'o', 'v')) { // moov
				if (read_moov(new Box()) == -1) return -1;
			} else { // skip all other boxes
				if (new Box().skip() == -1) return -1;
			}
		}
	}

	public int read_moof(Moof moof) throws IOException {
		while (true) {
			if (readNext() == -1) return -1;
			if (check4box_('m', 'f', 'h', 'd')) { // mfhd
				Box mfhd = new Box();
				if (read_(4, 4) == -1) return -1;
				moof.sn = int_(0);
				if (mfhd.skip() == -1) return -1;
			} else if (check4box_('t', 'r', 'a', 'f')) { // traf
				if (read_traf(moof, new Traf()) == -1) return -1;
			} else { // skip all other boxes
				if (new Box().skip() == -1) return -1;
			}
			if (pos >= moof.start + moof.size)
				return (int) (pos - (moof.start + moof.size));
		}
	}

	public int read_traf(Moof moof, Traf traf) throws IOException {
		while (true) {
			if (readNext() == -1) return -1;
			if (check4box_('t', 'f', 'h', 'd')) { // tfhd
				Box tfhd = new Box();
				if (read_(0, 8) == -1) return -1;
				int flags = int_(0);
				traf.trakId = int_(4);
				if ((flags & 0x000001) != 0) {
					if (read_(0, 8) == -1) return -1;
					traf.baseDataOffset = long_();
				}
				if ((flags & 0x000002) != 0) {
					if (skip_(4) == -1) return -1;
				}
				if ((flags & 0x000008) != 0) {
					if (read_(0, 4) == -1) return -1;
					traf.dfltSampleDuration = int_(0);
				}
				if ((flags & 0x000010) != 0) {
					if (read_(0, 4) == -1) return -1;
					traf.dfltSampleSize = int_(0);
				}
				if (tfhd.skip() == -1) return -1;
			} else if (check4box_('t', 'r', 'u', 'n')) { // trun
				Box trun = new Box();
				if (read_(0, 8) == -1) return -1;
				int flags = int_(0), sampleCount = int_(4);
				if ((flags & 0x000001) != 0) {
					if (read_(0, 4) == -1) return -1;
					traf.dataOffset = int_(0);
				}
				if ((flags & 0x000004) != 0) {
					if (skip_(4) == -1) return -1;
				}
				if ((flags & 0x000100) == 0)
					traf.duration = sampleCount * traf.dfltSampleDuration;
				if ((flags & 0x000200) == 0)
					traf.size = sampleCount * traf.dfltSampleSize;
				if ((flags & (0x000100 | 0x000200)) != 0)
					for (int i = 0; i < sampleCount; i++) {
						if ((flags & 0x000100) != 0) {
							if (read_(0, 4) == -1) return -1;
							traf.duration += int_(0);
						}
						if ((flags & 0x000200) != 0) {
							if (read_(0, 4) == -1) return -1;
							traf.size += int_(0);
						}
						if ((flags & 0x000400) != 0) {
							if (skip_(4) == -1) return -1;
						}
						if ((flags & 0x000800) != 0) {
							if (skip_(4) == -1) return -1;
						}
					}
				if (trun.skip() == -1) return -1;
			} else { // skip all other boxes
				if (new Box().skip() == -1) return -1;
			}
			if (pos >= traf.start + traf.size)
				return (int) (pos - (traf.start + traf.size));
		}
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
				return (int) (pos - (moov.start + moov.size));
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
				return (int) (pos - (trak.start + trak.size));
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
				return (int) (pos - (mdia.start + mdia.size));
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
