package org.janelia.saalfeldlab;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Intervals;

class J {

	public static void main(final String[] s) {

		BdvFunctions.show(new FunctionRealRandomAccessible<>(2, (x, y) -> {
			int i = 0;
			double v = 0, c = x.getDoublePosition(0), d = x.getDoublePosition(1);
			while (i < 64 && v < 4096) {
				final double e = c * c - d * d;
				d = 2 * c * d;
				c = e + 0.2;
				d += 0.6;
				v = Math.sqrt(c * c + d * d);
				++i;
			}
			y.set(i);
		}, IntType::new), Intervals.createMinMax(-1, -1, 1, 1), "", BdvOptions.options().is2D()).setDisplayRange(0, 64);
	}
}