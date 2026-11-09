package com.claneventlist;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/** Popout of the team's board pieces. All methods run on the EDT. */
public class SeasonalBoardWindow extends JFrame
{
	private static final String FALLBACK_URL = "https://ironforged.gg/seasonal-event";

	private final Runnable onRefresh;
	private final JPanel content = new JPanel();
	private SeasonalView current;
	private boolean positioned;

	public SeasonalBoardWindow(Runnable onRefresh)
	{
		this.onRefresh = onRefresh;
		setDefaultCloseOperation(HIDE_ON_CLOSE);
		getContentPane().setBackground(ColorScheme.DARKER_GRAY_COLOR);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
		add(scroll, BorderLayout.CENTER);
		setSize(560, 720);
	}

	/** Opens or raises the window. Position is kept after the first placement. */
	public void showView(SeasonalView v)
	{
		setView(v);
		if (!positioned)
		{
			setLocationRelativeTo(null);
			positioned = true;
		}
		setVisible(true);
		toFront();
	}

	/** Rebuilds the pieces in place. */
	public void setView(SeasonalView v)
	{
		current = v;
		content.removeAll();
		if (v == null)
		{
			setTitle("Seasonal event");
		}
		else
		{
			String event = v.event != null && v.event.name != null ? v.event.name : "Seasonal event";
			setTitle(v.team != null && v.team.name != null ? event + " · " + v.team.name : event);
			String url = webUrl(v);
			if (v.pieces != null)
			{
				for (JsonObject piece : v.pieces)
				{
					JComponent c = SeasonalPieces.render(piece, url, this::showDetail);
					c.setAlignmentX(Component.LEFT_ALIGNMENT);
					content.add(c);
					content.add(Box.createVerticalStrut(8));
				}
			}
			if (v.message != null && !v.message.isEmpty())
			{
				JLabel m = new JLabel(v.message);
				m.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				m.setAlignmentX(Component.LEFT_ALIGNMENT);
				content.add(m);
				content.add(Box.createVerticalStrut(8));
			}
		}
		JButton refresh = new JButton("Refresh");
		refresh.setAlignmentX(Component.LEFT_ALIGNMENT);
		refresh.addActionListener(e -> onRefresh.run());
		content.add(refresh);
		content.revalidate();
		content.repaint();
	}

	private static String webUrl(SeasonalView v)
	{
		return v != null && v.webUrl != null ? v.webUrl : FALLBACK_URL;
	}

	private static String str(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	private void showDetail(JsonObject detail)
	{
		String body = str(detail, "body");
		String hint = str(detail, "hint");
		String text = hint.isEmpty() ? body : body + "\n\n" + hint;
		String url = webUrl(current);
		Object[] options = {"Submit on ironforged.gg", "Close"};
		int choice = JOptionPane.showOptionDialog(this, text, str(detail, "title"),
			JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[1]);
		if (choice == 0)
		{
			LinkBrowser.browse(url);
		}
	}
}
