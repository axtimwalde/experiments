/**
 *
 */
package org.janelia.saalfeldlab;

import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ij.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.roi.RealMaskRealInterval;
import net.imglib2.roi.Regions;
import net.imglib2.roi.geom.GeomMasks;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.view.Views;
import net.imglib2.view.composite.Composite;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.GenericComposite;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class StarDist implements Callable<Void> {

	@Option(names = {"-i", "--input"}, required = true)
	private String source = "";

	@Option(names = {"-n", "--nrays"})
	private int nrays = 32;

	@Option(names = {"-m", "--maxdist"})
	private int maxdist = 2000;

	public static void main(final String... args) {

		new ImageJ();

		CommandLine.call(new StarDist(), args);
	}

	private static final double[][] rays(final int n) {

		final double[][] rays = new double[n][2];
		for (int i = 0; i < n; ++i) {
			final double phi = Math.PI * 2.0 / n * i;
			rays[i][0] = Math.cos(phi);
			rays[i][1] = Math.sin(phi);

			System.out.println(i + " : " + rays[i][0] + ", " + rays[i][1]);
		}
		return rays;
	}

	private static final <T extends Type<T>> int distance(
			final RealRandomAccess<T> src,
			final int maxD,
			final double[] ray) {

		final T ref = src.get().copy();
		int i = 0;
		do {
			src.move(ray);
			++i;
		} while (i <= maxD && src.get().valueEquals(ref));
		return i - 1;
	}

	private static final <T extends Type<T>> void starDist(
			final RealRandomAccessible<T> src,
			final RandomAccessibleInterval<UnsignedIntType> starDist,
			final double[][] rays,
			final int maxD) {

		final CompositeIntervalView<UnsignedIntType, ? extends GenericComposite<UnsignedIntType>> collapsed = Views.collapse(starDist);
		final Cursor<? extends GenericComposite<UnsignedIntType>> cursor = Views.iterable(collapsed).cursor();
		final RealRandomAccess<T> ra = src.realRandomAccess();
		final T ref = ra.get();
		final T background = ref.createVariable();

		while (cursor.hasNext()) {
			final GenericComposite<UnsignedIntType> dists = cursor.next();
			ra.setPosition(cursor);
			ra.get();
			if (!ref.valueEquals(background)) {
				for (int i = 0; i < rays.length; ++i) {
					ra.setPosition(cursor);
					dists.get(i).set(distance(ra, maxD, rays[i]));
				}
			}
		}
	}

	private static void fillStarDist(
			final Cursor<? extends Composite<UnsignedIntType>> starDist,
			final double[][] rays,
			final RandomAccessibleInterval<UnsignedLongType> target) {

		final UnsignedLongType one = new UnsignedLongType(1);

		final double[] x = new double[rays.length];
		final double[] y = new double[rays.length];
		final Composite<UnsignedIntType> dists = starDist.get();
		for (int i = 0; i < x.length; ++i) {
			final long d = dists.get(i).get();
			x[i] = starDist.getDoublePosition(0) + rays[i][0] * d;
			y[i] = starDist.getDoublePosition(1) + rays[i][1] * d;
		}
		final RealMaskRealInterval polygon = GeomMasks.closedPolygon2D(x, y);
		for (final UnsignedLongType t : Regions.sample(polygon, target))
			t.add(one);
	}

	private static void fillStarDists(
			final RandomAccessibleInterval<? extends Composite<UnsignedIntType>> starDists,
			final double[][] rays,
			final RandomAccessibleInterval<UnsignedLongType> target) {

		final Cursor<? extends Composite<UnsignedIntType>> cursor = Views.iterable(starDists).cursor();
		while (cursor.hasNext()) {
			cursor.fwd();
			fillStarDist(cursor, rays, target);
		}
	}

	@Override
	public Void call() throws Exception {

		final IHDF5Reader hdf5Reader = HDF5Factory.openForReading(source);
		final N5HDF5Reader n5 = new N5HDF5Reader(hdf5Reader, new int[]{64, 64});

		final String[] groups = n5.list("/");

		for (int i = 0; i < 10; ++i) {
			final String group = groups[i];
//		for (final String group : groups) {

			final RandomAccessibleInterval<UnsignedLongType> img = N5Utils.open(n5, group + "/mask");
	//		ImageJFunctions.show(img);

			final double[][] rays = rays(nrays);
			final RealRandomAccessible<UnsignedLongType> interpolant = Views.interpolate(Views.extendZero(img), new NearestNeighborInterpolatorFactory<>());
			final ArrayImg<UnsignedIntType, IntArray> starDists = ArrayImgs.unsignedInts(img.dimension(0), img.dimension(1), nrays);

			starDist(interpolant, starDists, rays, maxdist);

			ImageJFunctions.show(starDists);

			final ArrayImg<UnsignedLongType, LongArray> consensus = ArrayImgs.unsignedLongs(img.dimension(0), img.dimension(1));
			fillStarDists(Views.collapse(starDists), rays, consensus);

			ImageJFunctions.show(consensus);
		}

		return null;
	}

}
