package bothack.actions;

import bothack.bot.IPredicate;

/** Makes IPredicate callable natively from clojure (by implementing IFn via AFn) */
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