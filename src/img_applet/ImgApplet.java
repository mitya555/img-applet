package img_applet;

import img_applet.FFmpegProcess.TrackInfo;

import java.applet.Applet;
import java.awt.Button;
import java.awt.Color;
import java.awt.FlowLayout;
//import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Queue;
import java.util.regex.Pattern;

import javax.swing.JApplet;
import javax.swing.SwingUtilities;

import netscape.javascript.JSObject;

@SuppressWarnings("serial")
public class ImgApplet extends JApplet {

	private Button createButton(String label, ActionListener click, boolean active) {
		Button button = new Button(); 
		getContentPane().add(button);
		button.addActionListener(click);
		button.setLabel(label);
		button.setEnabled(active);
//		button.setVisible(active);
		return button;
	}

    private static boolean strEmpty(String str) { return str == null || str.length() == 0; }
	private static boolean isNo(String str) { return str == null || "No".equalsIgnoreCase(str) || "False".equalsIgnoreCase(str); }

    private boolean DEBUG = true;
    private void debug(String dbg) { if (DEBUG) System.out.println(dbg); }
    //private void debug(String dbg, String inf) { if (DEBUG) System.out.println(dbg); else System.out.println(inf); }

    private FFmpegProcess ffmpeg0;
    private Map<Integer,FFmpegProcess> ffmpegs = new HashMap<Integer,FFmpegProcess>();
    private Queue<Integer> id_pool = new ArrayDeque<Integer>();
    private int ffmpeg_count = 0;

	private Integer registerFFmpeg(FFmpegProcess ffmpeg, Object params) {
		Integer id = null;
		if (ffmpeg.init(params).HasInput()) {
			Integer id_from_pool = id_pool.poll();
			id = id_from_pool != null ? id_from_pool : ++ffmpeg_count;
			ffmpegs.put(id, ffmpeg);
			debug("Created FFmpeg ID: " + id);
		}
		return id;
	}
	
	public void removeFFmpegById(int id) {
		FFmpegProcess ffmpeg = ffmpegs.get(id);
		if (ffmpeg != null) {
			ffmpeg.stopPlayback();
			ffmpegs.remove(id);
			id_pool.add(id);
			debug("Removed FFmpeg ID: " + id);
		}
	}
	
	public FFmpegProcess createFFmpeg(final String jsCallback, String... params) {
		final HashMap<String,String> _params = new HashMap<String, String>();
		for (int i = 0; i < params.length - 1; i += 2) {
			_params.put(params[i], params[i + 1]);
		}
		final Applet _applet = this;
		return AccessController.doPrivileged(new PrivilegedAction<FFmpegProcess>() {
			@Override
			public FFmpegProcess run() {
				final FFmpegProcess ffmpeg = new FFmpegProcess();
				if (registerFFmpeg(ffmpeg, _params) != null && !strEmpty(jsCallback)) {
					ffmpeg.addObserver(new Observer() { @Override public void update(Observable o, Object arg) {
						boolean playing = FFmpegProcess.Event.START.equals(arg);
						JSObject.getWindow(_applet).call(jsCallback, new Object[] { ffmpeg, playing });
					} });
				}
				return ffmpeg;
			}
		}); /* doPrivileged() */
	}
	
	public int createFFmpegId(final String jsCallback, String... params) {
		final HashMap<String,String> _params = new HashMap<String, String>();
		for (int i = 0; i < params.length - 1; i += 2) {
			_params.put(params[i], params[i + 1]);
		}
		final Applet _applet = this;
//		return AccessController.doPrivileged(new PrivilegedAction<FFmpegProcess>() {
//			@Override
//			public FFmpegProcess run() {
				final FFmpegProcess ffmpeg = new FFmpegProcess();
				final Integer res = registerFFmpeg(ffmpeg, _params);
				if (res != null) {
					final int id = res;
					if (!strEmpty(jsCallback)) {
						ffmpeg.addObserver(new Observer() { @Override public void update(Observable o, Object arg) {
							boolean playing = FFmpegProcess.Event.START.equals(arg);
							JSObject.getWindow(_applet).call(jsCallback, new Object[] { id, playing });
						} });
					}
					return id;
				}
				return 0;
//			}
//		}); /* doPrivileged() */
	}

	@Override
	public void init() {
		
		super.init();
		
		DEBUG = !isNo(getParameter("debug"));
		
		ffmpeg0 = new FFmpegProcess();
		if (registerFFmpeg(ffmpeg0, this) != null) {
			final Button stopButton = createButton("Stop", new ActionListener() { @Override public void actionPerformed(ActionEvent e) {
				ffmpeg0.stopPlayback(); 
			} }, ffmpeg0.isPlaying());
			final Button playButton = createButton("Play", new ActionListener() { @Override public void actionPerformed(ActionEvent e) {
				ffmpeg0.play();
			} }, !ffmpeg0.isPlaying());
			ffmpeg0.addObserver(new Observer() { @Override public void update(Observable o, Object arg) {
				boolean playing = FFmpegProcess.Event.START.equals(arg);
				stopButton.setEnabled(playing); /*stopButton.setVisible(playing);*/ playButton.setEnabled(!playing); /*startButton.setVisible(!playing);*/
			} });
		}

        //Execute a job on the event-dispatching thread:
        //creating this applet's GUI.
//        try {
//            javax.swing.SwingUtilities.invokeAndWait(this);
//        } catch (Exception e) {
//            System.err.println("Failed to create GUI");
//            e.printStackTrace();
//        }

		// Remove as many temp files as possible
		try {
			for (File temp : JarLib.tmpdir.listFiles(new FilenameFilter() { @Override public boolean accept(File dir, String name) { return Pattern.matches("img_applet_\\d+\\.tmp", name); } }))
				temp.delete();
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				
				FlowLayout cont = new FlowLayout(FlowLayout.CENTER, 10, 10);
				getContentPane().setLayout(cont);
				
//				setButton(stopButton, "Stop", new ActionListener() { @Override public void actionPerformed(ActionEvent e) { stopPlayback(); } }, isPlaying());
//				setButton(playButton, "Play", new ActionListener() { @Override public void actionPerformed(ActionEvent e) { play(); } }, !isPlaying());
				
				getContentPane().setBackground(Color.WHITE);
//				System.out.println(FFmpeg.exe.getAbsolutePath());
//				console.append(FFmpeg.exe.getAbsolutePath() + "\n");
				
				debug("Initialized GUI");				
			}
		});
	}

	
	public void play(final int id) { AccessController.doPrivileged(new PrivilegedAction<Object>() { @Override public Object run() { ffmpegs.get(id).play(); return null; } }); }

	public void stopPlayback(int id) { ffmpegs.get(id).stopPlayback(); }
	
	public boolean isPlaying(int id) { return ffmpegs.get(id).isPlaying(); }

	public String getData(int id) throws IOException { byte[] res = ffmpegs.get(id).getData(); return res != null ? new String(res, "UTF-8") : null; }

	public String getDataURI(int id) throws IOException { return ffmpegs.get(id).getDataURI(); }
	
	public int getSN(int id) { return ffmpegs.get(id).getSN(); }
	
	int getQueueLength(int id) { return ffmpegs.get(id).getQueueLength(); }
	
	public boolean isStreaming(int id) { return ffmpegs.get(id).isStreaming(); }
	
	public String startHttpServer(int id) throws InterruptedException { return ffmpegs.get(id).startHttpServer(); } 

	public String getVideoDataURI(int id) throws IOException { return ffmpegs.get(id).getVideoDataURI(); }
	
	public int getVideoSN(int id) { return ffmpegs.get(id).getVideoSN(); }

	public int getVideoQueueLength(int id) { return ffmpegs.get(id).getVideoQueueLength(); }

	public long getVideoTimestamp(int id) { return ffmpegs.get(id).getVideoTimestamp(); }
	
	public TrackInfo getVideoTrackInfo(int id) { return ffmpegs.get(id).getVideoTrackInfo(); }

	public String getStderrData(int id) throws IOException { return ffmpegs.get(id).getStderrData(); }

	
	public boolean isPlaying() { return ffmpeg0.isPlaying(); }

	public String getData() throws IOException { byte[] res = ffmpeg0.getData(); return res != null ? new String(res, "UTF-8") : null; }

	public String getDataURI() throws IOException { return ffmpeg0.getDataURI(); }
	
	public int getSN() { return ffmpeg0.getSN(); }
	
	int getQueueLength() { return ffmpeg0.getQueueLength(); }
	
	public boolean isStreaming() { return ffmpeg0.isStreaming(); }
	
	public String startHttpServer() throws InterruptedException { return ffmpeg0.startHttpServer(); } 

	public String getVideoDataURI() throws IOException { return ffmpeg0.getVideoDataURI(); }
	
	public int getVideoSN() { return ffmpeg0.getVideoSN(); }

	public int getVideoQueueLength() { return ffmpeg0.getVideoQueueLength(); }

	public long getVideoTimestamp() { return ffmpeg0.getVideoTimestamp(); }
	
	public TrackInfo getVideoTrackInfo() { return ffmpeg0.getVideoTrackInfo(); }

	public String getStderrData() throws IOException { return ffmpeg0.getStderrData(); }
	
	
	public boolean isDebug() { return DEBUG; }

	@Override
	public void stop() {
		
		for (FFmpegProcess ffmpeg : ffmpegs.values())
			ffmpeg./*stopPlayback*/kill();
		
		super.stop();
	}

	@Override
	public void destroy() {
		
		for (FFmpegProcess ffmpeg : ffmpegs.values())
			ffmpeg.kill();
		
		super.destroy();
	}

//	public static void main(String[] args) {
//
//	}

}
