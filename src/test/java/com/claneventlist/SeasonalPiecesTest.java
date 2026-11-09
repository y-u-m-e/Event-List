package com.claneventlist;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SeasonalPiecesTest
{
	private static final String STYLES = "\"styles\":{"
		+ "\"default\":{\"border\":\"#3a2a24ff\",\"fill\":\"#00000066\",\"text\":\"#ffffffff\"},"
		+ "\"completed\":{\"border\":\"#10b981ff\",\"fill\":\"#10b9814d\",\"mark\":\"✓\",\"text\":\"#ffffffff\"}}";
	private static final String GRID_JSON = "{\"type\":\"grid\",\"rows\":1,\"cols\":2,\"cell_size\":44,\"gap\":4," + STYLES
		+ ",\"cells\":[{\"row\":1,\"col\":0,\"state\":\"completed\",\"tooltip\":\"t\","
		+ "\"detail\":{\"title\":\"Done\",\"body\":\"b\",\"hint\":\"h\"}}]}";

	private static JsonObject obj(String json)
	{
		return new JsonParser().parse(json).getAsJsonObject();
	}

	private static String textOf(Component c)
	{
		StringBuilder sb = new StringBuilder();
		if (c instanceof JLabel)
		{
			sb.append(((JLabel) c).getText());
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				sb.append(' ').append(textOf(k));
			}
		}
		return sb.toString();
	}

	private static BufferedImage paint(JComponent c)
	{
		c.setSize(c.getPreferredSize());
		BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
		c.paint(img.createGraphics());
		return img;
	}

	@Test
	public void unknownTypeFallsBackToLink()
	{
		JComponent c = SeasonalPieces.render(obj("{\"type\":\"wheel\"}"), "https://ironforged.gg/seasonal-event", d -> {});
		assertTrue(textOf(c).contains("ironforged.gg"));
	}

	@Test
	public void knownTypesRender()
	{
		String[] pieces = {
			"{\"type\":\"header\",\"title\":\"Delve\",\"text\":\"Team\",\"color\":\"#fbbf24\"}",
			"{\"type\":\"bar\",\"label\":\"Tiles\",\"value\":3,\"max\":10,\"color\":\"#10b981\"}",
			"{\"type\":\"stats\",\"rows\":[{\"label\":\"Done\",\"value\":\"3\"}]}",
			"{\"type\":\"text\",\"text\":\"hello\",\"muted\":true}"};
		for (String p : pieces)
		{
			assertNotNull(SeasonalPieces.render(obj(p), "u", d -> {}));
		}
		assertTrue(textOf(SeasonalPieces.render(obj(pieces[3]), "u", d -> {})).contains("hello"));
	}

	@Test
	public void gridPaintsStyleColors()
	{
		SeasonalGridComponent g = new SeasonalGridComponent(obj(GRID_JSON), d -> {});
		assertEquals(new Dimension(92, 44), g.getPreferredSize());
		BufferedImage img = paint(g);
		assertEquals(0xff10b981, img.getRGB(0, 22));
		assertEquals(0xff3a2a24, img.getRGB(48, 22));
	}

	@Test
	public void unknownStateUsesDefault()
	{
		String json = GRID_JSON.replace("\"state\":\"completed\"", "\"state\":\"mystery\"");
		BufferedImage img = paint(new SeasonalGridComponent(obj(json), d -> {}));
		assertEquals(0xff3a2a24, img.getRGB(0, 22));
	}

	@Test
	public void clickOnDetailCellReportsIt()
	{
		AtomicReference<JsonObject> got = new AtomicReference<>();
		SeasonalGridComponent g = new SeasonalGridComponent(obj(GRID_JSON), got::set);
		g.setSize(g.getPreferredSize());
		g.dispatchEvent(new MouseEvent(g, MouseEvent.MOUSE_CLICKED, 0, 0, 22, 22, 1, false));
		assertEquals("Done", got.get().get("title").getAsString());
		got.set(null);
		g.dispatchEvent(new MouseEvent(g, MouseEvent.MOUSE_CLICKED, 0, 0, 70, 22, 1, false));
		assertNull(got.get());
	}
}
