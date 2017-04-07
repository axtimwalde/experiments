/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.janelia.saalfeldlab;

import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.type.numeric.integer.LongType;

/**
 * A {@link RandomAccessible} over the <em>d</em>-th position of discrete
 * coordinate space.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class PositionRandomAccessible implements RandomAccessible< LongType >
{
	private final int n;
	private final int d;

	public PositionRandomAccessible( final int numDimensions, final int d )
	{
		this.n = numDimensions;
		this.d = d;
	}

	public class PositionRandomAccess extends Point implements RandomAccess< LongType >
	{
		private final LongType t = new LongType();

		public PositionRandomAccess()
		{
			super( PositionRandomAccessible.this.n );
		}

		@Override
		public LongType get()
		{
			t.set( position[ d ] );
			return t;
		}

		@Override
		public PositionRandomAccess copy()
		{
			return new PositionRandomAccess();
		}

		@Override
		public RandomAccess< LongType > copyRandomAccess()
		{
			return copy();
		}


	}

	@Override
	public int numDimensions()
	{
		return n;
	}

	@Override
	public RandomAccess< LongType > randomAccess()
	{
		return new PositionRandomAccess();
	}

	@Override
	public RandomAccess< LongType > randomAccess( final Interval interval )
	{
		return randomAccess();
	}

}
