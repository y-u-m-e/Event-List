package com.claneventlist;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JComponent;
import net.runelite.client.ui.FontManager;

/** Draws a "grid" piece: rows x cols of cells coloured by their state's style. */
public class SeasonalGridComponent extends JComponent
{
	private final JsonObject grid;
	private final int rows;
	private final int cols;
	private final int cell;
	private final int gap;
	private final JsonObject[][] cells;
	private final Font font;

	public SeasonalGridComponent(JsonObject grid, Consumer<JsonObject> onCellDetail)
	{
		this.grid = grid;
		this.rows = Math.max(0, num(grid, "rows", 0));
		this.cols = Math.max(0, num(grid, "cols", 0));
		this.cell = Math.max(1, num(grid, "cell_size", 44));
		this.gap = Math.max(0, num(grid, "gap", 4));
		this.cells = new JsonObject[rows][cols];
		if (grid.has("cells") && grid.get("cells").isJsonArray())
		{
			for (JsonElement e : grid.getAsJsonArray("cells"))
			{
				if (!e.isJsonObject())
				{
					continue;
				}
				JsonObject c = e.getAsJsonObject();
				int r = num(c, "row", 0) - 1; // rows are 1-based, cols 0-based
				int k = num(c, "col", -1);
				if (r >= 0 && r < rows && k >= 0 && k < cols)
				{
					cells[r][k] = c;
				}
			}
		}
		Font f;
		try
		{
			f = FontManager.getRunescapeSmallFont();
		}
		catch (Throwable t)
		{
			f = null;
		}
		this.font = f != null ? f : new Font(Font.SANS_SERIF, Font.PLAIN, 12);

		setToolTipText("");
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				JsonObject c = cellAt(e.getX(), e.getY());
				if (c != null && c.has("detail") && c.get("detail").isJsonObject())
				{
					onCellDetail.accept(c.getAsJsonObject("detail"));
				}
			}
		});
	}

	private static int num(JsonObject o, String key, int def)
	{
		try
		{
			return o.has(key) ? o.get(key).getAsInt() : def;
		}
		catch (RuntimeException e)
		{
			return def;
		}
	}

	private static String str(JsonObject o, String key)
	{
		try
		{
			return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/** Cell under the point; a placeholder object for empty cells, null in a gap or outside. */
	private JsonObject cellAt(int x, int y)
	{
		int step = cell + gap;
		if (x < 0 || y < 0 || x % step >= cell || y % step >= cell)
		{
			return null;
		}
		int c = x / step;
		int r = y / step;
		if (r >= rows || c >= cols)
		{
			return null;
		}
		return cells[r][c] != null ? cells[r][c] : new JsonObject();
	}

	private JsonObject style(String state)
	{
		JsonObject styles = grid.has("styles") && grid.get("styles").isJsonObject() ? grid.getAsJsonObject("styles") : new JsonObject();
		JsonElement s = state == null ? null : styles.get(state);
		if (s == null || !s.isJsonObject())
		{
			s = styles.get("default");
		}
		return s != null && s.isJsonObject() ? s.getAsJsonObject() : new JsonObject();
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(Math.max(0, cols * cell + (cols - 1) * gap), Math.max(0, rows * cell + (rows - 1) * gap));
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		JsonObject c = cellAt(e.getX(), e.getY());
		return str(c, "tooltip");
	}

	// shortcut: no hover brightening like the website, add if players ask
	@Override
	protected void paintComponent(Graphics g0)
	{
		Graphics2D g = (Graphics2D) g0.create();
		try
		{
			g.setFont(font);
			FontMetrics fm = g.getFontMetrics();
			for (int r = 0; r < rows; r++)
			{
				for (int c = 0; c < cols; c++)
				{
					JsonObject cj = cells[r][c];
					JsonObject st = style(str(cj, "state"));
					int x = c * (cell + gap);
					int y = r * (cell + gap);
					g.setColor(SeasonalView.parseColor(str(st, "fill"), new Color(0, 0, 0, 0)));
					g.fillRect(x, y, cell, cell);
					g.setColor(SeasonalView.parseColor(str(st, "border"), Color.GRAY));
					g.drawRect(x, y, cell - 1, cell - 1);
					String text = str(cj, "label");
					if (text == null)
					{
						text = str(st, "mark");
					}
					if (text != null)
					{
						g.setColor(SeasonalView.parseColor(str(st, "text"), Color.WHITE));
						g.drawString(text, x + (cell - fm.stringWidth(text)) / 2, y + (cell + fm.getAscent() - fm.getDescent()) / 2);
					}
				}
			}
		}
		finally
		{
			g.dispose();
		}
	}
}
