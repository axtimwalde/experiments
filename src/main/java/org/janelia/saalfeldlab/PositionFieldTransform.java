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

package org.janelia.saalfeldlab;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.type.numeric.RealType;

/**
 * A {@link RealTransform} by continuous coordinate lookup.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class PositionFieldTransform< T extends RealType< T > > implements RealTransform
{
	/* one for each dimension */
	private final RealRandomAccessible< T >[] positions;
	private final RealRandomAccess< T >[] positionAccesses;

	@SuppressWarnings( "unchecked" )
	public PositionFieldTransform( final RealRandomAccessible< T >[] positions )
	{
		this.positions = positions;
		positionAccesses = new RealRandomAccess[ positions.length ];
		for ( int d = 0; d < positions.length; ++d )
		{
			assert( positions[ d ].numDimensions() == positions.length ) : "Dimensions do not match.";

			positionAccesses[ d ] = positions[ d ].realRandomAccess();
		}
	}

	@Override
	public int numSourceDimensions()
	{
		return positions.length;
	}

	@Override
	public int numTargetDimensions()
	{
		return positions.length;
	}

	@Override
	public void apply( final double[] source, final double[] target )
	{
		for ( int d = 0; d < target.length; d++ )
		{
			final RealRandomAccess< T > access = positionAccesses[ d ];
			access.setPosition( source );
			target[ d ] = access.get().getRealDouble();
		}
	}

	@Override
	public void apply( final float[] source, final float[] target )
	{
		for ( int d = 0; d < target.length; d++ )
		{
			final RealRandomAccess< T > access = positionAccesses[ d ];
			access.setPosition( source );
			target[ d ] = ( float )access.get().getRealDouble();
		}
	}

	@Override
	public void apply( final RealLocalizable source, final RealPositionable target )
	{
		for ( int d = 0; d < target.numDimensions(); d++ )
		{
			final RealRandomAccess< T > access = positionAccesses[ d ];
			access.setPosition( source );
			target.setPosition( access.get().getRealDouble(), d );
		}
	}

	@Override
	public RealTransform copy()
	{
		return new PositionFieldTransform< T >( positions );
	}
}
