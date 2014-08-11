package img_applet;

import java.awt.Color;

import javax.swing.JApplet;


@SuppressWarnings("serial")
public class ImgApplet extends JApplet implements Runnable {

	@Override
	public void run() {
		getContentPane().setBackground(Color.WHITE);
		System.out.println(ffmpeg.ffmpeg.ffmpeg.getAbsolutePath());
	}

	@Override
	public void init() {
		super.init();
        //Execute a job on the event-dispatching thread:
        //creating this applet's GUI.
        try {
            javax.swing.SwingUtilities.invokeAndWait(this);
        } catch (Exception e) {
            System.err.println("Failed to create GUI");
            e.printStackTrace();
        }
	}

	@Override
	public void start() {
		// TODO Auto-generated method stub
		super.start();
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		super.stop();
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
		super.destroy();
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
