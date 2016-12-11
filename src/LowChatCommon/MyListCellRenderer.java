package LowChatCommon;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;

/**
 * Created by Equim on 16-12-9.
 */

// 用于处理高亮
public class MyListCellRenderer extends DefaultListCellRenderer
{
    private final Color notifyTextColor = new Color(204, 0, 0);           //私聊tab提醒文字颜色
    private final Color notifyBackgroundColor = new Color(230, 184, 0);   //私聊tab提醒背景颜色
    private final Font myFont = new Font("微软雅黑", Font.PLAIN, 16);
    // 这里之前用的是LinkedList。话说也是深切地感受到了面向对象封装的优势，只要到这改改就行了，对外的方法都不变。
    private HashSet<String> highlightSet;

    public MyListCellRenderer()
    {
        highlightSet = new HashSet<>();
    }

    public boolean add(String newName)
    {
        return highlightSet.add(newName);
    }

    public boolean remove(String name)
    {
        return highlightSet.remove(name);
    }

    // 其实这个用途不止是更新高亮，字体设置也是在这
    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
    {
        Component temp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (highlightSet.contains(value.toString()))
        //if (value.toString().equals(queryTo) || (queryTo.equals("公频") && value.toString().equals("公频")))
        {
            temp.setForeground(notifyTextColor);
            temp.setBackground(notifyBackgroundColor);
        }
        temp.setFont(myFont);
        return temp;
    }
}