package bothack.bot;

import java.util.Map;

/** Immutable representation of a 80x24 virtual terminal. */
public interface IFrame {
	/** Cursor position on the virtual terminal. */
	public IPosition cursor();
	/** The color of the glyph at given position on the virtual terminal. */
	public Color colorAt(IPosition pos);
	/** The glyph at given position on the virtual terminal. */
	public Character glyphAt(IPosition pos);
	/** 
	 * The row of text at the given y-coordinate on the virtual terminal. 
	 * @param y 0 to 23 inclusive.
	 */
	public String line(Long y);
}
