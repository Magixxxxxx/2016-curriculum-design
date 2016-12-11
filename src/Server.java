import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import LowChatCommon.*;         //这是后来才整在一起的包，其实一开始就该这样的，到后面才加进去的话，git的提交历史就没用了，当是新文件

/**
 * Created by Equim on 16-11-27.
 * 向Java势力低头 vol.1
 */
public class Server extends JFrame
{
    // 与生成、测试相关的常量
    private final boolean ShowSplashScreen = true;     //是否显示SplashScreen
    private final boolean ShowBG = true;                //是否显示背景图片

    // 色彩常量
    private final Color systemTextColor = new Color(0, 204, 51);          //系统信息的颜色，本来想用enum，结果又感觉不合适
    private final Color activityTextColor = new Color(0, 163, 204);       //上下线信息的颜色
    private final Color monitorTextColor = new Color(102, 0, 102);        //群主发的消息的颜色
    private final Color generalTextColor = new Color(0, 0, 0);            //一般消息的颜色

    // 文档视图
    private SimpleAttributeSet attrSet = new SimpleAttributeSet();  //文字属性
    private Document publicDoc;                        //会在Init_UI中初始化

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
    private MenuBar serverMenu = new MenuBar();
    private Menu m_file = new Menu("File");
    private MenuItem m_sendFile = new MenuItem("Send File to Selected User...");
    private Menu m_manage = new Menu("Manage");
    private MenuItem m_kick = new MenuItem("Kick Selected User");
    private Menu m_help = new Menu("Help");
    private MenuItem m_about = new MenuItem("About");

    // 网络与数据
    private ServerSocket server;
    private ServerSocket fileServerSocket;
    private LinkedList<Socket> clientList = new LinkedList<>();
    private Map<String, Socket> nameSocketMap = new HashMap<>();
    private Map<String, Document> nameDocMap = new HashMap<>();

    // 文件与流
    private PrintStream ps;
    private File fileToSend = null;
    private String personToKick = "";     //这个变量放在这，是因为踢人后会立即响应offline，信息会杂糅在一起，导致无法解析或信息错乱

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
        // 窗体
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setSize(415, 550);
        this.setTitle("陋室聊服务端");

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
                            // 靠消息让指定客户端建立33284端口的连接
                            // 如果是，则在客户端让其选择接收路径，建立各种stream。
                            // 33284只接受一个请求(线程里不用while(true))
                            ps = new PrintStream(nameSocketMap.get(onlineList.getSelectedValue()).getOutputStream());
                            ps.println("fileReq&" + "群主&" + fileToSend.getName() + "&" + new DecimalFormat(".00").format(fileToSend.length()/1024f/1024f));

                            Socket fileTargetSocket = fileServerSocket.accept();

                            DataOutputStream dout = new DataOutputStream(fileTargetSocket.getOutputStream());
                            FileInputStream fin = new FileInputStream(fileToSend);
                            byte[] buffer = new byte[1024];         // 1KB的buffer

                            FileProcessBar barFrame = new FileProcessBar(fileToSend.length()/1024f/1024f);

                            int length = 0;
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
        m_manage.add(m_kick);
        m_kick.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                personToKick = onlineList.getSelectedValue();
                if (personToKick.equals("公频"))
                {
                    JOptionPane.showMessageDialog(null, "你没有选中一个客户端", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                String reason = JOptionPane.showInputDialog(null, "您将要踢掉" + personToKick + "\n请输入理由：");
                try
                {
                    // 先发信，再踢
                    for(Socket clientIter : clientList)
                    {
                        ps = new PrintStream(clientIter.getOutputStream());
                        ps.println("kick&" + personToKick + "&" + reason);
                    }

                    nameSocketMap.get(personToKick).close();
                    if (onlineList.getSelectedValue().equals(personToKick))
                    {
                        onlineList.setSelectedIndex(0);
                        logArea.setDocument(publicDoc);
                    }
                    onlineListModel.removeElement(personToKick);
                    nameDocMap.remove(personToKick);

                    insertMsg(publicDoc, activityTextColor, "【" + personToKick + "】刚刚被群主踢出，理由：" + reason);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        });
        m_help.add(m_about);
        m_about.addActionListener(new HTML_About());
        serverMenu.add(m_file);
        serverMenu.add(m_manage);
        serverMenu.add(m_help);
        this.setMenuBar(serverMenu);

        // 完成所有UI初始化
        this.setVisible(true);
    }

    //////////////////////////////////////////////////////////////////////
    //  构造器
    //////////////////////////////////////////////////////////////////////
    public Server()
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

            server = new ServerSocket(2333);
            fileServerSocket = new ServerSocket(33284);
//            logArea.setForeground(systemMsg);
//            logArea.append(getTimeStamp() + "服务器已启动。 (127.0.0.1:2333)\n");
            insertMsg(publicDoc, systemTextColor, "服务器已启动。(127.0.0.1:2333)");

            // 发送群主的消息，可能是公频也可能是私聊
            ActionListener sendText = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!inputTextBox.getText().isEmpty())        //其实这里会触发bug，不知道为什么，有几个socket就会发几次  //已解决
                    {
                        try
                        {
                            String sendToWho = onlineList.getSelectedValue();
                            if (sendToWho.equals("公频"))
                            {
                                insertMsg(publicDoc, monitorTextColor, "群主: " + inputTextBox.getText());

                                //遍历socket list群发
                                for(Socket clientIter : clientList)
                                {
                                    ps = new PrintStream(clientIter.getOutputStream());
                                    ps.println("monitor&" + inputTextBox.getText());
                                }
                            }
                            else
                            {
                                // 在私聊doc更新
                                Document currentQueryDoc = logArea.getDocument();
                                insertMsg(currentQueryDoc, monitorTextColor, "群主: " + inputTextBox.getText());

                                // 发送给对方
                                ps = new PrintStream(nameSocketMap.get(sendToWho).getOutputStream());
                                ps.println("query&群主&" + inputTextBox.getText());
                            }

                            inputTextBox.setText("");         //上一段可能有延迟，所以这里本来可以小小地优化一下的
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

            // 处理左边tab的单击响应
            onlineList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent e) {
                    if (!onlineList.getValueIsAdjusting())
                    {
                        // 私聊给谁
                        String queryTo = onlineList.getSelectedValue();

                        // 撤销高亮
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

            // 该线程用于监听新连接
            new Thread(new Runnable() {                               //Java真麻烦，好想用BeginAccept
                @Override
                public void run() {
                    try
                    {
                        while (true)
                        {
                            Socket newClient = server.accept();              //好想用BeginAccept()
                            clientList.add(newClient);
                            //logArea.append("有人上线");

                            // 该线程用于响应某个socket
                            new Thread(new Runnable() {
                                @Override
                                public void run() {                         //接收并解析
                                    try
                                    {
                                        while (true)
                                        {
                                            String receivedMsg = new BufferedReader(new InputStreamReader(newClient.getInputStream())).readLine();

                                            //解析字符串：
                                            // new&username                                           | 添加新用户名
                                            // public&username&msg                                    | 公频发言
                                            // toMonitor&username&msg                                 | 给群主的发言
                                            // query&username&toUsername&msg                          | 私聊发言
                                            // fileFlag&username&toUsername&fileName&fileSize         | 传文件请求
                                            //不敢另开方法来解决，是怕遇到死锁
                                            String[] analyzedMsgs = receivedMsg.split("&");
                                            if (analyzedMsgs[0].equals("new"))
                                            {
                                                // 高亮公频tab
                                                if (!onlineList.getSelectedValue().equals("公频"))
                                                {
                                                    notifyRenderer.add("公频");
                                                    onlineList.updateUI();
                                                }

                                                // 为新客户端发送当前在线列表
                                                ps = new PrintStream(newClient.getOutputStream());
                                                ps.print("onlineList&群主&");
                                                for(HashMap.Entry<String, Socket> nicknameIter : nameSocketMap.entrySet())
                                                {
                                                    ps.print(nicknameIter.getKey() + "&");
                                                }
                                                ps.println();

                                                //建立nickname到socket的map
                                                nameSocketMap.put(analyzedMsgs[1], newClient);
                                                onlineListModel.addElement(analyzedMsgs[1]);

                                                // 添加私聊用的doc，并和name映射
                                                nameDocMap.put(analyzedMsgs[1], new DefaultStyledDocument());

                                                // 为其他客户端发送这个新人上线的消息
                                                for(Socket clientIter : clientList)
                                                {
                                                    ps = new PrintStream(clientIter.getOutputStream());
                                                    ps.println("online&" + analyzedMsgs[1]);
                                                }

                                                // 公频刷新
                                                insertMsg(publicDoc, activityTextColor, "【" + analyzedMsgs[1] + "】刚刚上线了");
                                            }
                                            else if (analyzedMsgs[0].equals("public"))
                                            {
                                                if (!onlineList.getSelectedValue().equals("公频"))      //给tab加高亮
                                                {
                                                    notifyRenderer.add("公频");
                                                    onlineList.updateUI();
                                                }

                                                insertMsg(publicDoc, generalTextColor, analyzedMsgs[1] + ": " + analyzedMsgs[2]);

                                                //遍历socket list群发
                                                for(Socket clientIter : clientList)
                                                {
                                                    ps = new PrintStream(clientIter.getOutputStream());
                                                    ps.println("public&" + analyzedMsgs[1] + "&" + analyzedMsgs[2]);
                                                }
                                            }
                                            else if (analyzedMsgs[0].equals("fileFlag"))
                                            {
                                                // 客户端到客户端的情况
                                                // 先由服务器转发请求到接收方
                                                // 确定要收后，开启33284，只接收两个socket，注意，两个客户端必须发送标识，以区分发送和接收
                                                // 将发送方的stream直接转发给接收方

                                                // 客户端发给群主的情况
                                                if (analyzedMsgs[2].equals("群主"))
                                                {
                                                    if (0 == JOptionPane.showConfirmDialog(null,
                                                            "收到来自" + analyzedMsgs[1] + "的传文件请求，是否接收？\n\n" +
                                                                    "文件名：" + analyzedMsgs[3] + "\n" +
                                                                    "大小：" + analyzedMsgs[4] + " MB", "文件传输", JOptionPane.YES_NO_OPTION))
                                                    {
                                                        new Thread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                try {
                                                                    Socket fileSocket = fileServerSocket.accept();

                                                                    byte[] buffer = null;
                                                                    int length = 0;
                                                                    DataInputStream din = null;
                                                                    FileOutputStream fout = null;

                                                                    din = new DataInputStream(fileSocket.getInputStream());

                                                                    JFileChooser jfc = new JFileChooser();
                                                                    jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
                                                                    jfc.setSelectedFile(new File(analyzedMsgs[3]));
                                                                    jfc.setDialogTitle("请选择接收的位置");
                                                                    jfc.showSaveDialog(null);
                                                                    File receivedFile = jfc.getSelectedFile();
                                                                    fout = new FileOutputStream(receivedFile);

                                                                    // 设置进度条
                                                                    FileProcessBar barFrame = new FileProcessBar(Float.parseFloat(analyzedMsgs[4]));

                                                                    buffer = new byte[1024];
                                                                    long receivedLength = 0;

                                                                    while (true)
                                                                    {
                                                                        receivedLength += buffer.length;
                                                                        length = din.read(buffer, 0, buffer.length);
                                                                        barFrame.setProcess(receivedLength/1024f/1024f);
                                                                        if (length == -1) break;
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
                                                else    // 客户端发给客户端的情况
                                                {
                                                    ps = new PrintStream(nameSocketMap.get(analyzedMsgs[2]).getOutputStream());
                                                    ps.println("fileReq&" + analyzedMsgs[1] + "&" + analyzedMsgs[3] + "&" + analyzedMsgs[4]);

                                                    new Thread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            try
                                                            {
                                                                // 建立标识·Socket映射，以区分发送者和接收者
                                                                HashMap<String, Socket> SR_map = new HashMap<String, Socket>();
                                                                Socket fileSocket_0 = fileServerSocket.accept();
                                                                Socket fileSocket_1 = fileServerSocket.accept();

                                                                SR_map.put(new DataInputStream(fileSocket_0.getInputStream()).readUTF(), fileSocket_0);
                                                                SR_map.put(new DataInputStream(fileSocket_1.getInputStream()).readUTF(), fileSocket_1);

                                                                DataInputStream din = new DataInputStream(SR_map.get("sender").getInputStream());
                                                                DataOutputStream dout = new DataOutputStream(SR_map.get("receiver").getOutputStream());

                                                                // 只有我一个人觉得这里非常炫酷吗((
                                                                int length;
                                                                byte[] buffer = new byte[1024];
                                                                while (true)
                                                                {
                                                                    length = din.read(buffer, 0, buffer.length);
                                                                    if (length == -1) break;
                                                                    dout.write(buffer, 0, buffer.length);
                                                                    dout.flush();
                                                                }

                                                                dout.close();
                                                                din.close();
                                                                fileSocket_0.close();
                                                                fileSocket_1.close();
                                                            }
                                                            catch(Exception e)
                                                            {
                                                                e.printStackTrace();
                                                            }
                                                        }
                                                    }).start();
                                                }
                                            }
                                            else if (analyzedMsgs[0].equals("query"))        //转发私聊
                                            {
                                                ps = new PrintStream(nameSocketMap.get(analyzedMsgs[2]).getOutputStream());
                                                ps.println("query&" + analyzedMsgs[1] + "&" + analyzedMsgs[3]);
                                            }
                                            else if (analyzedMsgs[0].equals("toMonitor"))
                                            {
                                                if (!onlineList.getSelectedValue().equals(analyzedMsgs[1]))      //给tab加高亮
                                                {
                                                    notifyRenderer.add(analyzedMsgs[1]);
                                                    onlineList.updateUI();
                                                }
                                                Document currentQueryDoc = nameDocMap.get(analyzedMsgs[1]);
                                                insertMsg(currentQueryDoc, generalTextColor, analyzedMsgs[1] + ": " + analyzedMsgs[2]);
                                            }
                                        }
                                    }
                                    catch(SocketException offlineException)         //有人下线
                                    {
                                        // 检索与清除
                                        clientList.remove(newClient);
                                        String offlineNickname = null;
                                        for(HashMap.Entry<String, Socket> nicknameIter : nameSocketMap.entrySet())      //由值反向求键，感觉很蛋疼
                                        {
                                            if (nicknameIter.getValue().equals(newClient))
                                            {
                                                offlineNickname = nicknameIter.getKey();
                                                break;
                                            }
                                        }

                                        // 离开私聊，如果在的话
                                        if (onlineList.getSelectedValue().equals(offlineNickname))
                                        {
                                            onlineList.setSelectedIndex(0);
                                            logArea.setDocument(publicDoc);
                                        }
                                        onlineListModel.removeElement(offlineNickname);
                                        //onlineList.updateUI();
                                        nameSocketMap.remove(offlineNickname);
                                        nameDocMap.remove(offlineNickname);

                                        // 消息更新
                                        if (!onlineList.getSelectedValue().equals("公频"))      //给公频tab加高亮
                                        {
                                            notifyRenderer.add("公频");
                                            onlineList.updateUI();
                                        }
                                        if (!offlineNickname.equals(personToKick))          //如果是被踢的，就不发信
                                        {
                                            insertMsg(publicDoc, activityTextColor, "【" + offlineNickname + "】刚刚下线了");

                                            // 消息奔走相告到各个客户端
                                            for(Socket clientIter : clientList)
                                            {
                                                try {
                                                    ps = new PrintStream(clientIter.getOutputStream());
                                                    ps.println("offline&" + offlineNickname);
                                                } catch(IOException must){}
                                            }
                                        }

                                        // 盖上棺材盖
                                        try {
                                            newClient.close();
                                        } catch(IOException must){}
                                    }
                                    catch(Exception ex)
                                    {
                                        ex.printStackTrace();
                                    }
                                }
                            }).start();
                            
                        }
                    }
                    catch(Exception e)
                    {
                        e.printStackTrace();
                    }
                    finally
                    {
                        try
                        {
                            server.close();
                            fileServerSocket.close();
                        }
                        catch(IOException ex)
                        {}
                    }
                }
            }).start();

        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
    //
    //  主方法
    //
    public static void main(String[] args)
    {
        new Server();                       //感觉有点用面向对象写面向过程的意味
    }
}
// Enjoy <3