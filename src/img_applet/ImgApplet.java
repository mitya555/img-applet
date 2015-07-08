package img_applet;

import img_applet.FFmpegProcess.TrackInfo;

import java.awt.Button;
import java.awt.Color;
import java.awt.FlowLayout;
//import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Observable;
import java.util.Observer;
import java.util.regex.Pattern;

import javax.swing.JApplet;
import javax.swing.SwingUtilities;

@SuppressWarnings("serial")
public class ImgApplet extends JApplet implements Runnable {

	private void setButton(Button button, String label, ActionListener click, boolean active) {
		getContentPane().add(button);
		button.addActionListener(click);
		button.setLabel(label);
		button.setEnabled(active);
//		button.setVisible(active);
	}
	
	private Button playButton = new Button(), stopButton = new Button();
	
	private void setUIForPlaying(boolean playing) { stopButton.setEnabled(playing); /*stopButton.setVisible(playing);*/ playButton.setEnabled(!playing); /*startButton.setVisible(!playing);*/ }

	@Override
	public void run() {
		
		FlowLayout cont = new FlowLayout(FlowLayout.CENTER, 10, 10);
		getContentPane().setLayout(cont);
		
		setButton(stopButton, "Stop", new ActionListener() { @Override public void actionPerformed(ActionEvent e) { stopPlayback(); } }, isPlaying());
		setButton(playButton, "Play", new ActionListener() { @Override public void actionPerformed(ActionEvent e) { play(); } }, !isPlaying());
		
		getContentPane().setBackground(Color.WHITE);
//		System.out.println(FFmpeg.exe.getAbsolutePath());
//		console.append(FFmpeg.exe.getAbsolutePath() + "\n");
	}

	private static boolean isNo(String str) { return str == null || "No".equalsIgnoreCase(str) || "False".equalsIgnoreCase(str); }

    private boolean DEBUG = true;
    
    private FFmpegProcess ffmpegProcess = new FFmpegProcess();

	@Override
	public void init() {
		
		super.init();
		
		DEBUG = !isNo(getParameter("debug"));
		
		ffmpegProcess.init(this).addObserver(new Observer() { @Override public void update(Observable o, Object arg) {
			setUIForPlaying(FFmpegProcess.Event.START.equals(arg)); }});
		
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
		
		SwingUtilities.invokeLater(this);
	}

	protected void play() {
		ffmpegProcess.play();
	}

	public boolean isPlaying() { 
		return ffmpegProcess.isPlaying();
	}

	protected void stopPlayback() {
		ffmpegProcess.stopPlayback();
	}

	private void killProcess() {
		ffmpegProcess.kill();
	}

	public String getDataURI() throws IOException { return ffmpegProcess.getDataURI(); }
	
	public int getSN() { return ffmpegProcess.getSN(); }
	
	int getQueueLength() { return ffmpegProcess.getQueueLength(); }
	
	public boolean isStreaming() { return ffmpegProcess.isStreaming(); }
	
	public String startHttpServer() throws InterruptedException { return ffmpegProcess.startHttpServer(); } 

	public String getVideoDataURI() throws IOException { return ffmpegProcess.getVideoDataURI(); }
	
	public int getVideoSN() { return ffmpegProcess.getVideoSN(); }

	public int getVideoQueueLength() { return ffmpegProcess.getVideoQueueLength(); }

	public long getVideoTimestamp() { return ffmpegProcess.getVideoTimestamp(); }
	
	public TrackInfo getVideoTrackInfo() { return ffmpegProcess.getVideoTrackInfo(); }
	
	public boolean isDebug() { return DEBUG; }

	@Override
	public void stop() {
		
		stopPlayback();
		
		super.stop();
	}

	@Override
	public void destroy() {
		
		killProcess();
		
		super.destroy();
	}

//	public static void main(String[] args) {
//
//	}

}
