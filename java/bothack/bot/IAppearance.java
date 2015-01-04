package bothack.bot;

/** Immutable representation of a virtual terminal character. */
public interface IAppearance {
	Character glyph();
	Color color();
}
