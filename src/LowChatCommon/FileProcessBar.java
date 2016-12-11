package LowChatCommon;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;

/**
 * Created by Equim on 16-12-10.
 */

// 文件传输的进度窗口
public class FileProcessBar extends JFrame
{
    private JProgressBar bar = new JProgressBar();
    private JLabel detail = new JLabel("", JLabel.CENTER);
    private DecimalFormat myFormat = new DecimalFormat(".00");
    private float fileSizeMB;

    public void setProcess(float doneSizeMB)
    {
        if (doneSizeMB < fileSizeMB)
        {
            bar.setValue((int)(doneSizeMB/fileSizeMB*100));
            detail.setText(myFormat.format(doneSizeMB) + " MB/" + myFormat.format(fileSizeMB) + " MB");
        }
        else
            this.dispose();
    }

    public FileProcessBar(float fileSizeMB)
    {
        this.setTitle("传输中");
        this.setSize(500, 70);
        this.setResizable(false);
        this.setLocationRelativeTo(null);
        this.setUndecorated(true);
        this.getRootPane().setWindowDecorationStyle(JRootPane.PLAIN_DIALOG);

        this.fileSizeMB = fileSizeMB;
        detail.setText("0.00 MB/" + myFormat.format(this.fileSizeMB) + " MB");
        bar.setMinimum(0);
        bar.setMaximum(100);
        bar.setValue(0);
        bar.setStringPainted(true);
        bar.setPreferredSize(new Dimension(500, 50));
        this.add(bar, BorderLayout.CENTER);
        this.add(detail, BorderLayout.SOUTH);

        this.setVisible(true);
    }
}
