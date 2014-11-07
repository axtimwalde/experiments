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
package mpicbg.models;

/**
 * 
 *
 * @author Stephan Saalfeld <saalfeld@mpi-cbg.de>
 */
public class GaussianMovingLeastSquaresTransform2 extends MovingLeastSquaresTransform2
{
	protected double gaussianWeigh( final double d2, final double var2 )
	{
		return Math.exp( -d2 / var2 );
	}
	
	/** Temporary.  Actually weigh should be overridden which is final in the current mpicbg master... */
	@Override
	public void applyInPlace( final float[] location )
	{
		final double var2 = 2 * alpha * alpha;
		final float[] ww = new float[ w.length ];
		
		for ( int i = 0; i < w.length; ++i )
		{	
			float s = 0;
			for ( int d = 0; d < location.length; ++d )
			{
				final float dx = p[ d ][ i ] - location[ d ];
				s += dx * dx;
			}
			if ( s <= 0 )
			{
				for ( int d = 0; d < location.length; ++d )
					location[ d ] = q[ d ][ i ];
				return;
			}
			ww[ i ] = w[ i ] * ( float )gaussianWeigh( s, var2 );
		}
		
		try 
		{
			model.fit( p, q, ww );
			model.applyInPlace( location );
		}
		catch ( final IllDefinedDataPointsException e ){}
		catch ( final NotEnoughDataPointsException e ){}
	}
}
