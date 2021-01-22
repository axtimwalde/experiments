package org.janelia.saalfeldlab.fill;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.util.Caches;
import org.janelia.saalfeldlab.util.N5Factory;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.Behaviours;

import com.formdev.flatlaf.FlatDarkLaf;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.converter.Converters;
import net.imglib2.img.cell.CellImg;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeView;
import net.imglib2.view.composite.RealComposite;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "ball-fill-test")
public class BallConsensusFillTest implements Callable<Void> {

	@Option(names = {"-i", "--n5url"}, required = true, description = "N5 URL, e.g. '/home/saalfeld/tmp/jrc_hela-2.n5'")
	private String n5Url = null;

	@Option(names = {"-d", "--n5dataset"}, required = true, description = "N5 dataset, e.g. '/labels/er_pred/s0'")
	private String n5Dataset = null;

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

		new CommandLine(new BallConsensusFillTest()).execute(args);
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

	private static double atanh(final double x) {

		return 0.5 * Math.log((1.0 + x) / (1.0 - x));
	}

	/**
	 * Calculate the gradients of the dataset, cache them and show them in BDV.
	 *
	 * @param <T>
	 * @throws IOException
	 */
	private final <T extends NativeType<T> & RealType<T>> void run() throws IOException {

		/* make an N5 reader */
		final N5Reader n5 = N5Factory.openReader(n5Url);

		System.out.println(n5);

		/* open the dataset, use volatile access */
		final RandomAccessibleInterval<T> tanhDistances = N5Utils.openVolatile(n5, n5Dataset);

		/* show with BDV, wrapped as volatile */
		BdvStackSource<?> bdv = BdvFunctions.show(
				VolatileViews.wrapAsVolatile(tanhDistances),
				n5Dataset);
		bdv.setColor(new ARGBType(0xffff00ff));
		bdv.setDisplayRange(0, 255);


		/* convert to linear distances in px */
		final RandomAccessibleInterval<DoubleType> distances = Converters.convert(
				tanhDistances,
				(a, b) -> b.setReal(
						Math.min(4, 0.25 * 50.0 * atanh((Math.max(20, Math.min(234, a.getRealDouble())) - 127.0) / 128))),
				new DoubleType());

		final RandomAccessibleInterval<DoubleType> cachedDistances = Caches.cache(distances, new int[] {64, 64, 64});

//		bdv = BdvFunctions.show(
//				VolatileViews.wrapAsVolatile(
//						Caches.cache(distances, 64, 64, 64)),
//				n5Dataset + " linear distances",
//				BdvOptions.options().addTo(bdv));
//		bdv.setColor(new ARGBType(0xffffffff));
//		bdv.setDisplayRange(-200, 200);

		/* find max */
//		final T max = tanhDistances.randomAccess().get().createVariable();
//		final Point seed = new Point(tanhDistances.numDimensions());
//		final Cursor<T> c = Views.iterable(tanhDistances).cursor();
//		while (c.hasNext()) {
//			final T t = c.next();
//			if (t.compareTo(max) > 0) {
//				max.set(t);
//				seed.setPosition(c);
//				System.out.println(t.getRealDouble() + " @ " + Util.printCoordinates(c));
//			}
//		}
//		System.out.println("Seed " + max.getRealDouble() + " @ " + Util.printCoordinates(seed));


		/* ball fill */
		final CellImg<UnsignedIntType, ?> counts =
				new CellImgFactory<>(
						new UnsignedIntType(), 64, 64, 64).create(tanhDistances);
		bdv = BdvFunctions.show(
				counts,
				n5Dataset + " counts",
				BdvOptions.options().addTo(bdv));
		bdv.setColor(new ARGBType(0xff00ff00));
		bdv.setDisplayRange(0, 10);


		final CellImg<BitType, ?> stateFlat =
				new CellImgFactory<>(
						new BitType(), 64, 64, 64, 1).create(Intervals.addDimension(tanhDistances, 0, 1));
		final BitType borderBit = stateFlat.randomAccess().get().createVariable();
		borderBit.set(true);

		final CompositeView<BitType, RealComposite<BitType>> state =
				Views.collapseReal(Views.extendValue(stateFlat, borderBit), 2);

//		bdv = BdvFunctions.show(
//				Views.hyperSlice(stateFlat, 3, 0),
//				n5Dataset + " visited",
//				BdvOptions.options().addTo(bdv));
//		bdv.setColor(new ARGBType(0xffffffff));
//		bdv.setDisplayRange(0, 1);

		/* fill worker */
		final ExecutorService exec = Executors.newCachedThreadPool();
		final UnsignedIntType borderCount = counts.randomAccess().get().createVariable();
		borderCount.set(1);
		final BallConsensusFill<?, ?, ?> fill = new BallConsensusFill<>(
				Views.extendZero(cachedDistances),
				Views.extendValue(counts, borderCount),
				state,
				new DiamondShape(1),
				0.5,
				50);

		/* add fill behavior */
		final Behaviours behaviours = new Behaviours(new InputTriggerConfig());
		behaviours.install(bdv.getBdvHandle().getTriggerbindings(), "fill");
		behaviours.behaviour(
				new SeedBehavior(
						bdv.getBdvHandle().getViewerPanel(),
						new Translation3D(),
						fill,
						exec),
				"fill",
				"SPACE button1");

		/* add save action */
		final Actions actions = new Actions(new InputTriggerConfig());
		actions.install(bdv.getBdvHandle().getKeybindings(), "fill");

		actions.runnableAction(() -> {

					try {
						final ExecutorService saveExec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
						System.out.println( "Saving counts to '" + n5Dataset + "-ball-mask'...");
						N5Utils.save(
								counts,
								N5Factory.openWriter(n5Url),
								n5Dataset + "-ball-mask",
								new int[] {128, 128, 128},
								new GzipCompression(),
								saveExec);
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
					finalBdv.getBdvHandle().getViewerPanel().requestRepaint();
					try {
						Thread.sleep(1000/10);
					} catch (final InterruptedException e) {
						break;
					}
				}
		}).start();

//
	}
}
