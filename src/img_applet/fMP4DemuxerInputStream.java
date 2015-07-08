package img_applet;

import img_applet.FFmpegProcess.Buffer;
import img_applet.FFmpegProcess.VideoBuffer;
import img_applet.FFmpegProcess.MultiBuffer;
import img_applet.FFmpegProcess.BufferList;
import img_applet.FFmpegProcess.BufferFactory;
import img_applet.FFmpegProcess.MediaDemuxer;
import img_applet.FFmpegProcess.ReaderToBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class fMP4DemuxerInputStream extends MediaDemuxer {

	public fMP4DemuxerInputStream(InputStream in,
			double growFactor, double shrinkThresholdFactor,
			MultiBuffer video, MultiBuffer audio,
			Gettable videoInfoCreatedCallback, Gettable audioInfoCreatedCallback,
			Runnable videoReadCallback, Runnable audioReadCallback,
			boolean debug) {
		super(in, video, audio,
				videoInfoCreatedCallback, audioInfoCreatedCallback,
				videoReadCallback, audioReadCallback,
				debug);
		this.growFactor = growFactor < 1.0 ? 1.0 : growFactor;
		this.shrinkThresholdFactor = shrinkThresholdFactor < 1.0 ? 1.5 : shrinkThresholdFactor;
	}

	protected double growFactor, shrinkThresholdFactor;

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
	private int read_(byte[] b, int nRead) throws IOException {
		int res = 0, nRes = 0;
		while (nRes < nRead && (res = in.read(b, nRes, nRead - nRes)) != -1)
			if (res > 0)
				nRes += res;
			else {
				res = in.read();
				if (res == -1)
					break;
				b[nRes++] = (byte)res;
			}
		if (nRes > 0)
			pos += nRes;
		dirty = true;
		return nRes < nRead ? -1 : nRes;
	}
	private int read_(int nSkip, int nRead) throws IOException {
		if (nSkip < 0) throw new IOException("Negative skip");
		else if (nSkip > 0) if (skip_(nSkip) == -1) return -1;
		buf.ptr = 0;
		return read_(buf.buf, nRead);
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
		long boxStart;
		int boxSize;
//		char[] boxName;
		public Box() {
			boxStart = pos - 8;
			boxSize = int_(0);
//			if (DEBUG) {
//				System.out.print("Begin ");
//				System.out.println(boxName = new char[] { (char)buf.get(4), (char)buf.get(5), (char)buf.get(6), (char)buf.get(7) });
//			}
		}
		public long skip() throws IOException {
//			if (DEBUG) {
//				System.out.print("End ");
//				System.out.println(boxName);
//			}
			return skip_(boxStart + boxSize - pos);
		}
		public long nextBoxStart() { return boxStart + boxSize; }
	}
	private enum TrakType { unknown, video, audio, other }
	class Trak extends Box {
		TrakType type = TrakType.unknown;
		int id, width, height, timeScale;
		long duration = 0L; // in 1.0/timeScale sec. 
		boolean done() { return type == TrakType.other || ((type == TrakType.video || type == TrakType.audio) && (traksByType.containsKey(type) || (id > 0 && timeScale > 0))); }
		long finish(Box moov) throws IOException {
			if ((type == TrakType.video || type == TrakType.audio) && !traksByType.containsKey(type)) {
				traksById.put(id, this);
				traksByType.put(type, this);
				switch (type) {
				case video: if (videoInfoCreatedCallback != null) videoInfoCreatedCallback.get(this); break;
				case audio: if (audioInfoCreatedCallback != null) audioInfoCreatedCallback.get(this); break;
				default: break;
				}
				if (traksByType.size() == 2)
					return moov.skip();
			}			
			return skip();
		}
	}
	private Map<Integer,Trak> traksById = new HashMap<Integer,Trak>();
	private Map<TrakType,Trak> traksByType = new HashMap<TrakType,Trak>();
	private class Traf extends Box implements ReaderToBuffer {
		int trakId, dfltSampleDuration, dfltSampleSize, dataOffset, duration, size;
		long baseDataOffset;
		Trak trak;
		@Override
		public int read(Buffer b) throws IOException {
			if (trak.type == TrakType.video)
				((VideoBuffer)b).timestamp = trak.duration;
			trak.duration += duration;
			if (skip_(baseDataOffset + dataOffset - pos) == -1) return -1;
			if (b.size < size || b.size > size * shrinkThresholdFactor) b.grow(size, growFactor);
			return read_(b.b, size);
		}
	}
	private class Moof extends Box {
		int sn;
		Traf[] trafs = new Traf[2];
	}

	@Override
	public int readFragment() throws IOException, InterruptedException {
		Moof moof = null;
		while (true) {
			if (readNext() == -1) return -1;
			if (check4box_('m', 'o', 'o', 'f')) { // moof
				if (read_moof(moof = new Moof()) == -1) return -1;
			} else if (check4box_('m', 'd', 'a', 't')) { // mdat
				Box mdat = new Box();
//				if (moof.trafs[0].duration > moof.trafs[0].trak.timeScale || moof.trafs[1].duration > moof.trafs[1].trak.timeScale) { // drop big fragments ( > 1 sec.)
//					if (mdat.skip() == -1) return -1;
//					continue;
//				}
				if (moof.trafs[0].trak.type == TrakType.video) {
					if (video.readToBuffer(moof.trafs[0]) == -1) return -1;
					if (videoReadCallback != null) videoReadCallback.run();
					if (moof.trafs[1] != null) {
						if (audio.readToBuffer(moof.trafs[1]) == -1) return -1;
						if (audioReadCallback != null) audioReadCallback.run();
					}
				} else {
					if (audio.readToBuffer(moof.trafs[0]) == -1) return -1;
					if (audioReadCallback != null) audioReadCallback.run();
					if (moof.trafs[1] != null) {
						if (video.readToBuffer(moof.trafs[1]) == -1) return -1;
						if (videoReadCallback != null) videoReadCallback.run();
					}
				}
				if (mdat.skip() == -1) return -1;
				return 0;
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
			if (moof.sn > 0 && moof.trafs[0] != null && moof.trafs[1] != null) {
				Arrays.sort(moof.trafs, new Comparator<Traf>() { @Override public int compare(Traf o1, Traf o2) {
					return (int) ((o1.baseDataOffset + o1.dataOffset) - (o2.baseDataOffset + o2.dataOffset)); } });
				if (moof.skip() == -1) return -1;
			}
			if (pos >= moof.nextBoxStart())
				return (int) (pos - moof.nextBoxStart());
		}
	}

	public int read_traf(Moof moof, Traf traf) throws IOException {
		while (true) {
			if (readNext() == -1) return -1;
			if (check4box_('t', 'f', 'h', 'd')) { // tfhd
				Box tfhd = new Box();
				if (read_(0, 8) == -1) return -1;
				int flags = int_(0), trakId = int_(4);
				if (traksById.containsKey(traf.trakId = trakId)) {
					traf.trak = traksById.get(trakId);
					moof.trafs[moof.trafs[0] == null ? 0 : 1] = traf;
				} else {
					if (traf.skip() == -1) return -1;
					return 0;
				}
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
			if (pos >= traf.nextBoxStart())
				return (int) (pos - traf.nextBoxStart());
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
			if (pos >= moov.nextBoxStart())
				return (int) (pos - moov.nextBoxStart());
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
			if (pos >= trak.nextBoxStart())
				return (int) (pos - trak.nextBoxStart());
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
			if (pos >= mdia.nextBoxStart())
				return (int) (pos - mdia.nextBoxStart());
		}
	}
	
	public static void main(String[] args) throws IOException, InterruptedException {
		File file = new File("C:\\Users\\dmitriy.mukhin\\AppData\\Local\\Temp\\img_applet\\output.mp4"); // "C:\\Documents and Settings\\Mitya\\Local Settings\\Temp\\img_applet\\output1.mp4");
		FileInputStream reader = new FileInputStream(file);
		MultiBuffer videoMultiBuffer = 
				new BufferList(new BufferFactory() { @Override public Buffer newBuffer() { return new FFmpegProcess.VideoBuffer(); } }, 20, 200, true, "Video"),
				audioMultiBuffer = new BufferList(20, 0, true, "Audio");
		fMP4DemuxerInputStream mp4reader = new fMP4DemuxerInputStream(reader, 1.0, 1.5, videoMultiBuffer, audioMultiBuffer, null, null, null, null, true);
		int res;
		while ((res = mp4reader.readFragment()) != -1)
			System.out.println("Fragment result: " + res);
		System.out.println("Fragment result: " + res);
		mp4reader.close();
	}

}
