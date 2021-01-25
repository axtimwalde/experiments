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

import java.util.function.Consumer;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import net.imglib2.Localizable;
import net.imglib2.algorithm.region.stardist.StarDists;
import net.imglib2.type.BooleanType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.view.composite.RealComposite;

/**
 *
 * @author Stephan Saalfeld
 */
public class StarDistConsensusFill<T extends RealType<T>, U extends IntegerType<U>, B extends BooleanType<B>> implements Consumer<Localizable> {

	// int or long? current TLongList cannot store more than Integer.MAX_VALUE
	private static final int CLEANUP_THRESHOLD = (int)1e5;

	private final StarDists<T, Pair<U, RealComposite<B>>> source;
	private final double minConsensus;
	private final double maxConsensusTolerance;
	private final double maxEnqueueRadius;
	private TLongList coordinates = new TLongArrayList();
	private final int n;

	/**
	 * @param source
	 *            stardist regions over counts and states
	 * @param shape
	 *            the shape for extending the current location
	 */
	public StarDistConsensusFill(
			final StarDists<T, Pair<U, RealComposite<B>>> source,
			final double minConsensus,
			final double maxConsensusTolerance,
			final double maxEnqueueRadius) {

		this.source = source;
		this.minConsensus = minConsensus;
		this.maxConsensusTolerance = maxConsensusTolerance;
		this.maxEnqueueRadius = maxEnqueueRadius;
		n = source.numDimensions();
	}

	private double consensus(
			final StarDists<T, Pair<U, RealComposite<B>>>.StarDist reference,
			final StarDists<T, Pair<U, RealComposite<B>>>.StarDist other) {

		final StarDists<T, Pair<U, RealComposite<B>>>.StarDist.Cursor cursor = reference.cursor();
		cursor.fwd(); // skip center
		double consensus = 1.0;
		int c = 0;
		while (cursor.hasNext()) {
			final Pair<U, RealComposite<B>> pair = cursor.next();
			if (pair.getA().getInteger() > 0) {
				other.setPosition(cursor);
				consensus *= reference.rayConsensus(other, cursor.getRay(), maxConsensusTolerance);
				++c;
			}
		}
		return c > 0 ? Math.pow(consensus, 1.0 / c) : 0;
	}

	private void update(
			final StarDists<T, Pair<U, RealComposite<B>>>.StarDist reference,
			final StarDists<T, Pair<U, RealComposite<B>>>.StarDist other) {

		final StarDists<T, Pair<U, RealComposite<B>>>.StarDist.Cursor cursor = reference.cursor();
		final Pair<U, RealComposite<B>> firstPair = cursor.next();
		firstPair.getA().inc();
		firstPair.getB().get(0).set(true);
		while (cursor.hasNext()) {

			final Pair<U, RealComposite<B>> pair = cursor.next();
			if (cursor.getCurrentDist() < maxEnqueueRadius) {
				final RealComposite<B> state = pair.getB();
				final B visited = state.get(0);
				final B enqueued = state.get(1);
				other.setPosition(cursor);
				final boolean consistent = reference.rayConsensus(other, cursor.getRay(), maxConsensusTolerance) > minConsensus;
				if (consistent) {
					pair.getA().inc();
					if (!(enqueued.get() || visited.get())) {
						for (int d = 0; d < n; ++d)
							coordinates.add(cursor.getLongPosition(d));
					}
					enqueued.set(true);
				}
			}
		}
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
	@Override
	public void accept(final Localizable seed) {

		coordinates.clear();

		final int cleanupThreshold = n * CLEANUP_THRESHOLD;

		final StarDists<T, Pair<U, RealComposite<B>>>.StarDist reference = source.randomAccess();
		final StarDists<T, Pair<U, RealComposite<B>>>.StarDist other = source.randomAccess();

		reference.setPosition(seed);
		update(reference, other);

		final long[] position = new long[n];

		for (int i = 0; i < coordinates.size(); i += n) {
//		for (int i = 0; i < coordinates.size() && i < 3; i += n) {
			for (int d = 0; d < n; ++d)
				position[d] = coordinates.get(i + d);

			reference.setPosition(position);
			reference.cursor().next().getB().get(0).set(true);

			final double consensus = consensus(reference, other);
//			System.out.println(consensus);
			if (consensus > minConsensus) {
				update(reference, other);
				if (i > cleanupThreshold) {
					coordinates = coordinates.subList(i, coordinates.size());
					i = -n;
				}
			}
		}
	}
}
