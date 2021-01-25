package net.imglib2.algorithm.region.stardist;

import org.junit.BeforeClass;
import org.junit.Test;

import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Util;

public class StarDistTest {

	static final private double[][] rays = new double[][]{
			{1, 0},
			{0, 1},
			{-1, 0},
			{0, -1}
	};

	static final private double[] dists = new double[]{
			10, 5, 3, 20
	};

	static final private long[] position = new long[]{
			100, 200
	};

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {}

	@Test
	public void test() {

		final FunctionRandomAccessible<IntType> img = new FunctionRandomAccessible<>(2, (x, y) -> {}, IntType::new);

		final StarDist<IntType> starDist = new StarDist<>(img, position, rays, dists, 10);
		final StarDist<IntType>.Cursor cursor = starDist.cursor();
		while (cursor.hasNext()) {
			cursor.fwd();
			System.out.println(Util.printCoordinates(cursor));
		}
	}

}
