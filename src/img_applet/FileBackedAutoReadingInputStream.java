package img_applet;

import java.io.FilterInputStream;
import java.io.InputStream;

public class FileBackedAutoReadingInputStream extends FilterInputStream {

	protected FileBackedAutoReadingInputStream(InputStream in) {
		super(in);
	}
	
	static private class _File {
		
	}

	static private class _List {
		static private class _ListItem { public volatile _ListItem next; public _File file; public _ListItem() { this.file = new _File(); } }
		private volatile _ListItem read, write;
		public void addFile() { _ListItem _li = new _ListItem(); if (read == null) write.next = read = write = _li; else { _li.next = write.next; write.next = _li; } }
	}


}
