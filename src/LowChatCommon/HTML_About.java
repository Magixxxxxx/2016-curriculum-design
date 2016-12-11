package LowChatCommon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;

/**
 * Created by Equim on 16-12-9.
 */
public class HTML_About implements ActionListener
{
    private JLabel aboutText;

    public HTML_About()
    {
        aboutText = new JLabel(
                "<html>" +
                    "<body>" +
                        "<p>" +
                            "<span style=\"font-family:微软雅黑;font-size:12px;font-weight:normal;\">" +
                                "陋室聊<br/>" +
                                "开发环境：JDK 8, Intellij IDEA 2016.1 Community<br/>" +
                                "代码总行数：约1,500行<br/>" +
                                "Copyright (c) 2016 Equim.  All rights reserved.<br/>" +
                                "===========================================<br/>" +
                            "<span style=\"font-family:微软雅黑;font-size:14px;font-weight:bold;\">" +
                                "一开始我确实很不情愿用Java。" +
                            "</span>" +
                            "<br/><br/>" +
                            "<blockquote><span style=\"font-family:微软雅黑;font-size:12px;font-style:italic;font-weight:normal;\">\"Had I not seen the sun, I could have borne the shade.\"\n" +
                                "<br/>-Emily Dickinson</span></blockquote>" +
                            "<br/>" +
                            "<span style=\"font-family:微软雅黑;font-size:12px;font-weight:normal;\">" +
                                "学过C#的我知道，Java的不足之处是在太多。没有委托类型，没有优雅的<br/>" +
                                "事件模型，没有get/set之类的语法糖，没有async/await也没有封装好<br/>" +
                                "的ARM异步编程模型，没有LINQ，没有真正的泛型，代码繁冗……<br/>" +
                                "尽管如此，作业还是要做的。写完UI逻辑以后，进入业务逻辑才写了两行<br/>" +
                                "我就写不下去了——没有我熟悉的BeginAccept()，只有一个accept()，<br/>" +
                                "得自己写线程封装。写就写吧，也没有委托类型，写个线程还得新建个对<br/>" +
                                "象。就连我在写这篇关于的时候，也发现Java不支持C#里的那种强制不转<br/>" +
                                "译标识……" +
                                "<br/><br/>" +
                                "然而，随着我进度的加快，我对Java（主要是Swing方面）的结构慢慢有<br/>" +
                                "了新的理解。Swing是个很灵活、很精简的UI库，相比WPF实在是“轻”<br/>" +
                                "了很多，Java这样做不是没有道理的。到了后面，我已经逐渐适应了Java<br/>" +
                                "的模式，感觉它的不足并没有对我的开发造成了太大困扰，相反，正是因<br/>" +
                                "为这个轻量级的UI库，做起来还挺方便的。" +
                                "<br/><br/>" +
                                    "<span style=\"font-family:微软雅黑;font-size:14px;font-weight:bold;\">" +
                                        "现在觉得Java酱也是蛮可爱的嘛……　　<s>C#酱 [囍] Java酱</s>" +
                                    "</span>" +
                                "<br/><br/>" +
                                "要不要去我的GitHub上看看呢？" +
                            "</span>" +
                        "</p>" +
                    "</body>" +
                "</html>");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try
        {
            if (JOptionPane.showConfirmDialog(null, aboutText, "关于", JOptionPane.YES_NO_OPTION) == 0)
                Desktop.getDesktop().browse(new URI("https://github.com/Equim-chan"));
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
    }
}