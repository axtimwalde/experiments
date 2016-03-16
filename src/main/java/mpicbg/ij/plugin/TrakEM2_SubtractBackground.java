package mpicbg.ij.plugin;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import ini.trakem2.display.Display;
import ini.trakem2.display.Displayable;
import ini.trakem2.display.Patch;
import ini.trakem2.display.Selection;

import java.util.ArrayList;
import java.util.List;

import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.TranslationModel1D;
import mpicbg.trakem2.util.Downsampler;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 *
 *
 * @author Stephan Saalfeld <saalfeld@mpi-cbg.de>
 */
public class TrakEM2_SubtractBackground implements PlugIn
{
	static protected int maxSize = 128;
	static protected float inlierRatio = 0.5f;
	static protected float maxEpsilon = 100;
	static protected float trustRange = 3;

	final protected boolean setup()
	{
		final GenericDialog gd = new GenericDialog( "Subtract Background" );
		gd.addNumericField( "image_size :", maxSize, 0, 4, "px" );
		gd.addNumericField( "inlier_ratio :", inlierRatio, 2, 4, "" );
		gd.addNumericField( "absolute_error range :", maxEpsilon, 2, 4, "px" );
		gd.addNumericField( "trust_range :", trustRange, 2, 4, "" );
		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;
		maxSize = ( int )gd.getNextNumber();
		inlierRatio = ( float )gd.getNextNumber();
		maxEpsilon = ( float )gd.getNextNumber();
		trustRange = ( float )gd.getNextNumber();
		return true;
	}

	final static public void setup(
			final int maxSize,
			final float inlierRatio,
			final float maxEpsilon,
			final float trustRange )
	{
		TrakEM2_SubtractBackground.maxSize = maxSize;
		TrakEM2_SubtractBackground.inlierRatio = inlierRatio;
		TrakEM2_SubtractBackground.maxEpsilon = maxEpsilon;
		TrakEM2_SubtractBackground.trustRange = trustRange;
	}

	final < T extends Type< T > > void copyIterableIntervals(
			final IterableInterval< T > src,
			final IterableInterval< T > dst )
	{
		for (
				final Cursor< T > a = src.cursor(), b = dst.cursor();
				a.hasNext();
				b.next().set( a.next() ) );
	}

	@Override
	synchronized public void run( final String arg0 )
	{
		if ( !setup() )
			return;

		final Display front = Display.getFront();
		if ( front == null ) return;

		final Selection selection = front.getSelection();

		final ArrayList< Patch > patches = new ArrayList< Patch >();
		if ( selection == null || selection.getSelected().size() == 0 )
		{
			final ArrayList< Displayable > displayables = front.getLayer().getDisplayables( Patch.class );
			for ( final Displayable d : displayables )
				patches.add( ( Patch )d );
		}
		else
		{
			for ( final Displayable d : selection.getSelected() )
				if ( Patch.class.isInstance( d ) )
					patches.add( ( Patch )d );
		}

		process( patches );
	}

	final public void process( final List< Patch > patches )
	{

		/* original size and scale factor */
		ImageStack stack = null;
		final int width = patches.get( 0 ).getOWidth();
		final int height = patches.get( 0 ).getOHeight();

		int scalePow = 0;

		int pi = 1;
		for ( final Patch p : patches )
		{
			FloatProcessor ip = ( FloatProcessor )p.getImageProcessor().convertToFloat();
			scalePow = 0;
			while ( ip.getWidth() > maxSize && ip.getHeight() > maxSize )
			{
				ip = Downsampler.downsampleFloatProcessor( ip );
				++scalePow;
			}
			if ( stack == null )
				stack = new ImageStack( ip.getWidth(), ip.getHeight() );
			stack.addSlice( ip );

			IJ.showProgress( ++pi, patches.size() );
		}

		new ImagePlus( "stack", stack ).show();

		final ImagePlusImg< FloatType, ? > img = ImagePlusImgs.from( new ImagePlus( "", stack ) );
		final double[][] vs = new double[ ( int )img.dimension( 2 ) ][ 1 ];
		final ArrayList< Point  > points = new ArrayList< Point >( ( int )img.dimension( 2 ) );
		final ArrayList< PointMatch > matches = new ArrayList< PointMatch >( ( int )img.dimension( 2 ) );
		final ArrayList< PointMatch > inliers = new ArrayList< PointMatch >();

		for ( int i = 0; i < img.dimension( 2 ); ++i )
		{
			vs[ i ] = new double[]{ 0 };
			final Point p = new Point( vs[ i ] );
			points.add( p );
			matches.add( new PointMatch( p, new Point( new double[]{ 0 } ) ) );
		}

		final FloatProcessor bg = new FloatProcessor( ( int )img.dimension( 0 ), ( int )img.dimension( 1 ) );
		for ( int i = bg.getWidth() * bg.getHeight() - 1; i >= 0; --i )
			bg.setf( i, -Float.MAX_VALUE );
		final ImagePlus imp = new ImagePlus( "bg", bg );
		imp.show();

		final RandomAccess< FloatType > access = img.randomAccess( img );
		while ( access.getLongPosition( 1 ) < img.dimension( 1 ) )
		{
			access.setPosition( 0, 0 );
			while ( access.getLongPosition( 0 ) < img.dimension( 0 ) )
			{
				access.setPosition( 0, 2 );
				for ( int z = 0; z < img.dimension( 2 ); ++z )
				{
					vs[ z ][ 0 ] = -access.get().getRealFloat();
					access.fwd( 2 );
				}
				final TranslationModel1D t = new TranslationModel1D();
				inliers.clear();
				try
				{
					final boolean modelFound = t.filterRansac( matches, inliers, 200, maxEpsilon, inlierRatio, t.getMinNumMatches(), trustRange );
					//t.fit( matches );
					if ( modelFound )
					{
						final double tf = t.getTranslation();
						bg.setf( access.getIntPosition( 0 ), access.getIntPosition( 1 ), ( float )tf );
					}
				}
				catch ( final NotEnoughDataPointsException e )
				{
					System.err.println( "Not enough data points." );
				}
				access.fwd( 0 );
			}
			IJ.log( access.getLongPosition( 1 ) + "" );
			access.fwd( 1 );
			imp.updateAndDraw();
		}

		/* filter unset values */
		RemoveSaturated.run( bg, -Float.MAX_VALUE );

		/* remove outliers */
		RemoveOutliers.run( bg, maxSize / 8, maxSize / 8, trustRange );

		/* smooth */
		//new Mean( bg ).mean( maxSize / 8 );
		//new GaussianBlur().blurGaussian( bg, maxSize / 8.0, maxSize / 8.0, 0.01 );

		float minV = Float.MAX_VALUE;
		for ( int i = bg.getWidth() * bg.getHeight() - 1; i >= 0; --i )
		{
			final float v = bg.getf( i );
			if ( v < minV )
				minV = v;
		}
		for ( int i = bg.getWidth() * bg.getHeight() - 1; i >= 0; --i )
			bg.setf( i, bg.getf( i ) - minV );

		imp.updateAndDraw();

		/* upscale (interesting that this, again, isn't possible to do right with ImageJ) */
		final FloatProcessor bgUpscaled = new FloatProcessor( width, height );
		final ImagePlusImg< FloatType, ? > imgBg = ImagePlusImgs.from( new ImagePlus( "", bg ) );
		final ImagePlusImg< FloatType, ? > imgBgUpscaled = ImagePlusImgs.from( new ImagePlus( "", bgUpscaled ) );

		final RandomAccessible< FloatType > imgBgExtended = Views.extendBorder( imgBg );
		final RealRandomAccessible< FloatType > interpolant = Views.interpolate( imgBgExtended, new NLinearInterpolatorFactory< FloatType >() );
		final AffineTransform2D affine = new AffineTransform2D();
		affine.translate( 0.5, 0.5 );
		affine.scale( Math.pow( 2, scalePow ) );
		final RealRandomAccessible< FloatType > transformedInterpolant = RealViews.affineReal( interpolant, affine );
		final RandomAccessible< FloatType > raster = Views.raster( transformedInterpolant );
		final RandomAccessibleInterval< FloatType > rasterInterval = Views.interval( raster, imgBgUpscaled );
		final IterableInterval< FloatType > iterable = Views.iterable( rasterInterval );

		copyIterableIntervals( iterable, imgBgUpscaled );

		new ImagePlus( "upscaled background", bgUpscaled ).show();
	}
}
