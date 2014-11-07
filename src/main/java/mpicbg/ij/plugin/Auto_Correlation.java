package mpicbg.ij.plugin;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import mpicbg.util.RealSum;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.fft.FourierTransform;
import net.imglib2.algorithm.fft.FourierTransform.Rearrangement;
import net.imglib2.algorithm.fft.InverseFourierTransform;
import net.imglib2.exception.ImgLibException;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.outofbounds.OutOfBoundsConstantValueFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 *
 *
 * @author Stephan Saalfeld <saalfeld@mpi-cbg.de>
 */
public class Auto_Correlation implements PlugIn
{
	final static private < T extends RealType< T > > double[] minMax( final Img< T > img )
	{
		final double a = img.firstElement().getRealDouble();
		final double[] minMax = new double[]{ a, a };
		for ( final T t : img )
		{
			final double v = t.getRealDouble();
			if ( v < minMax[ 0 ] )
				minMax[ 0 ] = v;
			else if ( v > minMax[ 1 ] )
				minMax[ 1 ] = v;
		}
		return minMax;
	}

	final static private < T extends RealType< T > > Img< FloatType > normalize(
			final IterableInterval< T > source,
			final ImgFactory< FloatType > imgFactory )
	{
		final Img< FloatType > normalizedImg = imgFactory.create( source, new FloatType() );
		final RealSum summer = new RealSum();
		final RealSum summer2 = new RealSum();
		final Cursor< FloatType > copy = normalizedImg.cursor();
		for ( final T t : source )
		{
			final double a = t.getRealDouble();
			summer.add( a );
			summer2.add( a * a );
			copy.next().setReal( a );
		}
		final double sum = summer.getSum();
		final double sum2 = summer2.getSum();
		final double n1 = 1.0 / source.size();
		final double mean = sum * n1;
		final double scale = ( sum2 - n1 * ( sum2 * sum2 ) ) / n1;
		for ( final FloatType t : normalizedImg )
			t.setReal( ( t.get() - mean ) * scale );

		return normalizedImg;
	}

	@Override
	public void run( final String arg )
	{
		final ImagePlus imp = IJ.getImage();
		final ImagePlus imp2;
		try
		{
			switch ( imp.getType() )
			{
			case ImagePlus.COLOR_RGB:
			case ImagePlus.COLOR_256:
				IJ.error( "Only single channel images supported" );
				return;
			case ImagePlus.GRAY8:
				imp2 = process( ImagePlusAdapter.wrapByte( imp ) );
				break;
			case ImagePlus.GRAY16:
				imp2 = process( ImagePlusAdapter.wrapShort( imp ) );
				break;
			case ImagePlus.GRAY32:
				imp2 = process( ImagePlusAdapter.wrapFloat( imp ) );
				break;
			default:
				imp2 = null;
			}

			if ( imp2 != null )
			{
				imp2.setTitle( imp.getTitle() + " autocorrelation" );
				imp2.setCalibration( imp.getCalibration() );
				imp2.show();

				/* centered view */
				final FloatImagePlus< FloatType > fimp = ImagePlusAdapter.wrapFloat( imp2 );

				final long[] min = new long[ fimp.numDimensions() ];
				final long[] max = new long[ fimp.numDimensions() ];

				for ( int d = 0; d < min.length; ++d )
				{
					min[ d ] = -fimp.dimension( d ) / 2;
					max[ d ] = min[ d ] + fimp.dimension( d ) - 1;
				}

				final RandomAccessible< FloatType > extendedFimp = Views.extendPeriodic( fimp );
				final RandomAccessibleInterval< FloatType > shiftedFimp = Views.offsetInterval( extendedFimp, new FinalInterval( min, max ) );

				ImageJFunctions.show( shiftedFimp );
			}
		}
		catch ( final IncompatibleTypeException e )
		{
			IJ.error( "Incompatible type ... however it comes ..." );
		}
	}

	public < T extends RealType< T > & NativeType< T > > ImagePlus process( final ImagePlusImg< T, ? > img )
			throws IncompatibleTypeException
	{
		normalize( img, new ImagePlusImgFactory< FloatType >() );

		final T firstElement = img.firstElement();

		final FourierTransform< T, ComplexFloatType > fft =
				new FourierTransform< T, ComplexFloatType >(
						img,
						new ComplexFloatType(),
						new OutOfBoundsConstantValueFactory< T, RandomAccessibleInterval< T > >( firstElement.createVariable() ) );
		fft.setRearrangement( Rearrangement.UNCHANGED );
		fft.process();
		final Img< ComplexFloatType > fftImg = fft.getResult();

		final ComplexFloatType c = new ComplexFloatType();
		for ( final ComplexFloatType t : fftImg )
		{
			c.set( t );
			c.complexConjugate();
			t.mul( c );
		}

		final InverseFourierTransform< FloatType, ComplexFloatType > ifft =
				new InverseFourierTransform< FloatType, ComplexFloatType >(
						fftImg, new ImagePlusImgFactory< FloatType >(), fft, new FloatType() );
		ifft.process();
		try
		{
			final FloatImagePlus< FloatType > img2 = ( FloatImagePlus< FloatType > )ifft.getResult();
			final FloatImagePlus< FloatType > fimg2 = ( FloatImagePlus< FloatType > )normalize( img2, new ImagePlusImgFactory< FloatType >() );
			final double[] minMax = minMax( fimg2 );
			final ImagePlus imp = fimg2.getImagePlus();
			imp.setDisplayRange( minMax[ 0 ], minMax[ 1 ] );
			return imp;
		}
		catch ( final ImgLibException e ) { return null; }
	}
}
