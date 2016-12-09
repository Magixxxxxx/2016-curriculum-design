import javax.imageio.ImageIO;
import javax.swing.*;
import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;

/**
 * Created by Equim on 16-12-10.
 */
// Java wrap of EqSplashScreen <3
public class EqSplashScreen extends JDialog
{
    // Java的Image为什么要这么搞……抽象类……
    private BufferedImage image;
    private JFrame main;
    private AudioClip clip;

    public EqSplashScreen(JFrame main)
    {
        try
        {
            this.main = main;           //好像没什么必要
            this.image = ImageIO.read(EqSplashScreen.class.getResource("/res/BG.bmp"));
            this.setAlwaysOnTop(true);
            this.setUndecorated(true);
            this.setSize(new Dimension(image.getWidth(), image.getHeight()));
            this.setLocationRelativeTo(null);
            this.getContentPane().setLayout(null);

            this.main.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(ComponentEvent e) {
                    EqSplashScreen.this.dispose();
                }
            });

            clip = Applet.newAudioClip(EqSplashScreen.class.getResource("/res/welcome.wav"));
            clip.play();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void paint(Graphics g) {
        if(image != null)
        {
            g.drawImage(image, 0, 0, this);
        }
    }
}
