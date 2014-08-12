package img_applet;

import java.io.FilterInputStream;
import java.io.InputStream;

public class MjpegInputStream extends FilterInputStream {

	public MjpegInputStream(InputStream in) { super(in); }
	
	//public readFrame

}
