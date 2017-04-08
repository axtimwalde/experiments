package mpicbg.ij.plugin;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.janelia.saalfeldlab.InterpolatedRealTransform;
import org.janelia.saalfeldlab.PositionFieldTransform;
import org.janelia.saalfeldlab.RealPositionRealRandomAccessible;

import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.Opener;
import ij.process.FloatProcessor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.planar.PlanarImg;
import net.imglib2.img.planar.PlanarImgs;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.DeformationFieldTransform;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class ApplyFlow
{
	private static final < T extends Type< T > > void copy( final RandomAccessible< ? extends T > source, final RandomAccessibleInterval< T > target )
	{
		Views.flatIterable( Views.interval( Views.pair( source, target ), target ) ).forEach(
				pair -> pair.getB().set( pair.getA() ) );
	}

	private static final FloatProcessor materialize( final RandomAccessibleInterval< FloatType > source )
	{
		final FloatProcessor target = new FloatProcessor( ( int )source.dimension( 0 ), ( int )source.dimension( 1 ) );
		copy(
				Views.zeroMin( source ),
				ArrayImgs.floats(
						( float[] )target.getPixels(),
						target.getWidth(),
						target.getHeight() ) );
		return target;
	}

	private static final RandomAccessibleInterval< FloatType > createTransformedInterval(
			final FloatProcessor source,
			final Interval targetInterval,
			final RealTransform transformFromSource )
	{
		return Views.interval(
						new RealTransformRandomAccessible<>(
							Views.interpolate(
									Views.extendBorder(
										ArrayImgs.floats(
											( float[] )source.convertToFloatProcessor().getPixels(),
											source.getWidth(),
											source.getHeight() ) ),
									new NLinearInterpolatorFactory<>() ),
							transformFromSource ),
						targetInterval );
	}

	private static final DeformationFieldTransform< DoubleType > createDeformationFieldTransform(
			final FloatProcessor shiftX,
			final FloatProcessor shiftY,
			final InterpolatorFactory< DoubleType, RandomAccessible< DoubleType > > interpolatorFactory )
	{
		final PlanarImg< DoubleType, DoubleArray > data = PlanarImgs.doubles( shiftX.getWidth(), shiftX.getHeight(), 2 );
		final float[] floatXPixels = ( float[] )shiftX.getPixels();
		int i = 0;
		for ( final DoubleType t : Views.flatIterable( Views.hyperSlice( data, 2, 0 ) ) )
			t.set( floatXPixels[ i++ ] );
		final float[] floatYPixels = ( float[] )shiftY.getPixels();
		i = 0;
		for ( final DoubleType t : Views.flatIterable( Views.hyperSlice( data, 2, 1 ) ) )
			t.set( floatYPixels[ i++ ] );

		return new DeformationFieldTransform<>(
				Views.interpolate(
						Views.extendBorder( data ),
						interpolatorFactory ) );
	}


	private static final DeformationFieldTransform< DoubleType > createDeformationFieldTransform(
			final FloatProcessor shiftX,
			final FloatProcessor shiftY )
	{
		return createDeformationFieldTransform( shiftX, shiftY, new NLinearInterpolatorFactory<>() );
	}


	private static final RealRandomAccessible< DoubleType > createPositionField(
			final RealTransform transform,
			final int d )
	{
		return new RealTransformRealRandomAccessible<>(
				new RealPositionRealRandomAccessible( transform.numTargetDimensions(), d ),
				transform );
	}

	private static final void writeDoubles( final String filePath, final Iterable< DoubleType > stream ) throws IOException
	{
		final File file = new File( filePath );
		if ( !file.getParentFile().exists() )
			file.getParentFile().mkdirs();
		if ( !file.exists() )
			file.createNewFile();

		try ( final DataOutputStream dos = new DataOutputStream( new FileOutputStream( file ) ) )
		{
			for ( final DoubleType t : stream )
				dos.writeDouble( t.get() );
		}
	}

	private static final void readDoubles( final String filePath, final Iterable< DoubleType > stream ) throws IOException
	{
		final File file = new File( filePath );
		try ( final DataInputStream dis = new DataInputStream( new FileInputStream( file ) ) )
		{
			for ( final DoubleType t : stream )
				t.set( dis.readDouble() );
		}
	}



	final public FloatProcessor run(
			final FloatProcessor ip,
			final ArrayImg< DoubleType, ? > xPositions,
			final ArrayImg< DoubleType, ? > yPositions,
			final int width,
			final int height,
			final String xPath,
			final String yPath,
			final AffineTransform2D affine,
			final double lambda ) throws IOException
	{
		final NLinearInterpolatorFactory< DoubleType > interpolatorFactory = new NLinearInterpolatorFactory<>();
		@SuppressWarnings( "unchecked" )
		final PositionFieldTransform< DoubleType > positions =
				new PositionFieldTransform<>(
						new RealRandomAccessible[]{
							Views.interpolate( Views.extendBorder( xPositions ), interpolatorFactory ),
							Views.interpolate( Views.extendBorder( yPositions ), interpolatorFactory )
						});

		final RealTransformSequence transformList = new RealTransformSequence();
		transformList.add( positions );
		transformList.add( affine );

		final InterpolatedRealTransform interpolatedTransform =
				new InterpolatedRealTransform(
						transformList,
						affine,
						lambda );

		return materialize(
				createTransformedInterval(
						ip,
						new FinalInterval( width, height ),
						interpolatedTransform ) );
	}


	public final static void main( final String... args ) throws IOException
	{
		new ImageJ();

		final ImagePlus imp = new Opener().openImage( "/home/saalfeld/tmp/dagmar/ken27-flattened-xy-00303.tif" );
		final FloatProcessor ip = imp.getStack().getProcessor( 1 ).convertToFloatProcessor();

		final int width = 5670;
		final int height = 6426;

		final String xPath = "/home/saalfeld/tmp/dagmar/26-03173.27-00303.rigid.tif.x.bin";
		final String yPath = "/home/saalfeld/tmp/dagmar/26-03173.27-00303.rigid.tif.y.bin";
		final ArrayImg< DoubleType, ? > xPositions = ArrayImgs.doubles( width, height );
		final ArrayImg< DoubleType, ? > yPositions = ArrayImgs.doubles( width, height );
		readDoubles( xPath, xPositions );
		readDoubles( yPath, yPositions );

		final AffineTransform2D rigid = new AffineTransform2D();
		rigid.set(
				0.99999748954962, -0.002240735249145, 13.832151825285585,
				0.002240735249145, 0.99999748954962, 14.594295519243587 );

		final ImageStack stack = new ImageStack( width, height );
		ImagePlus impTransformed = null;

		for ( double lambda = 0; lambda <= 1.0; lambda += 0.1 )
		{
			final FloatProcessor ipTransformed = new ApplyFlow().run(
					ip,
					xPositions,
					yPositions,
					width,
					height,
					xPath,
					yPath,
					rigid.inverse(),
					lambda );

			stack.addSlice( lambda + "", ipTransformed );

			if ( impTransformed == null )
			{
				impTransformed = new ImagePlus( "transformed", stack );
				impTransformed.show();
			}
			else
			{
				impTransformed.setStack( stack );
				impTransformed.updateAndDraw();
			}
		}
	}
}
