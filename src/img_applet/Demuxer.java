package img_applet;

public interface Demuxer {
	int read(ImgApplet.MultiBuffer video, ImgApplet.MultiBuffer audio);
}
