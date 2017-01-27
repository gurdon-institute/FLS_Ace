

import java.awt.Color;
import java.awt.Dimension;

import java.awt.GridLayout;
import java.awt.Toolkit;

import javax.swing.*;
import javax.swing.BoxLayout;

public class Working extends JDialog{
public static int WIDTH = 120;
public static int HEIGHT = 60;
private static final Dimension size = new Dimension(WIDTH,HEIGHT);
private static final Color PINK = new Color(255,192,255);
private JLabel label;
	
public Working(String name,int pos){
	//setLayout(new BoxLayout(getContentPane(),BoxLayout.Y_AXIS));
	setLayout(new GridLayout(2,0,2,2));
	setUndecorated(true);
	getRootPane().setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.BLACK),BorderFactory.createEmptyBorder(5,5,5,5)));
	getRootPane().setBackground(PINK);
	getContentPane().setBackground(PINK);
	JLabel nameLabel = new JLabel(name);
	add(nameLabel);
	label = new JLabel("...");
	add(label);
	setPreferredSize(size);
	pack();
	int xPos = 0;
	int yPos = pos;
	if(pos>(HEIGHT*10)){
		xPos = WIDTH*(yPos/(HEIGHT*10));
		yPos = 0;
	}
	setLocation(WIDTH+xPos,HEIGHT+yPos);
	setVisible(true);
}

public void setText(String str){
	label.setText(str);
}

}
