package img_applet;

import java.io.IOException;

public interface Demuxer {
	int read(ImgApplet.MultiBuffer video, ImgApplet.MultiBuffer audio) throws IOException;
}
