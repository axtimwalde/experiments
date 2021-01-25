/**
 *
 */
package net.imglib2.algorithm.region.stardist;

import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.Localizable;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.position.transform.Round;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.Views;
import net.imglib2.view.composite.Composite;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class StarDists<T extends RealType<T>, S> implements RandomAccessible<StarDists<T, S>.StarDist> {

	private final double[][] rays;
	private final RandomAccessible<? extends Composite<T>> dists;
	private final RandomAccessible<S> source;
	protected final double maxDist;

	public StarDists(
			final double[][] rays,
			final RandomAccessible<T> distsFlat,
			final RandomAccessible<S> source,
			final double maxDist) {

		this.rays = rays;
		this.dists = Views.collapse(distsFlat);
		this.source = source;
		this.maxDist = maxDist;
	}

	@Override
	public int numDimensions() {

		return dists.numDimensions();
	}

	public class StarDist implements RandomAccess<StarDist>, IterableInterval<S> {

		public class Cursor implements net.imglib2.Cursor<S> {

			private final RandomAccess<S> randomAccess = source.randomAccess();
			private int ray = 0;
			private double rayDist = 0;
			private double dist = -1.0;
			private boolean hasNext = true;
			private final Round<RandomAccess<S>> round =
					new Round<RandomAccess<S>>(new RealPoint(numDimensions()), randomAccess);
			{
				reset();
			}

			@Override
			public double getDoublePosition(final int d) {

				return randomAccess.getDoublePosition(d);
			}

			@Override
			public int numDimensions() {

				return StarDists.this.numDimensions();
			}

			@Override
			public S get() {

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
				if (dist >= rayDist) {
					++ray;
					hasNext = ray < rays.length;
					if (hasNext) {
						rayDist = Math.min(maxDist, sourceAccess.get().get(ray).getRealDouble());
						if (ray + 1 == rays.length) rayDist -= 1; // so the last entry is not visited twice
						round.setPosition(StarDist.this);
						dist = 1.0;
						round.move(rays[ray]);
					} else round.move(rays[ray -1]);
				} else if (dist > 0) {
					round.move(rays[ray]);
				}
			}

			@Override
			public void reset() {

				round.setPosition(StarDist.this);
				ray = 0;
				rayDist = Math.min(maxDist, sourceAccess.get().get(ray).getRealDouble());
				dist = -1.0;
				hasNext = true;
			}

			@Override
			public boolean hasNext() {

				return hasNext;
			}

			@Override
			public S next() {

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

			public int getRay() {

				return ray;
			}

			public double getCurrentDist() {

				return dist;
			}
		}

		private final RandomAccess<? extends Composite<T>> sourceAccess;
		protected final double[] a;
		protected final double[] b;

		public StarDist() {

			sourceAccess = dists.randomAccess();
			a = new double[numDimensions()];
			b = new double[numDimensions()];
		}

		@Override
		public long getLongPosition(final int d) {

			return sourceAccess.getLongPosition(d);
		}

		@Override
		public void fwd(final int d) {

			sourceAccess.fwd(d);
		}

		@Override
		public void bck(final int d) {

			sourceAccess.bck(d);
		}

		@Override
		public void move(final int distance, final int d) {

			sourceAccess.move(distance, d);
		}

		@Override
		public void move(final long distance, final int d) {

			sourceAccess.move(distance, d);
		}

		@Override
		public void move(final Localizable distance) {

			sourceAccess.move(distance);
		}

		@Override
		public void move(final int[] distance) {

			sourceAccess.move(distance);
		}

		@Override
		public void move(final long[] distance) {

			sourceAccess.move(distance);
		}

		@Override
		public void setPosition(final Localizable position) {

			sourceAccess.setPosition(position);
		}

		@Override
		public void setPosition(final int[] position) {

			sourceAccess.setPosition(position);
		}

		@Override
		public void setPosition(final long[] position) {

			sourceAccess.setPosition(position);
		}

		@Override
		public void setPosition(final int position, final int d) {

			sourceAccess.setPosition(position, d);
		}

		@Override
		public void setPosition(final long position, final int d) {

			sourceAccess.setPosition(position, d);
		}

		@Override
		public StarDist get() {

			return this;
		}

		@Override
		public StarDist copy() {

			return new StarDist();
		}

		@Override
		public RandomAccess<StarDist> copyRandomAccess() {

			return copy();
		}

		@Override
		public S firstElement() {

			final Cursor cursor = new Cursor();
			cursor.fwd();
			return cursor.get();
		}

		@Override
		public Object iterationOrder() {

			return this; // iteration order is only compatible with ourselves
		}

		@Override
		public int numDimensions() {

			return StarDists.this.numDimensions();
		}

		@Override
		public Cursor iterator() {

			return cursor();
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

		public double getDist(final int i) {

			return sourceAccess.get().get(i).getRealDouble();
		}

		public double absoluteDiff(final StarDist other) {

			double sum = 0;
			for (int i = 0; i < rays.length; ++i)
				sum += Math.abs(getDist(i) - other.getDist(i));

			return sum;
		}

		public double avgAbsoluteDiff(final StarDist other) {

			final double diff = absoluteDiff(other);
			return diff / rays.length;
		}

		public double squareDiff(final StarDist other) {

			double sum = 0;
			for (int i = 0; i < rays.length; ++i) {
				final double diff = getDist(i) - other.getDist(i);
				sum += diff * diff;
			}

			return sum;
		}

		public double rayConsensus(
				final StarDist other,
				final int i,
				final double tolerance) {

			localize(a);
			other.localize(b);
			LinAlgHelpers.subtract(b, a, a);
			final double lengthA = LinAlgHelpers.length(a);
			if (lengthA > maxDist) {
				System.out.println("Testing two stardist that are too far from each other!");
				return 0.01;
			}
			final double referenceDist = Math.min(maxDist, getDist(i)) - lengthA;
			final double otherDist = Math.min(maxDist - lengthA, other.getDist(i));
			final double union = Math.max(referenceDist, otherDist);
			final double intersection = Math.min(referenceDist, otherDist);
//			if (union - intersection < tolerance)
//				return 1.0;
//			else
			if (union == 0)
				return 0.01;
			else
				return Math.max(0.01, intersection / union);
		}
	}

	@Override
	public StarDist randomAccess() {

		return new StarDist();
	}

	@Override
	public StarDist randomAccess(final Interval interval) {

		// TODO Auto-generated method stub
		return null;
	}
}
