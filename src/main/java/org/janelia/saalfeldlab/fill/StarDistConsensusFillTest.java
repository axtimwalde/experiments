package org.janelia.saalfeldlab.fill;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.util.N5Factory;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.Behaviours;

import com.formdev.flatlaf.FlatDarkLaf;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.region.stardist.StarDists;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "stardist-fill-test")
public class StarDistConsensusFillTest implements Callable<Void> {

	@Option(names = {"-i", "--n5url"}, required = true, description = "N5 URL, e.g. '/home/saalfeld/projects/stardist/3d_cell_segmentaion/stardist_torch.hdf5'")
	private String n5Url = null;

	@Option(names = {"-j", "--n5image"}, required = true, description = "N5 image dataset, e.g. '/valid_img_0'")
	private String n5Image = null;

	@Option(names = {"-d", "--n5dists"}, required = true, description = "N5 stardists dataset, e.g. '/dist_out_0'")
	private String n5Dists = null;

	@Option(names = {"-r", "--n5rays"}, required = true, description = "N5 rays dataset, e.g. '/verts'")
	private String n5Rays = null;

	@Option(names = {"-s", "--distScale"}, required = false, description = "StarDist scale, e.g. 1.0")
	private double distScale = 1.0;


	/**
	 * Start the tool. We ignore the exit code returned by
	 * {@link CommandLine#execute(String...)} but this can be useful in other
	 * applications.
	 *
	 * @param args
	 */
	public static void main(final String... args) {

		try {
			UIManager.setLookAndFeel(new FlatDarkLaf());
		} catch (final UnsupportedLookAndFeelException e) {}

		new CommandLine(new StarDistConsensusFillTest()).execute(args);
	}

	/**
	 * The real implementation. We use {@link Callable Callable<Void>} instead
	 * of {@link Runnable} because {@link Runnable#run()} cannot throw
	 * {@link Exception Exceptions}.
	 *
	 * Since we would like to use some type parameters, we have to delegate to a
	 * method that was not declared in an interface without such parameters.
	 *
	 * @throws Exception
	 */
	@Override
	public Void call() throws Exception {

		run();
		return null;
	}

	private static <T extends NativeType<T> & RealType<T>> double[][] rays(
			final N5Reader n5,
			final String dataset) throws IOException {

		final RandomAccessibleInterval<T> img = N5Utils.open(n5, dataset);
		final double[][] rays = new double[(int)img.dimension(1)][(int)img.dimension(0)];
		final Cursor<T> cursor = Views.flatIterable(img).cursor();
		while (cursor.hasNext()) {
			final T t = cursor.next();
			rays[cursor.getIntPosition(1)][rays[0].length - 1 - cursor.getIntPosition(0)] = t.getRealDouble();
		}

		return rays;
	}

	private static final RandomAccessibleInterval<UnsignedIntType> createCounts(final Interval interval) {

		final long[] dimensions = interval.dimensionsAsLongArray();
		final int[] cellDimensions = new int[dimensions.length];
		Arrays.fill(cellDimensions, 64);

		final DiskCachedCellImgOptions factoryOptions = DiskCachedCellImgOptions.options()
				.cellDimensions(cellDimensions);
//				.cacheType(CacheType.BOUNDED)
//				.maxCacheSize(100);

		final DiskCachedCellImgFactory<UnsignedIntType> factory =
				new DiskCachedCellImgFactory<>(new UnsignedIntType(), factoryOptions);

		return factory.create(dimensions);
	}

	private static final RandomAccessibleInterval<BitType> createStates(final Interval interval) {

		final long[] dimensions = new long[interval.numDimensions() + 1];
		interval.dimensions(dimensions);
		dimensions[dimensions.length - 1] = 2;

		final int[] cellDimensions = new int[dimensions.length];
		Arrays.fill(cellDimensions, 64);
		cellDimensions[cellDimensions.length - 1] = 2;

		final DiskCachedCellImgOptions factoryOptions = DiskCachedCellImgOptions.options()
				.cellDimensions(cellDimensions);
//				.cacheType(CacheType.BOUNDED)
//				.maxCacheSize(100);

		final DiskCachedCellImgFactory<BitType> factory =
				new DiskCachedCellImgFactory<>(new BitType(), factoryOptions);

		return factory.create(dimensions);
	}

	/**
	 * Calculate the gradients of the dataset, cache them and show them in BDV.
	 *
	 * @param <T>
	 * @throws IOException
	 */
	private final <
			T extends NativeType<T> & RealType<T>,
			P extends NativeType<P> & RealType<P>> void run() throws IOException {

		/* make an N5 reader */
		final N5Reader n5 = N5Factory.openReader(n5Url);

		System.out.println(n5);

		final BdvOptions options = BdvOptions.options().screenScales(new double[]{0.5});

		/* open and show image */
		final RandomAccessibleInterval<P> img = N5Utils.openVolatile(n5, n5Image);
		final BdvStackSource<?> bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(img),
				n5Image,
				options);
		bdv.setColor(new ARGBType(0xffffffff));
		bdv.setDisplayRange(0, 2);

		/* open rays */
		final double[][] rays = rays(n5, n5Rays);
		for (final double[] ray : rays)
			System.out.println(LinAlgHelpers.length(ray));

		/* open stardists */
		RandomAccessibleInterval<T> distsFlat = N5Utils.open(n5, n5Dists);
		distsFlat = Converters.convert(
				distsFlat,
				(a, b) -> b.setReal(Math.max(0, a.getRealDouble() * distScale)),
				distsFlat.randomAccess().get().createVariable());
		final ExtendedRandomAccessibleInterval<T, RandomAccessibleInterval<T>> distsFlatExtended = Views.extendZero(distsFlat);
		RandomAccessible<T> distsFlatPermuted = distsFlatExtended;
		for (int d = distsFlat.numDimensions() - 1; d > 0; --d)
			distsFlatPermuted = Views.permute(distsFlatPermuted, 0, d);

		final RandomAccessible<T> finalDistsFlatPermuted = distsFlatPermuted;

		/* add fill behavior */
		final ExecutorService exec = Executors.newCachedThreadPool();
		final Behaviours behaviours = new Behaviours(new InputTriggerConfig());
		behaviours.install(bdv.getBdvHandle().getTriggerbindings(), "fill");
		behaviours.behaviour(
				new SeedBehavior(
						bdv.getBdvHandle(),
						new Translation3D(),
						() -> createCounts(img),
						() -> createStates(img),
						(counts, state) -> new StarDistConsensusFill<>(
								new StarDists<>(
										rays,
										finalDistsFlatPermuted,
										Views.pair(counts, state),
										20),
								0.75,
								1,
								10),
						exec),
				"fill",
				"SPACE button1");

		/* add save action */
		final Actions actions = new Actions(new InputTriggerConfig());
		actions.install(bdv.getBdvHandle().getKeybindings(), "fill");

		final BdvHandle bdvHandle = bdv.getBdvHandle();

		actions.runnableAction(() -> {

					try {
						final ExecutorService saveExec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
						System.out.println( "Saving counts to '" + n5Image + "-stardist-mask'...");
//						N5Utils.save(
//								bdv,
//								N5Factory.openWriter(n5Url),
//								n5Image + "-stardist-mask",
//								new int[] {128, 128, 128},
//								new GzipCompression(),
//								saveExec);
						saveExec.shutdown();
						saveExec.awaitTermination(1, TimeUnit.DAYS);
						System.out.println( "... Done!");
					} catch (final Exception e) {
						System.err.println( "Saving failed with " + e.getMessage());
					}
				},
				"save",
				"S");


		/* animate */
		final BdvStackSource<?> finalBdv = bdv;
		new Thread(() -> {

				while (true) {
					final int activeThreads = exec instanceof ThreadPoolExecutor ? ((ThreadPoolExecutor)exec).getActiveCount() : 1;
					final int sleepTime = activeThreads > 0 ? 1000/10 : 1000/5;
					if (activeThreads > 0)
						finalBdv.getBdvHandle().getViewerPanel().requestRepaint();

					try {
						Thread.sleep(sleepTime);
					} catch (final InterruptedException e) {
						break;
					}
				}
		}).start();

//
	}
}
