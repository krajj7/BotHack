package anbf;

/** a near-trascription of Sorear's Perl implementation of NetHack FOV simulation.
  * https://github.com/sorear/NetHack-FOV/blob/master/lib/NetHack/FOV.pm */
public class NHFov {
	private boolean[][] visible;
	private int x;
	private int y;
	private TransparencyInfo cbi;

	public interface TransparencyInfo {
		public boolean isTransparent(int x, int y);
	}

	private boolean clear(int x, int y) {
		return cbi.isTransparent(this.x+x, this.y+y);
	}
	
	private void cbo(int x, int y) {
		if (x >= 0 && y >= 0)
			visible[y][x] = true;
	}

	private void see(int x, int y) {
		cbo(this.x+x, this.y+y);
	}
	
	private boolean Qpath(int x, int y) {
		//System.out.println("qpath "+x+" "+y);
		int[] px = {0}, py = {0};
		boolean flip = Math.abs(x) > Math.abs(y);
		int[] rmaj = flip ? px : py;
		int[] rmin = flip ? py : px;
		int dmaj = flip ? x : y;
		int dmin = flip ? y : x;
		
		int fmin = -Math.abs(dmaj);
		
		for (int i = 2; i <= Math.abs(dmaj); ++i) {
			fmin += 2*Math.abs(dmin);
			if (fmin >= 0) {
				fmin -= 2*Math.abs(dmaj);
				rmin[0] += cmp(dmin, 0);
			}
			rmaj[0] += cmp(dmaj, 0);
			if (!clear(px[0],py[0]))
				return false;
		}
		return true;
	}
	
	private void quadrant(int hs, int row, int left, int rightMark) {
		//System.out.println("quadrant "+hs+" "+row+" "+left+" "+rightMark);
		int right, rightEdge;
		int rail = (hs == 1) ? 79-x : x;
		
		while (left <= rightMark) {
			rightEdge = left;
			boolean leftClear = clear(hs*left, row);
			while (clear(hs*rightEdge, row) == leftClear &&
					(leftClear || rightEdge <= rightMark+1))
				rightEdge++;
			rightEdge--;
			if (leftClear)
				rightEdge++;
			
			if (rightEdge >= rail)
				rightEdge = rail;
		
			if (!leftClear) {
				if (rightEdge > rightMark) {
					rightEdge = clear(hs*rightMark,
							row-(cmp(row, 0))) ? rightMark+1 : rightMark;
				}
				
				for (int i = left; i <= rightEdge; ++i) {
					see(hs*i, row);
				}
				left = rightEdge+1;
				continue;
			}
			
			if (left != 0) {
				for (; left <= rightEdge; ++left) {
					if (Qpath(hs*left, row))
						break;
				}
				
				if (left >= rail) {
					if (left == rail) {
						see(left*hs, row);
					}

					return;
				}
				
				if (left >= rightEdge) {
					left = rightEdge;
					continue;
				}
			}
			
			if (rightMark < rightEdge) {
				for (right = rightMark; right <= rightEdge; ++right) {
					if (!Qpath(hs*right, row))
						break;
				}
				--right;
			} else {
				right = rightEdge;
			}
			
			if (left <= right) {
				if (left == right && left == 0 && !clear(hs,row) && (left !=rail)) {
					right = 1;
				}
				
				if (right > rail)
					right = rail;
				
				for (int i = left; i <= right; ++i) {
					see(hs*i,row);
				}
				
				quadrant(hs, row+(cmp(row, 0)), left, right);
				left = right + 1;
			}
		}
	}
	
	private void trace() {
		int xl = 0, xr = 0;
		see(0,0);
		
		do see(--xl,0); while (clear(xl, 0));
		do see(++xr,0); while (clear(xr, 0));
		
		if (xr + x == 80)
			xr--;
		if (xl + x < 0)
			xl++;
		
		quadrant(-1,-1,0,-xl);
		quadrant(+1,-1,0,xr);
		quadrant(-1,+1,0,-xl);
		quadrant(+1,+1,0,xr);
	}
	
	public boolean[][] calculateFov(int startx, int starty, TransparencyInfo cb) {
		visible = new boolean[24][80];
		x = startx;
		y = starty;
		cbi = cb;
		trace();
		return visible;
	}
	
	private static int cmp(int x, int y) {
		return Integer.signum(Integer.compare(x, y));
	}
}
