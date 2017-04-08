package mpicbg.ij.plugin;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.janelia.saalfeldlab.PositionFieldTransform;
import org.janelia.saalfeldlab.PositionRandomAccessible;
import org.janelia.saalfeldlab.RealPositionRealRandomAccessible;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.io.Opener;
import ij.plugin.PlugIn;
import ij.plugin.filter.GaussianBlur;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ini.trakem2.imaging.filters.ValueToNoise;
import mpicbg.ij.integral.BlockPMCC;
import mpicbg.ij.integral.IntegralImage;
import mpicbg.ij.util.Filter;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.TranslationModel2D;
import mpicbg.util.Util;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.planar.PlanarImg;
import net.imglib2.img.planar.PlanarImgs;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.DeformationFieldTransform;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.Scale2D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 * <h1>Transfer an image sequence into an optic flow field<h1>
 *
 * <p>Flow fields are calculated for each pair <em>(t,t+1)</em> of the sequence
 * independently.  The motion vector for each pixel in image t is estimated by
 * searching the most similar looking pixel in image <em>t+1</em>.  The
 * similarity measure is Pearson Product-Moment Correlation Coefficient of all
 * pixels in a local vicinity.  The local vicinity is defined by a block and is
 * calculated using an {@link IntegralImage}.  Both the size of the block and
 * the search radius are parameters of the method.</p>
 *
 * @author Stephan Saalfeld <saalfeld@mpi-cbg.de>
 * @version 0.1a
 */
public class PMCCScaleSpaceBlockFlow implements PlugIn
{
	static protected int blockRadius = 8;
	static protected int maxDistance = 7;
	static protected boolean showColors = false;

	final static protected void colorCircle( final ColorProcessor ip, final int maxDistance )
	{
		final int r1 = Math.min( ip.getWidth(), ip.getHeight() ) / 2;

		for ( int y = 0; y < ip.getHeight(); ++y )
		{
			final float dy = y - ip.getHeight() / 2;
			for ( int x = 0; x < ip.getWidth(); ++x )
			{
				final float dx = x - ip.getWidth() / 2;
				final float l = ( float )Math.sqrt( dx * dx + dy * dy );

				if ( l > r1 )
					ip.putPixel( x, y, 0 );
				else
					ip.putPixel( x, y, colorVector( dx / maxDistance, dy / maxDistance ) );
			}
		}
	}

	final static protected int colorVector( final float xs, final float ys )
	{
		final double a = Math.sqrt( xs * xs + ys * ys );
		if ( a == 0.0 ) return 0;

		double o = ( Math.atan2( xs / a, ys / a ) + Math.PI ) / Math.PI * 3;

		final double r, g, b;

		if ( o < 3 )
			r = Math.min( 1.0, Math.max( 0.0, 2.0 - o ) ) * a;
		else
			r = Math.min( 1.0, Math.max( 0.0, o - 4.0 ) ) * a;

		o += 2;
		if ( o >= 6 ) o -= 6;

		if ( o < 3 )
			g = Math.min( 1.0, Math.max( 0.0, 2.0 - o ) ) * a;
		else
			g = Math.min( 1.0, Math.max( 0.0, o - 4.0 ) ) * a;

		o += 2;
		if ( o >= 6 ) o -= 6;

		if ( o < 3 )
			b = Math.min( 1.0, Math.max( 0.0, 2.0 - o ) ) * a;
		else
			b = Math.min( 1.0, Math.max( 0.0, o - 4.0 ) ) * a;

		return ( ( ( ( int )( r * 255 ) << 8 ) | ( int )( g * 255 ) ) << 8 ) | ( int )( b * 255 );
	}

	final static protected void algebraicToColor(
			final float[] ipXPixels,
			final float[] ipYPixels,
			final int[] ipColorPixels,
			final double max )
	{
		final int n = ipXPixels.length;
		for ( int i = 0; i < n; ++i )
		{
			final double x = ipXPixels[ i ] / max;
			final double y = ipYPixels[ i ] / max;

			final double r = Math.sqrt( x * x + y * y );
			final double phi = Math.atan2( x / r, y / r );

			if ( r == 0.0 )
				ipColorPixels[ i ] = 0;
			else
			{
				final double red, green, blue;

				double o = ( phi + Math.PI ) / Math.PI * 3;

				if ( o < 3 )
					red = Math.min( 1.0, Math.max( 0.0, 2.0 - o ) ) * r;
				else
					red = Math.min( 1.0, Math.max( 0.0, o - 4.0 ) ) * r;

				o += 2;
				if ( o >= 6 ) o -= 6;

				if ( o < 3 )
					green = Math.min( 1.0, Math.max( 0.0, 2.0 - o ) ) * r;
				else
					green = Math.min( 1.0, Math.max( 0.0, o - 4.0 ) ) * r;

				o += 2;
				if ( o >= 6 ) o -= 6;

				if ( o < 3 )
					blue = Math.min( 1.0, Math.max( 0.0, 2.0 - o ) ) * r;
				else
					blue = Math.min( 1.0, Math.max( 0.0, o - 4.0 ) ) * r;

				ipColorPixels[ i ] =  ( ( ( ( int )( red * 255 ) << 8 ) | ( int )( green * 255 ) ) << 8 ) | ( int )( blue * 255 );
			}
		}
	}

	final static protected void algebraicToColor(
			final short[] ipXPixels,
			final short[] ipYPixels,
			final int[] ipColorPixels,
			final double max )
	{
		final int n = ipXPixels.length;
		for ( int i = 0; i < n; ++i )
		{
			final double x = ipXPixels[ i ] / max;
			final double y = ipYPixels[ i ] / max;

			final double r = Math.sqrt( x * x + y * y );
			final double phi = Math.atan2( x / r, y / r );

			if ( r == 0.0 )
				ipColorPixels[ i ] = 0;
			else
			{
				final double red, green, blue;

				double o = ( phi + Math.PI ) / Math.PI * 3;

				if ( o < 3 )
					red = Math.min( 1.0, Math.max( 0.0, 2.0 - o ) ) * r;
				else
					red = Math.min( 1.0, Math.max( 0.0, o - 4.0 ) ) * r;

				o += 2;
				if ( o >= 6 ) o -= 6;

				if ( o < 3 )
					green = Math.min( 1.0, Math.max( 0.0, 2.0 - o ) ) * r;
				else
					green = Math.min( 1.0, Math.max( 0.0, o - 4.0 ) ) * r;

				o += 2;
				if ( o >= 6 ) o -= 6;

				if ( o < 3 )
					blue = Math.min( 1.0, Math.max( 0.0, 2.0 - o ) ) * r;
				else
					blue = Math.min( 1.0, Math.max( 0.0, o - 4.0 ) ) * r;

				ipColorPixels[ i ] =  ( ( ( ( int )( red * 255 ) << 8 ) | ( int )( green * 255 ) ) << 8 ) | ( int )( blue * 255 );
			}
		}
	}

	static public void opticFlow(
			final FloatProcessor ip1,
			final FloatProcessor ip2,
			final int distance,
			final ImageStack r,
			final ImageStack shiftVectors,
			final ImageStack of,
			final double scaleFactor )
	{
		final BlockPMCC bc = new BlockPMCC( ip1.getWidth(), ip1.getHeight(), ip1, ip2 );
		//final BlockPMCC bc = new BlockPMCC( ip1, ip2 );

		final FloatProcessor ipR = bc.getTargetProcessor();
		final float[] ipRPixels = ( float[] )ipR.getPixels();

		final ArrayList< Double > radiusList = new ArrayList< Double >();

		for ( double radius = 1; radius < r.getWidth() / 4; radius *= scaleFactor )
		{
			radiusList.add( radius );

			final FloatProcessor ipRMax = new FloatProcessor( ipR.getWidth(), ipR.getHeight() );
			final float[] ipRMaxPixels = ( float[] )ipRMax.getPixels();
			{
				for ( int i = 0; i < ipRMaxPixels.length; ++i )
					ipRMaxPixels[ i ] = -1;
			}
			final ShortProcessor ipX = new ShortProcessor( ipR.getWidth(), ipR.getHeight() );
			final ShortProcessor ipY = new ShortProcessor( ipR.getWidth(), ipR.getHeight() );
			final ColorProcessor cp = new ColorProcessor( ipR.getWidth(), ipR.getHeight() );

			r.addSlice( "" + radius, ipRMax );
			shiftVectors.addSlice( "" + radius, ipX );
			shiftVectors.addSlice( "" + radius, ipY );
			of.addSlice( "" + radius, cp );
		}

		/* assemble into typed arrays for quicker access */
		final float[][] rArrays = new float[ r.getSize() ][];
		final short[][] xShiftArrays = new short[ rArrays.length ][];
		final short[][] yShiftArrays = new short[ rArrays.length ][];
		final int[][] ofArrays = new int[ rArrays.length ][];
		final int[] radii = new int[ rArrays.length ];
		for ( int i = 0; i < radii.length; ++i )
		{
			rArrays[ i ] = ( float[] )r.getImageArray()[ i ];
			xShiftArrays[ i ] = ( short[] )shiftVectors.getImageArray()[ i << 1 ];
			yShiftArrays[ i ] = ( short[] )shiftVectors.getImageArray()[ ( i << 1 ) | 1 ];
			ofArrays[ i ] = ( int[] )of.getImageArray()[ i ];

			radii[ i ] = ( int )Math.round( radiusList.get( i ) );
		}

		for ( int yo = -distance; yo <= distance; ++yo )
		{
			for ( int xo = -distance; xo <= distance; ++xo )
			{
				// continue if radius is larger than maxDistance
				if ( yo * yo + xo * xo > distance * distance ) continue;

				IJ.log( String.format( "(%d, %d)", xo, yo ) );

				bc.setOffset( xo, yo );

				for ( int ri = 0; ri < radii.length; ++ri )
				{
					final int blockRadius = radii[ ri ];

					bc.rSignedSquare( blockRadius );

					final float[] ipRMaxPixels = rArrays[ ri ];
					final short[] ipXPixels = xShiftArrays[ ri ];
					final short[] ipYPixels = yShiftArrays[ ri ];

					// update the translation fields
					final int h = ipR.getHeight() - distance;
					final int width = ipR.getWidth();
					final int w = width - distance;

					for ( int y = distance; y < h; ++y )
					{
						final int row = y * width;
						final int rowR;
						if ( yo < 0 )
							rowR = row;
						else
							rowR = ( y - yo ) * width;
						for ( int x = distance; x < w; ++x )
						{
							final int i = row + x;
							final int iR;
							if ( xo < 0 )
								iR = rowR + x;
							else
								iR = rowR + ( x - xo );

							final float ipRPixel = ipRPixels[ iR ];
							final float ipRMaxPixel = ipRMaxPixels[ i ];

							if ( ipRPixel > ipRMaxPixel )
							{
								ipRMaxPixels[ i ] = ipRPixel;
								ipXPixels[ i ] = ( short )xo;
								ipYPixels[ i ] = ( short )yo;
							}
						}
					}
				}
			}
		}

		for ( int i = 0; i < radii.length; ++i )
			algebraicToColor(
					xShiftArrays[ i ],
					yShiftArrays[ i ],
					ofArrays[ i ],
					distance );
	}


	public final static void filterRansacOpticFlowScaleSpace(
			final ImageStack shiftVectors,
			final FloatProcessor shiftX,
			final FloatProcessor shiftY,
			final ShortProcessor inlierCounts ) throws NotEnoughDataPointsException
	{
		/* assemble into typed arrays for quicker access */
		/* TODO This is still inefficient because scale dimension is fastest but should be slowest
		 * this is true here and in opticFlow, i.e. scale should be interleaved (size of array
		 * becomes concern, use ImgLib2).
		 */
		final short[][] xShiftArrays = new short[ shiftVectors.size() / 2 ][];
		final short[][] yShiftArrays = new short[ xShiftArrays.length ][];
		for ( int i = 0; i < xShiftArrays.length; ++i )
		{
			xShiftArrays[ i ] = ( short[] )shiftVectors.getImageArray()[ i << 1 ];
			yShiftArrays[ i ] = ( short[] )shiftVectors.getImageArray()[ ( i << 1 ) | 1 ];
		}

		final int n = shiftVectors.getWidth() * shiftVectors.getHeight();
		final int m = xShiftArrays.length;

		final ArrayList< PointMatch > pq = new ArrayList< PointMatch >();
		for ( int i = 0; i < m; ++i )
			pq.add(
					new PointMatch(
							new Point( new double[]{ 0.0, 0.0 } ),
							new Point( new double[]{ 0.0, 0.0 } ) ) );

		final TranslationModel2D model = new TranslationModel2D();
		final ArrayList< PointMatch > pqInliers = new ArrayList< PointMatch >();
		final double[] translation = new double[ 6 ];

		for ( int i = 0; i < n; ++i )
		{
			for ( int j = 0; j < m; ++j )
			{
				final double[] q = pq.get( j ).getP2().getW();
				q[ 0 ] = xShiftArrays[ j ][ i ];
				q[ 1 ] = yShiftArrays[ j ][ i ];
			}
			model.ransac( pq, pqInliers, 100, 0.5, 0 );
			model.toArray( translation );
			shiftX.setf( i, ( float )translation[ 4 ] );
			shiftY.setf( i, ( float )translation[ 5 ] );

			inlierCounts.set( i, pqInliers.size() );

			if ( i / inlierCounts.getWidth() * inlierCounts.getWidth() == i )
				IJ.log( "row " + i / inlierCounts.getWidth() );

		}
	}


	public final static void filterOpticFlowScaleSpace(
			final ImageStack shiftVectors,
			final FloatProcessor shiftX,
			final FloatProcessor shiftY,
			final ShortProcessor inlierCounts,
			final short distance ) throws NotEnoughDataPointsException
	{
		/* assemble into typed arrays for quicker access */
		/* TODO This is still inefficient because scale dimension is fastest but should be slowest
		 * this is true here and in opticFlow, i.e. scale should be interleaved (size of array
		 * becomes concern, use ImgLib2).
		 */
		final short[][] xShiftArrays = new short[ shiftVectors.size() / 2 ][];
		final short[][] yShiftArrays = new short[ xShiftArrays.length ][];
		for ( int i = 0; i < xShiftArrays.length; ++i )
		{
			xShiftArrays[ i ] = ( short[] )shiftVectors.getImageArray()[ i << 1 ];
			yShiftArrays[ i ] = ( short[] )shiftVectors.getImageArray()[ ( i << 1 ) | 1 ];
		}

		final int n = shiftVectors.getWidth() * shiftVectors.getHeight();
		final int m = xShiftArrays.length;


		final int w = ( distance * 2 + 1 );
		final int[] countsArray = new int[ w * w ];
		final ArrayImg< IntType, IntArray > countsImg = ArrayImgs.ints( countsArray, distance * 2 + 1, distance * 2 + 1 );
		final ArrayRandomAccess< IntType > countsAccess = countsImg.randomAccess( countsImg );

		for ( int i = 0; i < n; ++i )
		{
			Arrays.fill( countsArray, 0 );
			for ( int j = 0; j < m; ++j )
			{
				final int x = xShiftArrays[ j ][ i ] + distance;
				final int y = yShiftArrays[ j ][ i ] + distance;

				countsAccess.setPosition( x, 0 );
				countsAccess.setPosition( y, 1 );
				countsAccess.get().inc();
			}

			float bestX = 0;
			float bestY = 0;
			int bestCount = 0;

			for ( int x = 0; x < w; ++x )
			{
				countsAccess.setPosition( x, 0 );
				for ( int y = 0; y < w; ++y )
				{
					countsAccess.setPosition( y, 1 );
					final int count = countsAccess.get().get();

					if ( count > bestCount )
					{
						bestCount = count;
						bestX = x - distance;
						bestY = y - distance;
					}
				}
			}

			shiftX.setf( i, bestX );
			shiftY.setf( i, bestY );

			inlierCounts.set( i, bestCount );

			if ( i / inlierCounts.getWidth() * inlierCounts.getWidth() == i )
				IJ.log( "row " + i / inlierCounts.getWidth() );

		}
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


	private static final < T extends Type< T > > void copy( final RandomAccessible< ? extends T > source, final RandomAccessibleInterval< T > target )
	{
		Views.flatIterable( Views.interval( Views.pair( source, target ), target ) ).forEach(
				pair -> pair.getB().set( pair.getA() ) );
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

	private static final FloatProcessor convertSignedShortToFloat( final ShortProcessor ip )
	{
		final FloatProcessor fp = new FloatProcessor( ip.getWidth(), ip.getHeight() );
		final short[] signedShortPixels = ( short[] )ip.getPixels();
		final float[] signedFloatPixels = ( float[] )fp.getPixels();
		for ( int i = 0; i < signedShortPixels.length; ++i )
			signedFloatPixels[ i ] = signedShortPixels[ i ];

		return fp;
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


	private static final DeformationFieldTransform< DoubleType > createDeformationFieldTransform(
			final ShortProcessor shiftX,
			final ShortProcessor shiftY,
			final InterpolatorFactory< DoubleType, RandomAccessible< DoubleType > > interpolatorFactory )
	{
		final PlanarImg< DoubleType, DoubleArray > data = PlanarImgs.doubles( shiftX.getWidth(), shiftX.getHeight(), 2 );
		final short[] signedShortXPixels = ( short[] )shiftX.getPixels();
		int i = 0;
		for ( final DoubleType t : Views.flatIterable( Views.hyperSlice( data, 2, 0 ) ) )
			t.set( signedShortXPixels[ i++ ] );
		final short[] signedShortYPixels = ( short[] )shiftY.getPixels();
		i = 0;
		for ( final DoubleType t : Views.flatIterable( Views.hyperSlice( data, 2, 1 ) ) )
			t.set( signedShortYPixels[ i++ ] );

		return new DeformationFieldTransform<>(
				Views.interpolate(
						Views.extendBorder( data ),
						interpolatorFactory ) );
	}


	private static final DeformationFieldTransform< DoubleType > createDeformationFieldTransform(
			final ShortProcessor shiftX,
			final ShortProcessor shiftY )
	{
		return createDeformationFieldTransform( shiftX, shiftY, new NLinearInterpolatorFactory<>() );
	}


	private static final FloatProcessor map(
			final FloatProcessor source,
			final ShortProcessor shiftX,
			final ShortProcessor shiftY )
	{

		return materialize(
				createTransformedInterval(
						source,
						new FinalInterval( source.getWidth(), source.getHeight() ),
						createDeformationFieldTransform( shiftX, shiftY ) ) );
	}


	private static final RealRandomAccessible< DoubleType > createPositionField(
			final RealTransform transform,
			final int d )
	{
		return new RealTransformRealRandomAccessible<>(
				new RealPositionRealRandomAccessible( transform.numTargetDimensions(), d ),
				transform );
	}


	private static final void visualizeDeformation(
			final FloatProcessor ip,
			final ImageStack seqR,
			final ImageStack seqOpticFlow,
			final ImageStack seqFlowVectors
			)
	{
		final ImageStack mappedImages = new ImageStack( ip.getWidth(), ip.getHeight() );
		for ( int s = 0; s < seqFlowVectors.size(); s += 2 )
		{
			final ImageProcessor target =
					map(
							ip,
							seqFlowVectors.getProcessor( s + 1 ).convertToShortProcessor(),
							seqFlowVectors.getProcessor( s + 2 ).convertToShortProcessor() );
			mappedImages.addSlice( target );
		}
		new ImagePlus( "target", mappedImages ).show();
	}


	private static final void visualizeFlow(
			final ImagePlus imp,
			final ImageStack seqR,
			final ImageStack seqOpticFlow,
			final ImageStack seqFlowVectors,
			final ColorProcessor filteredOpticFlow )
	{
//		final ImagePlus impR = new ImagePlus( imp.getTitle() + " R^2", seqR );
//		impR.setOpenAsHyperStack( true );
//		impR.setCalibration( imp.getCalibration() );
//		impR.show();

		final ImagePlus impOpticFlow = new ImagePlus( imp.getTitle() + " optic flow", seqOpticFlow );
		impOpticFlow.setOpenAsHyperStack( true );
		impOpticFlow.setCalibration( imp.getCalibration() );
		impOpticFlow.show();

		final ImagePlus impFilteredOpticFlow = new ImagePlus( imp.getTitle() + " filtered optic flow", filteredOpticFlow );
		impFilteredOpticFlow.show();


//		final ImagePlus notYetComposite = new ImagePlus( imp.getTitle() + " flow vectors", seqFlowVectors );
//		notYetComposite.setOpenAsHyperStack( true );
//		notYetComposite.setCalibration( imp.getCalibration() );
//		notYetComposite.setDimensions( 2, 1, 1 );
//
//		final ImagePlus impFlowVectors = new CompositeImage( notYetComposite, CompositeImage.GRAYSCALE );
//		impFlowVectors.setOpenAsHyperStack( true );
//		impFlowVectors.setDimensions( 2, 1, 1 );
//		impFlowVectors.show();
//
//		impFlowVectors.setPosition( 1, 1, 1 );
//		impFlowVectors.setDisplayRange( 0, 1 );
//		impFlowVectors.setPosition( 2, 1, 1 );
//		impFlowVectors.setDisplayRange( -Math.PI, Math.PI );
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



	@Override
	final public void run( final String args )
	{
		if ( IJ.versionLessThan( "1.41n" ) ) return;

		final ImagePlus imp = WindowManager.getCurrentImage();
		if ( imp == null || imp.getStack().getSize() != 2 )
		{
			IJ.error( "This plugin works for stacks with 2 slices only" );
			return;
		}

		final GenericDialog gd = new GenericDialog( "Generate optic flow" );
		gd.addNumericField( "maximal_distance :", maxDistance, 0, 6, "px" );
		gd.addCheckbox( "show_color_map", showColors );

		gd.showDialog();

		if (gd.wasCanceled()) return;

		maxDistance = ( int )gd.getNextNumber();
		showColors = gd.getNextBoolean();

		if ( showColors )
		{
			final ColorProcessor ipColor = new ColorProcessor( maxDistance * 2 + 1, maxDistance * 2 + 1 );
			colorCircle( ipColor, maxDistance );
			final ImagePlus impColor = new ImagePlus( "Color", ipColor );
			impColor.show();
		}

		final FloatProcessor ip1 = imp.getStack().getProcessor( 1 ).convertToFloatProcessor();
		final FloatProcessor ip2 = imp.getStack().getProcessor( 2 ).convertToFloatProcessor();

		final double scaleFactor = 2;

		int nScales = 1;
		for ( double d = maxDistance; d > scaleFactor; d /= scaleFactor )
			++nScales;

		RealRandomAccessible< DoubleType > xPositions = new RealPositionRealRandomAccessible( 2, 0 );
		RealRandomAccessible< DoubleType > yPositions = new RealPositionRealRandomAccessible( 2, 1 );

		final ImageStack ip2Stack = new ImageStack( ip2.getWidth(), ip2.getHeight() );
		ip2Stack.addSlice( "-1", ip2 );
		final ImagePlus impIp2Stack = new ImagePlus( "ip2 transformed", ip2Stack);
		impIp2Stack.show();

		final ValueToNoise filter1 = new ValueToNoise( 0, 0, 255 );
		final ValueToNoise filter2 = new ValueToNoise( 255, 0, 255 );

		for ( int i = 0; i < nScales; ++i )
		{
			final double scale = 1.0 / Util.pow( scaleFactor, nScales - 1 - i );
			FloatProcessor ip1Scaled = Filter.createDownsampled( ip1, scale, 0.5f, 0.5f );
			@SuppressWarnings( "unchecked" )
			final FloatProcessor ip2Transformed = materialize(
					createTransformedInterval(
							ip2,
							new FinalInterval( ip2.getWidth(), ip2.getHeight() ),
							new PositionFieldTransform<>(
									( RealRandomAccessible< DoubleType >[] )new RealRandomAccessible[]{
										xPositions,
										yPositions } ) ) );
			FloatProcessor ip2Scaled = Filter.createDownsampled(
					ip2Transformed,
					scale,
					0.5f,
					0.5f );

			ip1Scaled = filter1.process( ip1Scaled ).convertToFloatProcessor();
			ip1Scaled = filter2.process( ip1Scaled ).convertToFloatProcessor();
			ip2Scaled = filter1.process( ip2Scaled ).convertToFloatProcessor();
			ip2Scaled = filter2.process( ip2Scaled ).convertToFloatProcessor();

			ip2Stack.addSlice( "" + i, ip2Transformed );
			impIp2Stack.setStack( ip2Stack );
			impIp2Stack.updateAndDraw();

//			new ImagePlus( "ip2 scaled and transformed" , ip2Scaled ).show();
			final ImageStack seqR = new ImageStack( ip1Scaled.getWidth(), ip1Scaled.getHeight() );
			final ImageStack seqOpticFlow = new ImageStack( ip1Scaled.getWidth(), ip1Scaled.getHeight() );
			final ImageStack seqFlowVectors = new ImageStack( ip1Scaled.getWidth(), ip1Scaled.getHeight() );

			final short distance = ( short )Math.ceil( scaleFactor * 2 );

			opticFlow(
					ip1Scaled,
					ip2Scaled,
					distance,
					seqR,
					seqFlowVectors,
					seqOpticFlow,
					1.5 );

			final FloatProcessor shiftXFloat = new FloatProcessor( ip1Scaled.getWidth(), ip1Scaled.getHeight() );
			final FloatProcessor shiftYFloat = new FloatProcessor( ip1Scaled.getWidth(), ip1Scaled.getHeight() );
			final ShortProcessor inlierCounts = new ShortProcessor( ip1Scaled.getWidth(), ip1Scaled.getHeight() );
			final ColorProcessor filteredOpticFlow = new ColorProcessor( ip1Scaled.getWidth(), ip1Scaled.getHeight() );
			try
			{
				filterOpticFlowScaleSpace(
						seqFlowVectors,
						shiftXFloat,
						shiftYFloat,
						inlierCounts,
						distance );
			}
			catch ( final NotEnoughDataPointsException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			algebraicToColor(
					( float[] )shiftXFloat.getPixels(),
					( float[] )shiftYFloat.getPixels(),
					( int[] )filteredOpticFlow.getPixels(),
					distance );

//			final FloatProcessor shiftXFloat = convertSignedShortToFloat( seqFlowVectors.getProcessor( seqFlowVectors.size() / 2 + 2 ).convertToShortProcessor() );
//			final FloatProcessor shiftYFloat = convertSignedShortToFloat( seqFlowVectors.getProcessor( seqFlowVectors.size() / 2 + 2 + 1 ).convertToShortProcessor() );

			new GaussianBlur().blurGaussian( shiftXFloat, 4 * scaleFactor );
			new GaussianBlur().blurGaussian( shiftYFloat, 4 * scaleFactor );

			final RealTransformSequence transformSequence = new RealTransformSequence();
			transformSequence.add( new Scale2D( scale, scale ) );
			transformSequence.add( createDeformationFieldTransform(
					shiftXFloat,
					shiftYFloat ) );
			transformSequence.add( new Scale2D( 1.0 / scale, 1.0 / scale ) );

			xPositions = new RealTransformRandomAccessible<>(
					xPositions,
					transformSequence );
			yPositions = new RealTransformRandomAccessible<>(
					yPositions,
					transformSequence );

			visualizeFlow( imp, seqR, seqOpticFlow, seqFlowVectors, filteredOpticFlow );
//			visualizeDeformation( ip2Scaled, seqR, seqOpticFlow, seqFlowVectors );
//			filter( ip2Scaled, seqFlowVectors );
		}

		@SuppressWarnings( "unchecked" )
		final PositionFieldTransform< DoubleType > transform = new PositionFieldTransform<>(
				new RealRandomAccessible[]{
						xPositions,
						yPositions } );

		final FloatProcessor ip2Transformed = materialize(
				createTransformedInterval(
						ip2,
						new FinalInterval( ip2.getWidth(), ip2.getHeight() ),
						transform ) );
		ip2Stack.addSlice( "final", ip2Transformed );
		impIp2Stack.setStack( ip2Stack );
		impIp2Stack.updateAndDraw();

		final RandomAccessibleInterval< DoubleType > xField =
				Views.interval(
						Views.raster(
								createPositionField( transform, 0 ) ),
						new FinalInterval( ip2.getWidth(), ip2.getHeight() ) );

		final RandomAccessibleInterval< DoubleType > yField =
				Views.interval(
						Views.raster(
								createPositionField( transform, 1 ) ),
						new FinalInterval( ip2.getWidth(), ip2.getHeight() ) );

		try
		{
//			writeDoubles( imp.getOriginalFileInfo().directory + "/dx.bin", Views.flatIterable( xField ) );
//			writeDoubles( imp.getOriginalFileInfo().directory + "/dy.bin", Views.flatIterable( yField ) );
			writeDoubles( imp.getOriginalFileInfo().directory + imp.getOriginalFileInfo().fileName + ".x.bin", Views.flatIterable( xField ) );
			writeDoubles( imp.getOriginalFileInfo().directory + imp.getOriginalFileInfo().fileName + ".y.bin", Views.flatIterable( yField ) );

		}
		catch ( final IOException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		IJ.log( "Done." );

	}

	public static int[] visualizeDeformationField(
			final int width,
			final int height,
			final String xPath,
			final String yPath,
			final double max ) throws IOException
	{
		final ArrayImg< DoubleType, ? > xField = ArrayImgs.doubles( width, height );
		final ArrayImg< DoubleType, ? > yField = ArrayImgs.doubles( width, height );

		readDoubles( xPath, xField );
		readDoubles( yPath, yField );

		final float[] xShiftsArray = new float[ width * height ];
		final float[] yShiftsArray = new float[ width * height ];

		final ArrayImg< FloatType, ? > xShifts = ArrayImgs.floats( xShiftsArray, width, height );
		final ArrayImg< FloatType, ? > yShifts = ArrayImgs.floats( yShiftsArray, width, height );

		final RandomAccessibleInterval< LongType > xFieldReference = Views.interval( new PositionRandomAccessible( 2, 0 ), xShifts );
		final RandomAccessibleInterval< LongType > yFieldReference = Views.interval( new PositionRandomAccessible( 2, 1 ), yShifts );

		Views.flatIterable(
				Views.interval(
						Views.pair(
								Views.pair(
										xFieldReference,
										xField ),
								xShifts ),
						xShifts ) ).forEach(
								pair -> pair.getB().set( ( float )pair.getA().getB().get() - pair.getA().getA().get() ) );
		Views.flatIterable(
				Views.interval(
						Views.pair(
								Views.pair(
										yFieldReference,
										yField ),
								yShifts ),
						yShifts ) ).forEach(
								pair -> pair.getB().set( ( float )pair.getA().getB().get() - pair.getA().getA().get() ) );

		System.out.println( "Loaded and converted transform fields" );

		final int[] colors = new int[ width * height ];

		algebraicToColor( xShiftsArray, yShiftsArray, colors, max );

		return colors;
	}


	public final static void main( final String... args ) throws IOException
	{
		new ImageJ();
//		final ImagePlus imp = new Opener().openImage( "/groups/saalfeld/saalfeldlab/scheffer/26-27/26.bot.min.i-27.top.min.i.tif" );
//		final ImagePlus imp = new Opener().openImage( "/groups/saalfeld/saalfeldlab/scheffer/26-27/26.bot.min.i-27.top.min.i.affine.tif" );
//		final ImagePlus imp = new Opener().openImage( "/groups/saalfeld/saalfeldlab/scheffer/26-27/26.3008-27.128.2.tif" );
//		final ImagePlus imp = new Opener().openImage( "/groups/saalfeld/saalfeldlab/scheffer/26-27/26.3008-27.128.2.affine.tif" );
//		final ImagePlus imp = new Opener().openImage( "/groups/saalfeld/saalfeldlab/scheffer/26-27-2017-03-1.crop.tif" );
//		final ImagePlus imp = new Opener().openImage( "/home/saalfelds/tmp/dagmar/26-27-affine.tif" );
		final ImagePlus imp = new Opener().openImage( "/groups/saalfeld/home/saalfelds/tmp/dagmar/26-03173.27-00303.rigid.tif" );
		imp.show();
		new PMCCScaleSpaceBlockFlow().run("");
		new ImagePlus(
				"",
				new ColorProcessor(
						imp.getWidth(),
						imp.getHeight(),
						visualizeDeformationField(
								imp.getWidth(),
								imp.getHeight(),
//								imp.getOriginalFileInfo().directory + "/dx.bin",
//								imp.getOriginalFileInfo().directory + "/dy.bin",
								imp.getOriginalFileInfo().directory + imp.getOriginalFileInfo().fileName + ".x.bin",
								imp.getOriginalFileInfo().directory + imp.getOriginalFileInfo().fileName + ".y.bin",
								400 ) ) ).show();
	}
}
