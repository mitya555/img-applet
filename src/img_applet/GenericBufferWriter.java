package img_applet;

import img_applet.FFmpegProcess.Buffer;
import img_applet.FFmpegProcess.MediaReader;

import java.io.IOException;
import java.io.InputStream;

public class GenericBufferWriter extends MediaReader {

	protected GenericBufferWriter(InputStream in, int bufferSize) {
		super(in);
		this.bufferSize = bufferSize > 0 ? bufferSize : 100000;
	}

	protected int bufferSize;
	
	@Override
	public int read(Buffer b) throws IOException {
		if (b.size < bufferSize)
			b.b = new byte[b.size = bufferSize];
		return read(b.b);
	}
}
