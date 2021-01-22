/*
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2009 - 2016 Tobias Pietzsch, Stephan Preibisch, Stephan Saalfeld,
 * John Bogovic, Albert Cardona, Barry DeZonia, Christian Dietz, Jan Funke,
 * Aivar Grislis, Jonathan Hale, Grant Harris, Stefan Helfrich, Mark Hiner,
 * Martin Horn, Steffen Jaensch, Lee Kamentsky, Larry Lindsey, Melissa Linkert,
 * Mark Longair, Brian Northan, Nick Perry, Curtis Rueden, Johannes Schindelin,
 * Jean-Yves Tinevez and Michael Zinsmaier.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.janelia.saalfeldlab.fill;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import net.imglib2.Cursor;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.algorithm.region.hypersphere.HyperSphere;
import net.imglib2.roi.Regions;
import net.imglib2.roi.geom.GeomMasks;
import net.imglib2.roi.geom.real.WritableSphere;
import net.imglib2.type.BooleanType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.composite.RealComposite;

/**
 *
 * @author Stephan Saalfeld
 */
public class BallConsensusFill<T extends RealType<T>, U extends IntegerType<U>, B extends BooleanType<B>> {

	// int or long? current TLongList cannot store more than Integer.MAX_VALUE
	private static final int CLEANUP_THRESHOLD = (int)1e5;

	private final RandomAccessible<T> source;
	private final RandomAccessible<U> counts;
	private final RandomAccessible<RealComposite<B>> state; // 0 - visited, 1 - enqueued
	private final Shape shape;
	private final double minOverlap;
	private final int skipCount;

	/**
	 * @param source
	 *            signed pixel distances
	 * @param counts
	 *            write fill counts into this
	 * @param state
	 *            tracking visited locations
	 * @param shape
	 *            the shape for extending the current location
	 */
	public BallConsensusFill(
			final RandomAccessible<T> source,
			final RandomAccessible<U> counts,
			final RandomAccessible<RealComposite<B>> state,
			final Shape shape,
			final double minOverlap,
			final int skipCount) {

		this.source = source;
		this.counts = counts;
		this.state = state;
		this.shape = shape;
		this.minOverlap = minOverlap;
		this.skipCount = skipCount;
	}


	private static <T extends IntegerType<T>> double sampleBallCounts(
			final RandomAccessible<T> counts,
			final double[] position,
			final double radius) {

		final WritableSphere sphere = GeomMasks.closedSphere(position, radius);
		long m = 0, c = 0;
		for (final T t : Regions.sample(sphere, counts)) {
			if (t.getIntegerLong() > 0)
				++c;
			++m;
		}
		return (double)c / m;
	}

	private static <T extends IntegerType<T>> double sampleBallCounts2(
			final RandomAccessible<T> counts,
			final Localizable position,
			final long radius) {

		final HyperSphere<T> sphere = new HyperSphere<>(counts, position, radius);
		long m = 0, c = 0;
		for (final T t : sphere) {
			if (t.getIntegerLong() > 0)
				++c;
			++m;
		}
		return (double)c / m;
	}

	private static <T extends IntegerType<T>> void countUpBall(
			final RandomAccessible<T> counts,
			final double[] position,
			final double radius) {

		final WritableSphere sphere = GeomMasks.closedSphere(position, radius);
		for (final T t : Regions.sample(sphere, counts))
			t.inc();
	}

	private static <T extends IntegerType<T>> void countUpBall2(
			final RandomAccessible<T> counts,
			final Localizable position,
			final long radius) {

		final HyperSphere<T> sphere = new HyperSphere<>(counts, position, radius);
		for (final T t : sphere)
			t.inc();
	}

	/**
	 *
	 * Iterative n-dimensional flood fill for arbitrary neighborhoods: Starting
	 * at seed location, write fillLabel into target at current location and
	 * continue for each pixel in neighborhood defined by shape if neighborhood
	 * pixel is in the same connected component and fillLabel has not been
	 * written into that location yet.
	 *
	 * @param seed
	 *            Start flood fill at this location.
	 * @param shape
	 *            Defines neighborhood that is considered for connected
	 *            components, e.g.
	 *            {@link net.imglib2.algorithm.neighborhood.DiamondShape}
	 * @param filter
	 *            Returns true if pixel has not been visited yet and should be
	 *            written into. Returns false if target pixel has been visited
	 *            or source pixel is not part of the same connected component.
	 * @param writer
	 *            Defines how fill label is written into target at current
	 *            location.
	 * @param <T>
	 *            input pixel type
	 * @param <U>
	 *            fill label type
	 */
	public void fill(final Localizable seed) {

		final int n = source.numDimensions();

		TLongList coordinates = new TLongArrayList();
		for (int d = 0; d < n; ++d) {
			coordinates.add(seed.getLongPosition(d));
		}

		final int cleanupThreshold = n * CLEANUP_THRESHOLD;

		final RandomAccessible<Neighborhood<RealComposite<B>>> neighborhoodVisited = shape.neighborhoodsRandomAccessible(state);
		final RandomAccess<Neighborhood<RealComposite<B>>> neighborhoodVisitedAccess = neighborhoodVisited.randomAccess();
		final RandomAccess<T> sourceAccess = source.randomAccess();
		final RandomAccess<U> countsAccess = counts.randomAccess();
		final RandomAccess<RealComposite<B>> stateAccess = state.randomAccess();
		neighborhoodVisitedAccess.setPosition(seed);
		sourceAccess.setPosition(seed);

		final double[] position = seed.positionAsDoubleArray();
		final long[] longPosition = seed.positionAsLongArray();
		final Point positionPoint = Point.wrap(longPosition);

		// countUpBall( counts, position, sourceAccess.get().getRealDouble() );
		countUpBall2(counts, positionPoint, Math.round(sourceAccess.get().getRealDouble()));

		for (int i = 0; i < coordinates.size(); i += n) {
			for (int d = 0; d < n; ++d)
				position[d] = longPosition[d] = coordinates.get(i + d);

			stateAccess.setPosition(longPosition);
			stateAccess.get().get(0).set(true);

			boolean enqueueNext = false;
			countsAccess.setPosition(longPosition);
			final long longRadius;
			if (countsAccess.get().getIntegerLong() > skipCount) {
				enqueueNext = true;
				longRadius = 0;
			} else {
				sourceAccess.setPosition(longPosition);
				final double radius = sourceAccess.get().getRealDouble();
				if (radius <= 0) continue;

				longRadius = Math.round(radius);
			}

//			if (!enqueueNext && sampleBallCounts(counts, position, radius) > 0.5) {
			if (!enqueueNext && sampleBallCounts2(counts, positionPoint, longRadius) > minOverlap) {
//				countUpBall(counts, position, radius);
				countUpBall2(counts, positionPoint, longRadius);
				enqueueNext = true;
			}

			if (enqueueNext) {

				neighborhoodVisitedAccess.setPosition(longPosition);
				final Cursor<RealComposite<B>> neighborhoodVisitedCursor = neighborhoodVisitedAccess.get().cursor();

				while (neighborhoodVisitedCursor.hasNext()) {
					final B enqueued = neighborhoodVisitedCursor.next().get(1);
					if (!enqueued.get()) {
						enqueued.set(true);
						for (int d = 0; d < n; ++d)
							coordinates.add(neighborhoodVisitedCursor.getLongPosition(d));
					}
				}

				if (i > cleanupThreshold) {
					// TODO should it start from i + n?
					coordinates = coordinates.subList(i, coordinates.size());
					i = 0;
				}
			}
		}
	}
}
