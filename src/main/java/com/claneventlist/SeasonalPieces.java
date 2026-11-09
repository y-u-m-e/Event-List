package com.claneventlist;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/** Turns one API display piece into a Swing component. Knows nothing about event types. */
public final class SeasonalPieces
{
	private interface Builder
	{
		JComponent build(JsonObject piece, String webUrl, Consumer<JsonObject> onCellDetail);
	}

	private static final Map<String, Builder> BUILDERS = new HashMap<>();

	static
	{
		BUILDERS.put("header", (p, u, d) -> header(p));
		BUILDERS.put("grid", (p, u, d) -> new SeasonalGridComponent(p, d));
		BUILDERS.put("bar", (p, u, d) -> bar(p));
		BUILDERS.put("stats", (p, u, d) -> stats(p));
		BUILDERS.put("text", (p, u, d) -> text(p));
	}

	private SeasonalPieces()
	{
	}

	public static JComponent render(JsonObject piece, String webUrl, Consumer<JsonObject> onCellDetail)
	{
		String type = str(piece, "type", "");
		Builder b = BUILDERS.get(type);
		if (b != null)
		{
			try
			{
				return b.build(piece, webUrl, onCellDetail);
			}
			catch (RuntimeException e)
			{
				// malformed piece: fall through to the link
			}
		}
		return link(webUrl);
	}

	private static JComponent link(String webUrl)
	{
		JLabel l = new JLabel("This part of the event is on ironforged.gg");
		l.setForeground(ColorScheme.BRAND_ORANGE);
		l.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		l.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(webUrl);
			}
		});
		return l;
	}

	private static String str(JsonObject o, String key, String def)
	{
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
	}

	private static Font font(boolean bold)
	{
		try
		{
			Font f = bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont();
			if (f != null)
			{
				return f;
			}
		}
		catch (Throwable t)
		{
			// headless or font load failure: plain font
		}
		return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 12);
	}

	/** RuneScape fonts lack glyphs such as the check mark; draw such text in a logical font instead. */
	static Font displayable(Font f, String text)
	{
		return text == null || f.canDisplayUpTo(text) == -1 ? f : new Font(Font.DIALOG, Font.BOLD, f.getSize());
	}

	private static JLabel label(String text, Color color, boolean bold)
	{
		JLabel l = new JLabel(text);
		l.setForeground(color);
		l.setFont(displayable(font(bold), text));
		return l;
	}

	private static JPanel row()
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setOpaque(false);
		return p;
	}

	private static JComponent header(JsonObject p)
	{
		JPanel row = row();
		JPanel swatch = new JPanel();
		swatch.setBackground(SeasonalView.parseColor(str(p, "swatch", null), ColorScheme.BRAND_ORANGE));
		swatch.setPreferredSize(new Dimension(12, 12));
		JPanel left = new JPanel(new BorderLayout(6, 0));
		left.setOpaque(false);
		JPanel swatchWrap = new JPanel(new java.awt.GridBagLayout());
		swatchWrap.setOpaque(false);
		swatchWrap.add(swatch);
		left.add(swatchWrap, BorderLayout.WEST);
		left.add(label(str(p, "title", ""), Color.WHITE, true), BorderLayout.CENTER);
		row.add(left, BorderLayout.WEST);
		JLabel right = label(str(p, "right", ""), ColorScheme.LIGHT_GRAY_COLOR, false);
		right.setHorizontalAlignment(JLabel.RIGHT);
		row.add(right, BorderLayout.CENTER);
		return row;
	}

	private static JComponent bar(JsonObject p)
	{
		JPanel box = new JPanel(new BorderLayout(0, 2));
		box.setOpaque(false);
		box.add(label(str(p, "label", ""), ColorScheme.LIGHT_GRAY_COLOR, false), BorderLayout.NORTH);
		int max = Math.max(1, (int) Double.parseDouble(str(p, "max", "1")));
		int value = (int) Double.parseDouble(str(p, "value", "0"));
		JProgressBar bar = new JProgressBar(0, max);
		bar.setValue(Math.max(0, Math.min(max, value)));
		bar.setStringPainted(true);
		bar.setString(value + " / " + max);
		bar.setForeground(SeasonalView.parseColor(str(p, "color", null), ColorScheme.BRAND_ORANGE));
		bar.setPreferredSize(new Dimension(0, 14));
		box.add(bar, BorderLayout.CENTER);
		return box;
	}

	private static JComponent stats(JsonObject p)
	{
		JPanel box = new JPanel(new GridLayout(0, 1, 0, 2));
		box.setOpaque(false);
		if (p.has("rows") && p.get("rows").isJsonArray())
		{
			for (JsonElement e : p.getAsJsonArray("rows"))
			{
				if (!e.isJsonObject())
				{
					continue;
				}
				JsonObject r = e.getAsJsonObject();
				JPanel line = row();
				line.add(label(str(r, "label", ""), ColorScheme.LIGHT_GRAY_COLOR, false), BorderLayout.WEST);
				JLabel v = label(str(r, "value", ""), Color.WHITE, false);
				v.setHorizontalAlignment(JLabel.RIGHT);
				line.add(v, BorderLayout.CENTER);
				box.add(line);
			}
		}
		return box;
	}

	private static JComponent text(JsonObject p)
	{
		boolean muted = "muted".equals(str(p, "tone", "normal"));
		JLabel l = label(str(p, "text", ""), muted ? ColorScheme.LIGHT_GRAY_COLOR : Color.WHITE, false);
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		return l;
	}
}
