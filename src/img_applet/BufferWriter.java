package img_applet;

import java.io.IOException;

public interface BufferWriter {
	int read(ImgApplet.Buffer b) throws IOException;
}
