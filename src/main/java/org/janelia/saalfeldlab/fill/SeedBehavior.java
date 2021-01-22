/**
 *
 */
package org.janelia.saalfeldlab.fill;

import java.util.concurrent.ExecutorService;

import org.scijava.ui.behaviour.ClickBehaviour;

import bdv.viewer.ViewerPanel;
import net.imglib2.Point;
import net.imglib2.RealPoint;
import net.imglib2.position.transform.Round;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.util.Util;

/**
 * @author Stephan Saalfeld
 *
 */
public class SeedBehavior implements ClickBehaviour {

	private final ViewerPanel bdv;
	private final RealTransform sourceTransform;
	private final Point seed = new Point(3);
	private final Round<Point> round = new Round<Point>(new RealPoint(3), seed);
	private final BallConsensusFill<?, ?, ?> fill;
	private final ExecutorService exec;

	public SeedBehavior(
			final ViewerPanel bdv,
			final RealTransform sourceTransform,
			final BallConsensusFill<?, ?, ?> fill,
			final ExecutorService exec) {

		this.bdv = bdv;
		this.sourceTransform = sourceTransform;
		this.fill = fill;
		this.exec = exec;
	}

	@Override
	public void click(final int x, final int y) {

		bdv.displayToGlobalCoordinates(x, y, round);
		sourceTransform.apply(round, round);
		exec.submit(() -> fill.fill(seed));
		System.out.println("global coordinates: " + Util.printCoordinates(seed));
	}
}
