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

import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.type.numeric.real.DoubleType;

/**
 * A {@link RealRandomAccessible} over the <em>d</em>-th position of real
 * coordinate space.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class RealPositionRealRandomAccessible implements RealRandomAccessible< DoubleType >
{
	private final int n;
	private final int d;

	public RealPositionRealRandomAccessible( final int numDimensions, final int d )
	{
		this.n = numDimensions;
		this.d = d;
	}

	public class RealPositionRealRandomAccess extends RealPoint implements RealRandomAccess< DoubleType >
	{
		private final DoubleType t = new DoubleType();

		public RealPositionRealRandomAccess()
		{
			super( RealPositionRealRandomAccessible.this.n );
		}

		@Override
		public DoubleType get()
		{
			t.set( position[ d ] );
			return t;
		}

		@Override
		public RealPositionRealRandomAccess copy()
		{
			return new RealPositionRealRandomAccess();
		}

		@Override
		public RealPositionRealRandomAccess copyRealRandomAccess()
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
	public RealPositionRealRandomAccess realRandomAccess()
	{
		return new RealPositionRealRandomAccess();
	}

	@Override
	public RealPositionRealRandomAccess realRandomAccess( final RealInterval interval )
	{
		return realRandomAccess();
	}

}
