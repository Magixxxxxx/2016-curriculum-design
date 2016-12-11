import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import LowChatCommon.*;

/**
 * Created by Equim on 16-11-28.
 */
public class Client extends JFrame
{
    // 与生成、测试相关的常量
    private final boolean ShowSplashScreen = true;
    private final boolean ShowBG = true;
    private final boolean DismissOnlineListAfterDC = true;      //是否在断线后清空在线列表
    private final String serverIP = "127.0.0.1";        //这里是为了方便内外网之间切换
    private final int serverChatPort = 2333;
    private final int serverFilePort = 33284;

    // 色彩常量
    private final Color systemTextColor = new Color(0, 204, 51);
    private final Color activityTextColor = new Color(0, 163, 204);
    private final Color selfTextColor = new Color(0, 51, 102);       //自己发的消息的颜色
    private final Color monitorTextColor = new Color(102, 0, 102);
    private final Color generalTextColor = new Color(0, 0, 0);

    // 文档视图
    private SimpleAttributeSet attrSet = new SimpleAttributeSet();
    private Document publicDoc;

    // UI元素
    private JTextField inputTextBox = new JTextField();
    private JTextPane logArea = new JTextPane()
    {
        @Override
        public void paintComponent(Graphics g)         //设置背景图片，仅在打包成jar时有效
        {
            if (ShowBG)
            {
                try
                {
                    // 设置为透明，得才能绘图
                    this.setOpaque(false);

                    // 设为透明后，把背景涂抹成白色，不然就会变成light gray色
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, this.getWidth(), this.getHeight());

                    // 将BG画在右下角
                    BufferedImage bg = ImageIO.read(Server.class.getResource("/res/textBG.jpg"));
                    g.drawImage(bg, this.getWidth()-bg.getWidth(), this.getHeight()-bg.getHeight(), this);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
            super.paintComponent(g);
        }
    };
    private JScrollPane scrollPane = new JScrollPane(logArea);
    private JButton textButton = new JButton("发送");
    private JList<String> onlineList = new JList<>();
    private MyListCellRenderer notifyRenderer = new MyListCellRenderer();
    private DefaultListModel<String> onlineListModel = new DefaultListModel<>();

    // 菜单
    private MenuBar clientMenu = new MenuBar();
    private Menu m_file = new Menu("File");
    private MenuItem m_sendFile = new MenuItem("Send File to Selected User...");
    private Menu m_help = new Menu("Help");
    private MenuItem m_about = new MenuItem("About");


    // 网络与数据
    private Socket client;
    private boolean connecting = true;
    private Map<String, Document> nameDocMap = new HashMap<>();

    // 文件与流
    private PrintStream ps;
    private BufferedReader br;
    private File fileToSend = null;
    private String nickname;

    // 获取时间戳
    private String getTimeStamp()
    {
        return new SimpleDateFormat("[HH:mm:ss] ").format(new Date());
    }

    // 插入消息到文档视图
    private void insertMsg(Document doc, Color textColor, String msg)
    {
        try
        {
            // 设置颜色
            StyleConstants.setForeground(attrSet, textColor);
            // 插入字符串，带时间戳的
            doc.insertString(doc.getLength(), getTimeStamp() + msg +"\n", attrSet);
            // 调节滚动条到最底端
            if (logArea.getStyledDocument().equals(doc))
                logArea.select(doc.getLength(), doc.getLength());
        }
        catch (BadLocationException impossible)
        {
            impossible.printStackTrace();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //  UI初始化
    //////////////////////////////////////////////////////////////////////
    private void Init_UI()
    {
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setSize(415, 550);
        this.setTitle("陋室聊客户端");

        // 设置聊天记录的默认字体等
        publicDoc = logArea.getStyledDocument();
        StyleConstants.setFontSize(attrSet, 14);
//        StyleConstants.setFontFamily(attrSet, "微软雅黑");
        logArea.setEditable(false);
        this.add(scrollPane, BorderLayout.CENTER);

        // 设置左边的在线列表
        onlineListModel.addElement("公频");
        onlineList.setModel(onlineListModel);
        onlineList.setCellRenderer(notifyRenderer);
        onlineList.setBackground(Color.lightGray);
        onlineList.setSelectedIndex(0);
        this.add(onlineList, BorderLayout.WEST);

        // 设置下面的输入栏
        inputTextBox.setFont(inputTextBox.getFont().deriveFont(14F));
        textButton.setFont(new Font("微软雅黑", textButton.getFont().getStyle(), textButton.getFont().getSize()));
        JPanel bottom = new JPanel();
        bottom.setLayout(new BorderLayout());
        bottom.add(inputTextBox, BorderLayout.CENTER);
        bottom.add(textButton, BorderLayout.EAST);
        this.add(bottom, BorderLayout.SOUTH);

        // 菜单
        m_file.add(m_sendFile);
        m_sendFile.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (onlineList.getSelectedIndex() == 0)
                {
                    JOptionPane.showMessageDialog(null, "你没有选中一个客户端", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                JFileChooser jfc = new JFileChooser();
                jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
                jfc.setDialogTitle("请选择要发送的文件");
                if (jfc.showOpenDialog(null) == JFileChooser.CANCEL_OPTION)
                    return;
                fileToSend = jfc.getSelectedFile();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try
                        {
                            ps = new PrintStream(client.getOutputStream());
                            ps.println("fileFlag&" + nickname + "&" + onlineList.getSelectedValue() + "&" + fileToSend.getName() + "&" + new DecimalFormat(".00").format(fileToSend.length()/1024f/1024f));

                            Socket fileTargetSocket = new Socket(serverIP, serverFilePort);

                            DataOutputStream dout = new DataOutputStream(fileTargetSocket.getOutputStream());
                            FileInputStream fin = new FileInputStream(fileToSend);
                            byte[] buffer = new byte[1024];         // 1KB的buffer

                            dout.writeUTF("sender");          //发送标识以识别

                            FileProcessBar barFrame = new FileProcessBar(fileToSend.length()/1024f/1024f);

                            int length;
                            int sentLength = 0;
                            while((length = fin.read(buffer, 0, buffer.length))>0)
                            {
                                sentLength += length;
                                dout.write(buffer, 0, length);
                                barFrame.setProcess(sentLength/1024f/1024f);
                                dout.flush();
                            }

                            dout.close();
                            fin.close();
                            fileTargetSocket.close();
                        }
                        catch(Exception ex)
                        {
                            ex.printStackTrace();
                        }
                    }
                }).start();
            }
        });
        m_help.add(m_about);
        m_about.addActionListener(new HTML_About());
        clientMenu.add(m_file);
        clientMenu.add(m_help);
        this.setMenuBar(clientMenu);

        // 完成所有UI初始化
        this.setVisible(true);
    }

    //////////////////////////////////////////////////////////////////////
    //  构造器
    //////////////////////////////////////////////////////////////////////
    public Client()
    {
        try
        {
            // 显示SplashScreen
            if (ShowSplashScreen)
            {
                EqSplashScreen splash = new EqSplashScreen(this);
                splash.setVisible(true);
                //Thread.sleep(1500);
            }

            this.Init_UI();

            do
            {
                nickname = JOptionPane.showInputDialog("请输入昵称");
                if (nickname == null)
                {
                    JOptionPane.showMessageDialog(null, "用户名不能为空！", "", JOptionPane.ERROR_MESSAGE);
                }
                else if (nickname.equals("群主") || nickname.equals("公频"))
                {
                    JOptionPane.showMessageDialog(null, "嘻嘻、这些名字也是不行的！", "", JOptionPane.ERROR_MESSAGE);
                }
            }while(nickname == null || nickname.equals("群主") || nickname.equals("公频"));
            this.setTitle(String.format("陋室聊客户端 - %s", nickname));

            client = new Socket(serverIP, serverChatPort);

            insertMsg(publicDoc, systemTextColor, "已连接到服务器。(" + serverIP + ":" + String.valueOf(serverChatPort) + ")");

            ps = new PrintStream(client.getOutputStream());
            br = new BufferedReader(new InputStreamReader(client.getInputStream()));

            // 发送消息，可能是公频或私聊
            ActionListener sendText = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!inputTextBox.getText().isEmpty() && connecting)
                    {
                        try
                        {
                            String sendToWho = onlineList.getSelectedValue();
                            if (sendToWho.equals("公频"))
                            {
                                ps.println("public&" + nickname + "&" + inputTextBox.getText());

                                insertMsg(publicDoc, selfTextColor, nickname + ": " + inputTextBox.getText());
                            }
                            else
                            {
                                if (sendToWho.equals("群主"))
                                    ps.println("toMonitor&" + nickname + "&" + inputTextBox.getText());
                                else
                                    ps.println("query&" + nickname + "&" + sendToWho + "&" + inputTextBox.getText());

                                Document currentQueryDoc = logArea.getDocument();
                                insertMsg(currentQueryDoc, selfTextColor, nickname + ": " + inputTextBox.getText());
                            }

                            inputTextBox.setText(null);
                        }
                        catch(Exception ex)
                        {
                            ex.printStackTrace();
                        }
                    }
                }
            };
            inputTextBox.addActionListener(sendText);
            textButton.addActionListener(sendText);

            // 处理左边tab的单击响应，这块和服务端是一毛一样的
            onlineList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent e) {
                    if (!onlineList.getValueIsAdjusting())
                    {
                        String queryTo = onlineList.getSelectedValue();      //私聊给谁

                        notifyRenderer.remove(queryTo);
                        onlineList.updateUI();

                        if (queryTo.equals("公频"))
                        {
                            //mainDoc.insertString(mainDoc.getLength(), doc.getText(0, doc.getLength()), attrSet);      //不可取
                            logArea.setDocument(publicDoc);
                            logArea.select(publicDoc.getLength(), publicDoc.getLength());
                        }
                        else
                        {
                            Document targetDoc = nameDocMap.get(queryTo);
                            logArea.setDocument(targetDoc);
                            logArea.select(targetDoc.getLength(), targetDoc.getLength());
                        }
                    }
                }
            });

            ps.println("new&" + nickname);                    //发送新建用户请求

            // 由socket收消息
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (connecting)
                    {
                        try
                        {
                            String receivedMsg = br.readLine();

                            //解析字符串：
                            // monitor&msg                              | 公频群主消息
                            // public&username&msg                      | 公频消息
                            // query&username&msg                       | 私聊消息
                            // onlineList&username&...                  | 在线列表 (刚刚登陆时获取)
                            // online&username&                         | 上线信息
                            // offline&username                         | 下线信息
                            // kick&username&reason                     | 踢人公告
                            // fileReq&username&filename&size           | 来自某人的发文件请求
                            String[] analyzedMsgs = receivedMsg.split("&");
                            if (analyzedMsgs[0].equals("monitor"))
                            {
                                if (!onlineList.getSelectedValue().equals("公频"))      //给tab加高亮
                                {
                                    notifyRenderer.add("公频");
                                    onlineList.updateUI();
                                }

                                insertMsg(publicDoc, monitorTextColor, "群主: " + analyzedMsgs[1]);
                            }
                            else if (analyzedMsgs[0].equals("public") && !analyzedMsgs[1].equals(nickname))  //不然自己发的又被传回了
                            {
                                if (!onlineList.getSelectedValue().equals("公频"))      //给tab加高亮
                                {
                                    notifyRenderer.add("公频");
                                    onlineList.updateUI();
                                }

                                insertMsg(publicDoc, generalTextColor, analyzedMsgs[1] + ": " + analyzedMsgs[2]);
                            }
                            else if (analyzedMsgs[0].equals("query"))
                            {
                                if (!onlineList.getSelectedValue().equals(analyzedMsgs[1]))      //给tab加高亮
                                {
                                    notifyRenderer.add(analyzedMsgs[1]);
                                    onlineList.updateUI();
                                }

                                Document currentQueryDoc = nameDocMap.get(analyzedMsgs[1]);
                                insertMsg(currentQueryDoc, generalTextColor, analyzedMsgs[1] + ": " + analyzedMsgs[2]);
                            }
                            else if (analyzedMsgs[0].equals("onlineList"))
                            {
                                //刚刚上线会收到已经在线的人的列表
                                for(int i=1;i<=analyzedMsgs.length-1;i++)
                                {
                                    onlineListModel.addElement(analyzedMsgs[i]);
                                    nameDocMap.put(analyzedMsgs[i], new DefaultStyledDocument());
                                }
                                onlineList.updateUI();
                            }
                            else if (analyzedMsgs[0].equals("online") && !analyzedMsgs[1].equals(nickname))
                            {
                                //有人上线的情况
                                if (!onlineList.getSelectedValue().equals("公频"))      //给公频tab加高亮
                                {
                                    notifyRenderer.add("公频");
                                    onlineList.updateUI();
                                }

                                onlineListModel.addElement(analyzedMsgs[1]);
                                nameDocMap.put(analyzedMsgs[1], new DefaultStyledDocument());

                                insertMsg(publicDoc, activityTextColor, "【" + analyzedMsgs[1] + "】刚刚上线了");
                            }
                            else if (analyzedMsgs[0].equals("offline"))
                            {
                                //有人下线的情况
                                if (!onlineList.getSelectedValue().equals("公频"))      //给公频tab加高亮
                                {
                                    notifyRenderer.add("公频");
                                    onlineList.updateUI();
                                }

                                if (onlineList.getSelectedValue().equals(analyzedMsgs[1]))
                                {
                                    onlineList.setSelectedIndex(0);
                                    logArea.setDocument(publicDoc);
                                }
                                onlineListModel.removeElement(analyzedMsgs[1]);
                                nameDocMap.remove(analyzedMsgs[1]);

                                insertMsg(publicDoc, activityTextColor, "【" + analyzedMsgs[1] + "】刚刚下线了");
                            }
                            else if (analyzedMsgs[0].equals("kick"))
                            {
                                // 有人被踢的情况
                                if (!onlineList.getSelectedValue().equals("公频"))      //给公频tab加高亮
                                {
                                    notifyRenderer.add("公频");
                                    onlineList.updateUI();
                                }

                                insertMsg(publicDoc, activityTextColor,  "【" + analyzedMsgs[1] + "】刚刚被群主踢出，理由：" + analyzedMsgs[2]);

                                if (analyzedMsgs[1].equals(nickname))
                                {
                                    // 自己被踢…
                                    client.close();
                                    JOptionPane.showMessageDialog(null, "你已被群主踢出，理由：" + analyzedMsgs[2], "Kicked", JOptionPane.INFORMATION_MESSAGE);
                                    insertMsg(publicDoc, systemTextColor,  "与服务器断开连接");
                                    connecting = false;
                                }
                                else
                                {
                                    if (onlineList.getSelectedValue().equals(analyzedMsgs[1]))
                                    {
                                        onlineList.setSelectedIndex(0);
                                        logArea.setDocument(publicDoc);
                                    }
                                    onlineListModel.removeElement(analyzedMsgs[1]);
                                    nameDocMap.remove(analyzedMsgs[1]);
                                }
                            }
                            else if (analyzedMsgs[0].equals("fileReq"))
                            {
                                //来自某人的传文件请求
                                if (0 == JOptionPane.showConfirmDialog(null,
                                        "收到来自" + analyzedMsgs[1] + "的传文件请求，是否接收？\n\n" +
                                                "文件名：" + analyzedMsgs[2] + "\n" +
                                                "大小：" + analyzedMsgs[3] + " MB", "文件传输", JOptionPane.YES_NO_OPTION))
                                {
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                Socket fileSocket = new Socket(serverIP, serverFilePort);

                                                byte[] buffer = null;
                                                int length = 0;
                                                DataInputStream din = null;
                                                FileOutputStream fout = null;

                                                new DataOutputStream(fileSocket.getOutputStream()).writeUTF("receiver");

                                                din = new DataInputStream(fileSocket.getInputStream());

                                                JFileChooser jfc = new JFileChooser();
                                                jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
                                                jfc.setSelectedFile(new File(analyzedMsgs[2]));
                                                jfc.setDialogTitle("请选择接收的位置");
                                                jfc.showSaveDialog(null);
                                                File receivedFile = jfc.getSelectedFile();
                                                fout = new FileOutputStream(receivedFile);

                                                // 设置进度条
                                                FileProcessBar barFrame = new FileProcessBar(Float.parseFloat(analyzedMsgs[3]));

                                                buffer = new byte[1024];
                                                long receivedLength = 0;

                                                while (true) {
                                                    if (din != null) {
                                                        receivedLength += buffer.length;
                                                        length = din.read(buffer, 0, buffer.length);
                                                        barFrame.setProcess(receivedLength/1024f/1024f);
                                                    }
                                                    if (length == -1) {
                                                        break;
                                                    }
                                                    fout.write(buffer, 0, length);
                                                    fout.flush();
                                                }

                                                fout.close();
                                                din.close();
                                                fileSocket.close();

                                                Object[] selections = new Object[]{"打开文件", "打开所在文件夹", "关闭"};
                                                switch (JOptionPane.showOptionDialog(null,
                                                        "文件已接收到" + receivedFile.getParent(),
                                                        "传输完成",
                                                        JOptionPane.DEFAULT_OPTION,
                                                        JOptionPane.INFORMATION_MESSAGE,
                                                        null,
                                                        selections,
                                                        selections[2]))
                                                {
                                                    case 0: Desktop.getDesktop().open(receivedFile); break;
                                                    case 1: Desktop.getDesktop().open(new File(receivedFile.getParent())); break;
                                                }
                                            }
                                            catch (Exception ex)
                                            {
                                                ex.printStackTrace();
                                            }
                                        }
                                    }).start();
                                }
                            }
                        }
                        catch(SocketException disconnectionException)
                        {
                            // 这里必须说一下，以前这段在聊天记录窗加上这行的行为是放在移除左边的在线列表之后的。
                            // 然后发生了很奇怪的事，调试时发现在线列表移除后，并不能运行到聊天窗加字这块，一直找不到原因。
                            insertMsg(publicDoc, systemTextColor,  "与服务器断开连接");

                            if (!onlineList.getSelectedValue().equals("公频"))
                            {
                                onlineList.setSelectedIndex(0);
                                logArea.setDocument(publicDoc);
                            }
                            if (DismissOnlineListAfterDC)
                                onlineListModel.removeAllElements();
                            //onlineList.updateUI();

                            try { client.close(); } catch(IOException must){}

                            connecting = false;
                        }
                        catch(Exception e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();

        }
        catch (ConnectException ex)
        {
            insertMsg(publicDoc, systemTextColor,  "连接失败。\n" + ex.getMessage());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    //
    //  主方法
    //
    public static void main(String[] args)
    {
        new Client();
    }
}
// Enjoy <3