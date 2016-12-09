import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Equim on 16-11-27.
 * 向Java势力低头 vol.1
 */
public class Server extends JFrame
{
    private SimpleAttributeSet attrSet = new SimpleAttributeSet();  //文字属性
    private Document publicDoc;           //会在Init_UI中初始化

    private JTextField inputTextBox = new JTextField();
    private JTextPane logArea = new JTextPane();                    //为了支持每行不同颜色
    private JButton textButton = new JButton("发送");
    private JList onlineList = new JList();
    private DefaultListModel onlineListModel = new DefaultListModel();

    private final Color systemTextColor = new Color(0, 204, 51);          //系统信息的颜色，本来想用enum，结果又感觉不合适
    private final Color activityTextColor = new Color(0, 163, 204);       //上下线信息的颜色
    private final Color notifyTextColor = new Color(204, 0, 0);           //私聊tab提醒文字颜色
    private final Color notifyBackgroundColor = new Color(230, 184, 0);   //私聊tab提醒背景颜色
    private final Color monitorTextColor = new Color(102, 0, 102);        //群主发的消息的颜色
    private final Color generalTextColor = new Color(0, 0, 0);            //一般消息的颜色

//    private JTabbedPane tabPane = new JTabbedPane(JTabbedPane.TOP);       //放弃尝试了

    private MenuBar serverMenu = new MenuBar();
    private Menu m_help = new Menu("Help");
    private MenuItem m_about = new MenuItem("About");

    private ServerSocket server;
    private LinkedList<Socket> clientList = new LinkedList<Socket>();
    private Map<String, Socket> nameSocketMap = new HashMap<>();
    private Map<String, Document> nameDocMap = new HashMap<>();

    private PrintStream ps;

    private String getTimeStamp()
    {
        return new SimpleDateFormat("[hh:mm:ss] ").format(new Date());
    }
    //
    //  UI初始化
    //
    private void Init_UI()                              //TODO: 加上滚动条支持
    {
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setSize(400, 550);
        this.setTitle("服务端");

        publicDoc = logArea.getStyledDocument();
        StyleConstants.setFontSize(attrSet, 16);
//        StyleConstants.setFontFamily(attrSet, "微软雅黑");


        logArea.setEditable(false);
        this.add(logArea, BorderLayout.CENTER);
        JPanel bottom = new JPanel();
        bottom.setLayout(new BorderLayout());
        bottom.add(inputTextBox, BorderLayout.CENTER);
        bottom.add(textButton, BorderLayout.EAST);
        this.add(bottom, BorderLayout.SOUTH);

        onlineListModel.addElement("公频");
        //DEBUG
        //onlineListModel.addElement("Equim");
        //onlineListModel.addElement("Vivian");
        //
        onlineList.setModel(onlineListModel);
        onlineList.setBackground(Color.lightGray);
        onlineList.setSelectedIndex(0);
        this.add(onlineList, BorderLayout.WEST);

        m_help.add(m_about);
        //关于信息
        m_about.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try
                {
                    if (JOptionPane.showConfirmDialog(null,
                            "真的很不乐意用Java啊！！\n具体的等写完再说吧\n\n要不要去我的GitHub看看呢？",
                            "关于", JOptionPane.YES_NO_OPTION) == 0)
                        Desktop.getDesktop().browse(new URI("https://github.com/Equim-chan"));
                }
                catch(Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        });

        serverMenu.add(m_help);
        this.setMenuBar(serverMenu);
        this.setVisible(true);
    }


    //
    //  构造器
    //
    public Server()
    {
        try
        {
            this.Init_UI();

            server = new ServerSocket(2333);
//            logArea.setForeground(systemMsg);
//            logArea.append(getTimeStamp() + "服务器已启动。 (127.0.0.1:2333)\n");
            StyleConstants.setForeground(attrSet, systemTextColor);
            publicDoc.insertString(publicDoc.getLength(), getTimeStamp() + "服务器已启动。(127.0.0.1:2333)\n", attrSet);

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

                            // 发送群主的消息，可能是公频也可能是私聊
                            ActionListener sendText = new ActionListener() {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    if (!inputTextBox.getText().isEmpty())        //其实这里会触发bug，不知道为什么，有几个socket就会发几次
                                    {
                                        try
                                        {
                                            String sendToWho = onlineList.getSelectedValue().toString();
                                            if (sendToWho.equals("公频"))
                                            {
                                                StyleConstants.setForeground(attrSet, monitorTextColor);
                                                publicDoc.insertString(publicDoc.getLength(),
                                                        getTimeStamp() + "群主: " + inputTextBox.getText() +"\n", attrSet);

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
                                                StyleConstants.setForeground(attrSet, monitorTextColor);
                                                currentQueryDoc.insertString(currentQueryDoc.getLength(),
                                                        getTimeStamp() + "群主: " + inputTextBox.getText() +"\n", attrSet);

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
                                        String queryTo = onlineList.getSelectedValue().toString();      //私聊给谁

                                        onlineList.setCellRenderer(new DefaultListCellRenderer() {      //撤销高亮
                                            @Override                                                   //TODO: 这里有bug！！！首先，只能高亮一个，然后，点任何一个都能解除高亮。
                                            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                                                Component temp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                                                if (value.toString().equals(queryTo) || (queryTo.equals("公频") && value.toString().equals("公频")))
                                                {
                                                    setForeground(Color.black);
                                                    setBackground(Color.lightGray);
                                                }
                                                return temp;
                                            }
                                        });

                                        if (queryTo.equals("公频"))
                                        {
                                            //mainDoc.insertString(mainDoc.getLength(), doc.getText(0, doc.getLength()), attrSet);      //不可取
                                            logArea.setDocument(publicDoc);
                                        }
                                        else
                                        {
                                            logArea.setDocument(nameDocMap.get(queryTo));
                                        }
                                    }
                                }
                            });

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
                                            // new&username                     | 添加新用户名
                                            // public&username&msg              | 公频发言
                                            // toMonitor&username&msg           | 给群主的发言
                                            // query&username&toUsername&msg    | 私聊发言
                                            // fileFlag&username&toUsername     | 传文件请求
                                            //不敢另开方法来解决，是怕遇到死锁
                                            String[] analyzedMsgs = receivedMsg.split("&");
                                            if (analyzedMsgs[0].equals("new"))
                                            {
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
                                                StyleConstants.setForeground(attrSet, activityTextColor);
                                                publicDoc.insertString(publicDoc.getLength(),
                                                        getTimeStamp() + "【" + analyzedMsgs[1] + "】刚刚上线了\n", attrSet);

                                            }
                                            else if (analyzedMsgs[0].equals("public"))
                                            {
                                                if (!onlineList.getSelectedValue().toString().equals("公频"))      //给tab加高亮
                                                {
                                                    onlineList.setCellRenderer(new DefaultListCellRenderer() {
                                                        @Override
                                                        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                                                            Component temp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                                                            if (value.toString().equals("公频"))
                                                            {
                                                                setForeground(notifyTextColor);
                                                                setBackground(notifyBackgroundColor);
                                                            }
                                                            return temp;
                                                        }
                                                    });
                                                }

                                                StyleConstants.setForeground(attrSet, generalTextColor);
                                                publicDoc.insertString(publicDoc.getLength(),
                                                        getTimeStamp() + analyzedMsgs[1] + ": " + analyzedMsgs[2] + "\n", attrSet);

                                                //遍历socket list群发
                                                for(Socket clientIter : clientList)
                                                {
                                                    ps = new PrintStream(clientIter.getOutputStream());
                                                    ps.println("public&" + analyzedMsgs[1] + "&" + analyzedMsgs[2]);
                                                }
                                            }
                                            else if (analyzedMsgs[0].equals("fileFlag"))
                                            {
                                                //TODO:传文件功能
                                            }
                                            else if (analyzedMsgs[0].equals("query"))        //转发私聊
                                            {
                                                ps = new PrintStream(nameSocketMap.get(analyzedMsgs[2]).getOutputStream());
                                                ps.println("query&" + analyzedMsgs[1] + "&" + analyzedMsgs[3]);
                                            }
                                            else if (analyzedMsgs[0].equals("toMonitor"))
                                            {
                                                if (!onlineList.getSelectedValue().toString().equals(analyzedMsgs[1]))      //给tab加高亮
                                                {
                                                    onlineList.setCellRenderer(new DefaultListCellRenderer() {
                                                        @Override
                                                        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                                                            Component temp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                                                            if (value.toString().equals(analyzedMsgs[1]))
                                                            {
                                                                setForeground(notifyTextColor);
                                                                setBackground(notifyBackgroundColor);
                                                            }
                                                            return temp;
                                                        }
                                                    });
                                                }
                                                Document currentQueryDoc = nameDocMap.get(analyzedMsgs[1]);

                                                StyleConstants.setForeground(attrSet, generalTextColor);
                                                currentQueryDoc.insertString(currentQueryDoc.getLength(),
                                                        getTimeStamp() + analyzedMsgs[1] + ": " + analyzedMsgs[2] + "\n", attrSet);
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
                                        if (onlineList.getSelectedValue().toString().equals(offlineNickname))
                                            onlineList.setSelectedIndex(0);
                                        onlineListModel.removeElement(offlineNickname);
                                        nameSocketMap.remove(offlineNickname);
                                        nameDocMap.remove(offlineNickname);

                                        // 消息更新
                                        try
                                        {
                                            StyleConstants.setForeground(attrSet, activityTextColor);
                                            publicDoc.insertString(publicDoc.getLength(),
                                                    getTimeStamp() + "【" + offlineNickname + "】刚刚下线了\n", attrSet);
                                        }
                                        catch(BadLocationException impossible)
                                        {
                                            impossible.printStackTrace();
                                        }

                                        // 消息奔走相告到各个客户端
                                        try
                                        {
                                            for(Socket clientIter : clientList)
                                            {
                                                ps = new PrintStream(clientIter.getOutputStream());
                                                ps.println("offline&" + offlineNickname);
                                            }

                                        // 盖上棺材盖
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