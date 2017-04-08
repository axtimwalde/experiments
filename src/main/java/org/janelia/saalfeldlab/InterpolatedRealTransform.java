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
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.realtransform.RealTransform;

/**
 * A {@link RealTransform} that interpolates between two RealTransforms.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class InterpolatedRealTransform implements RealTransform
{
	private final RealTransform a;
	private final RealTransform b;
	private final double lambda;
	private final double[] targetPositionA;
	private final double[] targetPositionB;
	private final RealPositionable targetPositionableA;
	private final RealPositionable targetPositionableB;

	public InterpolatedRealTransform(
			final RealTransform a,
			final RealTransform b,
			final double lambda )
	{
		assert
			a.numSourceDimensions() == b.numSourceDimensions() &&
			a.numTargetDimensions() == b.numTargetDimensions() : "Number of dimensions do not match.";

		this.a = a;
		this.b = b;
		this.lambda = lambda;

		targetPositionA = new double[ a.numTargetDimensions() ];
		targetPositionB = new double[ b.numTargetDimensions() ];
		targetPositionableA = RealPoint.wrap( targetPositionA );
		targetPositionableB = RealPoint.wrap( targetPositionB );
	}

	@Override
	public int numSourceDimensions()
	{
		return a.numSourceDimensions();
	}

	@Override
	public int numTargetDimensions()
	{
		return targetPositionA.length;
	}

	@Override
	public void apply( final double[] source, final double[] target )
	{
		a.apply( source, targetPositionA );
		b.apply( source, targetPositionB );

		for ( int d = 0; d < targetPositionA.length; d++ )
			target[ d ] = ( targetPositionA[ d ] - targetPositionB[ d ] ) * lambda + targetPositionB[ d ];
	}

	@Override
	public void apply( final float[] source, final float[] target )
	{
		throw new UnsupportedOperationException( "Please use double precision" );
	}

	@Override
	public void apply( final RealLocalizable source, final RealPositionable target )
	{
		a.apply( source, targetPositionableA );
		b.apply( source, targetPositionableB );

		for ( int d = 0; d < target.numDimensions(); d++ )
			target.setPosition( ( targetPositionA[ d ] - targetPositionB[ d ] ) * lambda + targetPositionB[ d ], d );
	}

	@Override
	public InterpolatedRealTransform copy()
	{
		return new InterpolatedRealTransform(
				a.copy(),
				b.copy(),
				lambda );
	}
}