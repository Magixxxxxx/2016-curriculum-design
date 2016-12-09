import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Main on 16-11-28.
 */
public class Client extends JFrame
{
    private SimpleAttributeSet attrSet = new SimpleAttributeSet();
    private Document publicDoc;

    private JTextField inputTextBox = new JTextField();
    private JTextPane logArea = new JTextPane();
    private JButton textButton = new JButton("发送");
    private JList onlineList = new JList();
    private DefaultListModel onlineListModel = new DefaultListModel();

    private MenuBar serverMenu = new MenuBar();
    private Menu m_help = new Menu("Help");
    private MenuItem m_about = new MenuItem("About");

    private final Color systemTextColor = new Color(0, 204, 51);
    private final Color activityTextColor = new Color(0, 163, 204);
    private final Color notifyTextColor = new Color(204, 0, 0);
    private final Color notifyBackgroundColor = new Color(230, 184, 0);
    private final Color selfTextColor = new Color(0, 51, 102);       //自己发的消息的颜色
    private final Color monitorTextColor = new Color(102, 0, 102);
    private final Color generalTextColor = new Color(0, 0, 0);

    private PrintStream ps;
	private BufferedReader br;
    private String nickname;
    
    private Socket client;
    private boolean connecting = true;
    private Map<String, Document> nameDocMap = new HashMap<>();

    private String getTimeStamp()
    {
        return new SimpleDateFormat("[hh:mm:ss] ").format(new Date());
    }
    //
    //  UI初始化
    //
    private void Init_UI()
    {
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setSize(400, 550);
        this.setTitle(String.format("客户端(%s)", nickname));

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
    public Client()
    {
        try
        {
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

            this.Init_UI();

            client = new Socket("127.0.0.1", 2333);
            StyleConstants.setForeground(attrSet, systemTextColor);
            publicDoc.insertString(publicDoc.getLength(), getTimeStamp() + "已连接到服务器。(127.0.0.1:2333)\n", attrSet);

            ps = new PrintStream(client.getOutputStream());
            br = new BufferedReader(new InputStreamReader(client.getInputStream()));

            // 发送消息，可能是公频或私聊
            ActionListener sendText = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (inputTextBox.getText() != null)
                    {
                        try
                        {
                            String sendToWho = onlineList.getSelectedValue().toString();
                            if (sendToWho.equals("公频"))
                            {
                                ps.println("public&" + nickname + "&" + inputTextBox.getText());

                                StyleConstants.setForeground(attrSet, selfTextColor);
                                publicDoc.insertString(publicDoc.getLength(),
                                        getTimeStamp() + nickname + ": " + inputTextBox.getText() +"\n", attrSet);
                            }
                            else
                            {
                                if (sendToWho.equals("群主"))
                                    ps.println("toMonitor&" + nickname + "&" + inputTextBox.getText());
                                else
                                    ps.println("query&" + nickname + "&" + sendToWho + inputTextBox.getText());

                                Document currentQueryDoc = logArea.getDocument();
                                StyleConstants.setForeground(attrSet, selfTextColor);
                                currentQueryDoc.insertString(currentQueryDoc.getLength(),
                                        getTimeStamp() + nickname + ": " + inputTextBox.getText() +"\n", attrSet);
                            }

                            inputTextBox.setText(null);
                        }
                        catch(BadLocationException ex)
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
                        String queryTo = onlineList.getSelectedValue().toString();      //私聊给谁

                        onlineList.setCellRenderer(new DefaultListCellRenderer() {      //撤销高亮
                            @Override
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
                            // monitor&msg                | 服务器消息
                            // public&username&msg        | 公频消息
                            // query&username&msg         | 私聊消息
                            // onlineList&username&...    | 在线列表 (刚刚登陆时获取)
                            // online&username&           | 上线信息
                            // offline&username           | 下线信息
                            String[] analyzedMsgs = receivedMsg.split("&");
                            if (analyzedMsgs[0].equals("monitor"))
                            {
                                StyleConstants.setForeground(attrSet, monitorTextColor);
                                publicDoc.insertString(publicDoc.getLength(),
                                        getTimeStamp() + "群主: " + analyzedMsgs[1] + "\n", attrSet);
                            }
                            else if (analyzedMsgs[0].equals("public") && !analyzedMsgs[1].equals(nickname))  //不然自己发的又被传回了
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
                            }
                            else if (analyzedMsgs[0].equals("query"))
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
                            else if (analyzedMsgs[0].equals("onlineList"))
                            {
                                //刚刚上线会收到已经在线的人的列表
                                for(int i=1;i<=analyzedMsgs.length-1;i++)
                                {
                                    onlineListModel.addElement(analyzedMsgs[i]);
                                    nameDocMap.put(analyzedMsgs[i], new DefaultStyledDocument());
                                }
                            }
                            else if (analyzedMsgs[0].equals("online") && !analyzedMsgs[1].equals(nickname))
                            {
                                //有人上线的情况
                                onlineListModel.addElement(analyzedMsgs[1]);
                                nameDocMap.put(analyzedMsgs[1], new DefaultStyledDocument());

                                StyleConstants.setForeground(attrSet, activityTextColor);
                                publicDoc.insertString(publicDoc.getLength(),
                                        getTimeStamp() + "【" + analyzedMsgs[1] + "】刚刚上线了\n", attrSet);
                            }
                            else if (analyzedMsgs[0].equals("offline"))
                            {
                                //有人下线的情况
                                onlineListModel.removeElement(analyzedMsgs[1]);
                                nameDocMap.remove(analyzedMsgs[1]);
                                try
                                {
                                    StyleConstants.setForeground(attrSet, activityTextColor);
                                    publicDoc.insertString(publicDoc.getLength(),
                                            getTimeStamp() + "【" + analyzedMsgs[1] + "】刚刚下线了\n", attrSet);
                                }
                                catch(BadLocationException impossible)
                                {
                                    impossible.printStackTrace();
                                }
                            }
                        }
                        catch(SocketException disconnectionException)
                        {
                            onlineListModel.removeAllElements();

                            try
                            {
                                StyleConstants.setForeground(attrSet, systemTextColor);
                                publicDoc.insertString(publicDoc.getLength(),
                                        getTimeStamp() + "与服务器断开连接\n", attrSet);
                            }
                            catch(BadLocationException impossible)
                            {
                                impossible.printStackTrace();
                            }

                            connecting = false;

                            try { client.close(); } catch(IOException must){}
                        }
                        catch(Exception e)
                        {
                            e.printStackTrace();
                        }
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
        new Client();
    }
}
// Enjoy <3