/**
 *
 */
package org.janelia.saalfeldlab.fill;

import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.util.ARGBStream;
import org.janelia.saalfeldlab.util.GoldenAngleSaturatedARGBStream;
import org.scijava.ui.behaviour.ClickBehaviour;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.position.transform.Round;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeView;
import net.imglib2.view.composite.RealComposite;

/**
 * @author Stephan Saalfeld
 *
 */
public class SeedBehavior implements ClickBehaviour {

	private final BdvHandle bdv;
	private final RealTransform sourceTransform;
	private final Point seed = new Point(3);
	private final Round<Point> round = new Round<Point>(new RealPoint(3), seed);
	private final BiFunction<RandomAccessible<UnsignedIntType>, RandomAccessible<RealComposite<BitType>>, Consumer<Localizable>> fillSupplier;
	private final Supplier<RandomAccessibleInterval<UnsignedIntType>> countsSupplier;
	private final Supplier<RandomAccessibleInterval<BitType>> statesSupplier;
	private final ExecutorService exec;
	private final ARGBStream argbStream = new GoldenAngleSaturatedARGBStream();
	private int colorId = 0;
	private Hashtable<
			BdvStackSource<UnsignedIntType>,
			Pair<
					RandomAccessibleInterval<UnsignedIntType>,
					RandomAccessibleInterval<BitType>>> labels = new Hashtable<>();

	public SeedBehavior(
			final BdvHandle bdv,
			final RealTransform sourceTransform,
			final Supplier<RandomAccessibleInterval<UnsignedIntType>> countsSupplier,
			final Supplier<RandomAccessibleInterval<BitType>> statesSupplier,
			final BiFunction<RandomAccessible<UnsignedIntType>, RandomAccessible<RealComposite<BitType>>, Consumer<Localizable>> fillSupplier,
			final ExecutorService exec) {

		this.bdv = bdv;
		this.sourceTransform = sourceTransform;
		this.countsSupplier = countsSupplier;
		this.statesSupplier = statesSupplier;
		this.fillSupplier = fillSupplier;
		this.exec = exec;
	}

	@Override
	public void click(final int x, final int y) {

		final ViewerPanel viewer = bdv.getViewerPanel();
		final Source<?> spimSource = viewer.state().getCurrentSource().getSpimSource();

		BdvStackSource<UnsignedIntType> currentLabel = null;
		for (final BdvStackSource<UnsignedIntType> label : labels.keySet()) {
			if (label.isCurrent()) {
				currentLabel = label;
				break;
			}
		}

		final RandomAccessibleInterval<UnsignedIntType> counts;
		final RandomAccessibleInterval<BitType> stateFlat;
		if (currentLabel == null) {
			counts = countsSupplier.get();
			stateFlat = statesSupplier.get();

			/* add sources */
			final BdvStackSource<UnsignedIntType> stackSource = BdvFunctions.show(
					counts,
					"counts-" + colorId,
					BdvOptions.options().addTo(bdv));
			stackSource.setColor(new ARGBType(argbStream.argb(colorId++)));
			stackSource.setDisplayRange(0, 64);

			labels.put(stackSource, new ValuePair<>(counts, stateFlat));
		} else {
			final Pair<RandomAccessibleInterval<UnsignedIntType>, RandomAccessibleInterval<BitType>> pair =
					labels.get(currentLabel);
			counts = pair.getA();
			stateFlat = pair.getB();
		}

		final BitType borderBit = stateFlat.randomAccess().get().createVariable();
		borderBit.set(true);
		final CompositeView<BitType, RealComposite<BitType>> state =
				Views.collapseReal(Views.extendValue(stateFlat, borderBit), 2);

		viewer.displayToGlobalCoordinates(x, y, round);
		sourceTransform.apply(round, round);

		exec.submit(() -> fillSupplier.apply(Views.extendZero(counts), state).accept(seed));
	}
}
