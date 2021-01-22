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

package net.imglib2.algorithm.region.stardist;

import java.util.Iterator;

import net.imglib2.IterableInterval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.position.transform.Round;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.composite.Composite;

/**
 *
 * @author Stephan Saalfeld
 */
public class StarDist<T> extends Point implements IterableInterval<T> {

	public class Cursor implements net.imglib2.Cursor<T> {

		private final RandomAccess<T> randomAccess = source.randomAccess();
		private int ray = 0;
		private double dist = -1.0;
		private boolean hasNext = true;
		private final Round<RandomAccess<T>> round =
				new Round<RandomAccess<T>>(new RealPoint(numDimensions), randomAccess);
		{
			reset();
		}



		@Override
		public double getDoublePosition(final int d) {

			return randomAccess.getDoublePosition(d);
		}

		@Override
		public int numDimensions() {

			return numDimensions;
		}

		@Override
		public T get() {

			return randomAccess.get();
		}

		@Override
		public Cursor copy() {

			final Cursor copy = new Cursor();
			copy.round.setPosition(round);
			copy.ray = ray;
			copy.dist = dist;
			copy.hasNext = hasNext;
			return copy;
		}

		@Override
		public void jumpFwd(final long steps) {

			for (int s = 0; s < steps; ++s)
				fwd();
		}

		@Override
		public void fwd() {

			dist += 1.0;
			if (dist > dists[ray]) {
				++ray;
				hasNext = ray < dists.length;
				if (hasNext) {
					round.setPosition(position);
					dist = 1.0;
					round.move(rays[ray]);
				} else round.move(rays[ray -1]);
			} else if (dist > 0) {
				round.move(rays[ray]);
			}
		}

		@Override
		public void reset() {

			round.setPosition(position);
			ray = 0;
			dist = -1.0;
			hasNext = true;
		}

		@Override
		public boolean hasNext() {

			return hasNext;
		}

		@Override
		public T next() {

			fwd();
			return get();
		}

		@Override
		public long getLongPosition(final int d) {

			return randomAccess.getLongPosition(d);
		}

		@Override
		public Cursor copyCursor() {

			return copy();
		}
	}


	protected final int numDimensions;
	protected final double[][] rays;
	protected final double[] dists;
	protected final RandomAccessible<T> source;
	protected final double maxDist;
	protected final double[] a;
	protected final double[] b;

	public StarDist(
			final RandomAccessible<T> source,
			final long[] position,
			final double[][] rays,
			final double[] dists,
			final double maxDist) {

		super(position);
		this.numDimensions = source.numDimensions();
		this.source = source;
		this.rays = rays;
		this.dists = dists;
		this.dists[dists.length - 1] -= 1.0; // < trick to make hasNext test simpler
		this.maxDist = maxDist;
		a = new double[numDimensions];
		b = new double[numDimensions];
	}

	public StarDist(
			final RandomAccessible< T > source,
			final Localizable position,
			final double[][] rays,
			final double[] dists,
			final double maxDist) {

		this(source, position.positionAsLongArray(), rays, dists, maxDist);
	}

	@Override
	public long size() {

		throw new UnsupportedOperationException(
				"It is certainly possible to estimate the number elements "
				+ "in a StarDist but I didn't see the point implementing "
				+ "this.  At least the naive way is equivalent to iterating "
				+ "it fully which defeats the purpose.");
	}

	@Override
	public T firstElement() {

		final StarDist<T>.Cursor cursor = new Cursor();
		cursor.fwd();
		return cursor.get();
	}

	@Override
	public Object iterationOrder() {

		return this; // iteration order is only compatible with ourselves
	}

	@Override
	public double realMin(final int d) {

		throw new UnsupportedOperationException(
				"It is certainly possible to estimate the bounds by looping"
				+ "over all rays of the StarDist but I didn't see the point"
				+ "implementing it.");
	}

	@Override
	public void realMin(final double[] min) {

		throw new UnsupportedOperationException(
				"It is certainly possible to estimate the bounds by looping"
				+ "over all rays of the StarDist but I didn't see the point"
				+ "implementing it.");
	}

	@Override
	public void realMin(final RealPositionable min) {

		throw new UnsupportedOperationException(
				"It is certainly possible to estimate the bounds by looping"
				+ "over all rays of the StarDist but I didn't see the point"
				+ "implementing it.");
	}

	@Override
	public double realMax(final int d) {

		throw new UnsupportedOperationException(
				"It is certainly possible to estimate the bounds by looping"
				+ "over all rays of the StarDist but I didn't see the point"
				+ "implementing it.");
	}

	@Override
	public void realMax(final double[] max) {

		throw new UnsupportedOperationException(
				"It is certainly possible to estimate the bounds by looping"
				+ "over all rays of the StarDist but I didn't see the point"
				+ "implementing it.");
	}

	@Override
	public void realMax(final RealPositionable max) {

		throw new UnsupportedOperationException(
				"It is certainly possible to estimate the bounds by looping"
				+ "over all rays of the StarDist but I didn't see the point"
				+ "implementing it.");
	}

	@Override
	public int numDimensions() {

		return numDimensions;
	}

	@Override
	public Iterator<T> iterator() {

		return cursor();
	}

	@Override
	public long min(final int d) {

		throw new UnsupportedOperationException(
				"It is certainly possible to estimate the bounds by looping"
				+ "over all rays of the StarDist but I didn't see the point"
				+ "implementing it.");
	}

	@Override
	public void min(final long[] min) {

		throw new UnsupportedOperationException(
				"It is certainly possible to estimate the bounds by looping"
				+ "over all rays of the StarDist but I didn't see the point"
				+ "implementing it.");
	}

	@Override
	public void min(final Positionable min) {

		throw new UnsupportedOperationException(
				"It is certainly possible to estimate the bounds by looping"
				+ "over all rays of the StarDist but I didn't see the point"
				+ "implementing it.");
	}

	@Override
	public long max(final int d) {

		throw new UnsupportedOperationException(
				"It is certainly possible to estimate the bounds by looping"
				+ "over all rays of the StarDist but I didn't see the point"
				+ "implementing it.");
	}

	@Override
	public void max(final long[] max) {

		throw new UnsupportedOperationException(
				"It is certainly possible to estimate the bounds by looping"
				+ "over all rays of the StarDist but I didn't see the point"
				+ "implementing it.");
	}

	@Override
	public void max(final Positionable max) {

		throw new UnsupportedOperationException(
				"It is certainly possible to estimate the bounds by looping"
				+ "over all rays of the StarDist but I didn't see the point"
				+ "implementing it.");
	}

	@Override
	public void dimensions(final long[] dimensions) {

		throw new UnsupportedOperationException(
				"It is certainly possible to estimate the bounds by looping"
				+ "over all rays of the StarDist but I didn't see the point"
				+ "implementing it.");
	}

	@Override
	public long dimension(final int d) {

		throw new UnsupportedOperationException(
				"It is certainly possible to estimate the bounds by looping"
				+ "over all rays of the StarDist but I didn't see the point"
				+ "implementing it.");
	}

	@Override
	public Cursor cursor() {

		return new Cursor();
	}

	@Override
	public Cursor localizingCursor() {

		return cursor();
	}

	public void setDist(final double dist, final int i) {

		dists[i] = dist;
	}

	public <R extends RealType<R>, C extends Composite<R>> void setDists(final C composite) {

		for (int i = 0; i < dists.length; ++i)
			setDist(composite.get(i).getRealDouble(), i);
	}

	public double absoluteDiff(final StarDist<?> other) {

		final double[] otherDists = other.dists;
		double sum = 0;
		for (int i = 0; i < dists.length; ++i)
			sum += Math.abs(dists[i] - otherDists[i]);

		return sum;
	}

	public double avgAbsoluteDiff(final StarDist<?> other) {

		final double diff = absoluteDiff(other);
		return diff / dists.length;
	}

	public double squareDiff(final StarDist<?> other) {

		final double[] otherDists = other.dists;
		double sum = 0;
		for (int i = 0; i < dists.length; ++i) {
			final double diff = dists[i] - otherDists[i];
			sum += diff * diff;
		}

		return sum;
	}

	public double rayConsensus(
			final StarDist<?> other,
			final int i) {

		localize(a);
		other.localize(b);
		LinAlgHelpers.subtract(b, a, a);
		final double diff =
				Math.abs(Math.min(maxDist, dists[i]) - Math.min(maxDist - LinAlgHelpers.length(a), other.dists[i]));

		return 1.0 - diff / dists[i];
	}
}
