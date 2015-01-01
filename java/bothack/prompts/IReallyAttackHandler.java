package bothack.prompts;

/** Called when you move towards a peaceful monster. */
public interface IReallyAttackHandler {
/** Called when you move towards a peaceful monster. 
 * False is returned by default.
 * @param what The name of the monster
 * @return True to attack the monster */
	Boolean reallyAttack(String what);
}
