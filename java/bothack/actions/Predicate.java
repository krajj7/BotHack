package bothack.actions;

import bothack.bot.IPredicate;

/** Makes IPredicate callable natively from clojure (by implementing IFn via AFn).
 * IPredicates are wrapped automatically – bots don't need to use this. */
class Predicate extends clojure.lang.AFn {
	final IPredicate p;

	Predicate(IPredicate p) {
		this.p = p;
	}
	
	@Override
	public Object invoke(Object x) {
		return p.apply(x);
	}
}