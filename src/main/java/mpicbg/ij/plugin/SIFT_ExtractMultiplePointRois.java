package mpicbg.ij.plugin;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import mpicbg.ij.FeatureTransform;
import mpicbg.ij.SIFT;
import mpicbg.ij.TransformMeshMapping;
import mpicbg.ij.util.Util;
import mpicbg.imagefeatures.Feature;
import mpicbg.imagefeatures.FloatArray2DSIFT;
import mpicbg.models.AbstractModel;
import mpicbg.models.AffineModel2D;
import mpicbg.models.CoordinateTransformMesh;
import mpicbg.models.GaussianMovingLeastSquaresTransform2;
import mpicbg.models.HomographyModel2D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import mpicbg.models.SimilarityModel2D;
import mpicbg.models.TranslationModel2D;
import mpicbg.util.ColorStream;
import mpicbg.util.Timer;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.KDTree;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.neighborsearch.NearestNeighborSearch;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;

/**
 * Extract multiple consensus sets of landmark correspondences in two images.
 * Display the consensus sets as Voronoi diagrams and save them as as
 * PointRois.
 *
 * The plugin uses the Scale Invariant Feature Transform (SIFT) by David Lowe
 * \cite{Lowe04} and the Random Sample Consensus (RANSAC) by Fishler and Bolles
 * \citet{FischlerB81} with respect to a transformation model to identify
 * landmark correspondences.
 *
 * BibTeX:
 * <pre>
 * &#64;article{Lowe04,
 *   author    = {David G. Lowe},
 *   title     = {Distinctive Image Features from Scale-Invariant Keypoints},
 *   journal   = {International Journal of Computer Vision},
 *   year      = {2004},
 *   volume    = {60},
 *   number    = {2},
 *   pages     = {91--110},
 * }
 * &#64;article{FischlerB81,
 *	 author    = {Martin A. Fischler and Robert C. Bolles},
 *   title     = {Random sample consensus: a paradigm for model fitting with applications to image analysis and automated cartography},
 *   journal   = {Communications of the ACM},
 *   volume    = {24},
 *   number    = {6},
 *   year      = {1981},
 *   pages     = {381--395},
 *   publisher = {ACM Press},
 *   address   = {New York, NY, USA},
 *   issn      = {0001-0782},
 *   doi       = {http://doi.acm.org/10.1145/358669.358692},
 * }
 * </pre>
 *
 * @author Stephan Saalfeld <saalfeld@mpi-cbg.de>
 */
public class SIFT_ExtractMultiplePointRois implements PlugIn
{
	protected ImagePlus imp1;
	protected ImagePlus imp2;

	final protected ArrayList< Feature > fs1 = new ArrayList< Feature >();
	final protected ArrayList< Feature > fs2 = new ArrayList< Feature >();;

	static private class Param
	{
		final public FloatArray2DSIFT.Param sift = new FloatArray2DSIFT.Param();

		/**
		 * Closest/next closest neighbour distance ratio
		 */
		public float rod = 0.92f;

		public boolean useGeometricConsensusFilter = true;

		/**
		 * Maximal allowed alignment error in px
		 */
		public float maxEpsilon = 25.0f;

		/**
		 * Inlier/candidates ratio
		 */
		public float minInlierRatio = 0.05f;

		/**
		 * Minimal absolute number of inliers
		 */
		public int minNumInliers = 7;

		/**
		 * Implemeted transformation models for choice
		 */
		final static public String[] modelStrings = new String[]{ "Translation", "Rigid", "Similarity", "Affine", "Perspective" };
		public int modelIndex = 1;
	}

	final static private Param p = new Param();

//	protected ImagePlus display( final ColorProcessor target, final ArrayList< PointMatch > pm12, final ARGBType argb )
//	{
//
//		final ImagePlusImgFactory< ARGBType > factory = new ImagePlusImgFactory< ARGBType >();
//
//			final KDTree< ARGBType > kdtreeMatches = new KDTree< ARGBType >( matches2ColorSamples( pm12 ) );
//			final KDTree< ARGBType > kdtreeMask = new KDTree< ARGBType >( maskSamples );
//
//			/* nearest neighbor */
//			final ImagePlusImg< ARGBType, ? > img = factory.create( new long[]{ imp1.getWidth(), imp1.getHeight() }, new ARGBType() );
//			drawNearestNeighbor(
//					img,
//					new NearestNeighborSearchOnKDTree< ARGBType >( kdtreeMatches ),
//					new NearestNeighborSearchOnKDTree< ARGBType >( kdtreeMask ) );
//
//			final ImagePlus impVis;
//			ColorProcessor ipVis;
//			try
//			{
//				impVis = img.getImagePlus();
//				ipVis = ( ColorProcessor )impVis.getProcessor();
//				while ( ipVis.getWidth() > meshResolution * minGridSize )
//					ipVis = Downsampler.downsampleColorProcessor( ipVis );
//				ipTable.copyBits( ipVis, i * w + w, j * h + h, Blitter.COPY );
//				impTable.updateAndDraw();
//			}
//			catch ( final ImgLibException e )
//			{
//				IJ.log( "ImgLib2 Exception, vectors could not be painted." );
//				e.printStackTrace();
//			}
//		}
//		else
//		{
//			final Roi roi = new Roi( i * w + w, j * h + h, w, h );
//			final Color c = IJ.getInstance().getForeground();
//			ipTable.setColor( Color.WHITE );
//			ipTable.fill( roi );
//			ipTable.setColor( c );
//		}
//	}

	final static protected < T extends Type< T > > long drawNearestNeighbor(
			final IterableInterval< T > target,
			final NearestNeighborSearch< T > nnSearchSamples )
	{
		final Timer timer = new Timer();
		timer.start();
		final Cursor< T > c = target.localizingCursor();
		while ( c.hasNext() )
		{
			c.fwd();
			nnSearchSamples.search( c );
			c.get().set( nnSearchSamples.getSampler().get() );
		}
		return timer.stop();
	}

	final static protected void colorPoints(
			final Iterable< Point > points,
			final RealPointSampleList< ARGBType > samples,
			final Color color )
	{
		for ( final Point point : points )
			samples.add( new RealPoint( point.getL() ), new ARGBType( color.getRGB() ) );
	}

	final protected ImageProcessor transform( final ImageProcessor ip, final Collection< PointMatch > matches )
	{
		final GaussianMovingLeastSquaresTransform2 t = new GaussianMovingLeastSquaresTransform2();
		try
		{
			t.setModel( AffineModel2D.class );
		}
		catch ( final Exception e )
		{
			return null;
		}
		t.setAlpha( ip.getWidth() / 10 );

		final TransformMeshMapping< CoordinateTransformMesh > mapping;
		try
		{
			t.setMatches( matches );
			mapping = new TransformMeshMapping< CoordinateTransformMesh >( new CoordinateTransformMesh( t, 128, ip.getWidth(), ip.getHeight() ) );
		}
		catch ( final NotEnoughDataPointsException e )
		{
			IJ.showMessage( "Not enough landmarks selected to find a transformation model." );
			return null;
		}
		catch ( final IllDefinedDataPointsException e )
		{
			IJ.showMessage( "The set of landmarks is ill-defined in terms of the desired transformation." );
			return null;
		}

		final ImageProcessor ip2 = ip.createProcessor( ip.getWidth(), ip.getHeight() );
		ip.setInterpolationMethod( ImageProcessor.BILINEAR );
		mapping.mapInterpolated( ip, ip2 );

		return ip2;
	}

	@Override
	public void run( final String args )
	{
		// cleanup
		fs1.clear();
		fs2.clear();

		if ( IJ.versionLessThan( "1.40" ) ) return;

		final int[] ids = WindowManager.getIDList();
		if ( ids == null || ids.length < 2 )
		{
			IJ.showMessage( "You should have at least two images open." );
			return;
		}

		final String[] titles = new String[ ids.length ];
		for ( int i = 0; i < ids.length; ++i )
		{
			titles[ i ] = ( WindowManager.getImage( ids[ i ] ) ).getTitle();
		}

		final GenericDialog gd = new GenericDialog( "Extract SIFT Landmark Correspondences" );

		gd.addMessage( "Image Selection:" );
		final String current = WindowManager.getCurrentImage().getTitle();
		gd.addChoice( "source_image", titles, current );
		gd.addChoice( "target_image", titles, current.equals( titles[ 0 ] ) ? titles[ 1 ] : titles[ 0 ] );

		gd.addMessage( "Scale Invariant Interest Point Detector:" );
		gd.addNumericField( "initial_gaussian_blur :", p.sift.initialSigma, 2, 6, "px" );
		gd.addNumericField( "steps_per_scale_octave :", p.sift.steps, 0 );
		gd.addNumericField( "minimum_image_size :", p.sift.minOctaveSize, 0, 6, "px" );
		gd.addNumericField( "maximum_image_size :", p.sift.maxOctaveSize, 0, 6, "px" );

		gd.addMessage( "Feature Descriptor:" );
		gd.addNumericField( "feature_descriptor_size :", p.sift.fdSize, 0 );
		gd.addNumericField( "feature_descriptor_orientation_bins :", p.sift.fdBins, 0 );
		gd.addNumericField( "closest/next_closest_ratio :", p.rod, 2 );

		gd.addMessage( "Geometric Consensus Filter:" );
		gd.addCheckbox( "filter matches by geometric consensus", p.useGeometricConsensusFilter );
		gd.addNumericField( "maximal_alignment_error :", p.maxEpsilon, 2, 6, "px" );
		gd.addNumericField( "minimal_inlier_ratio :", p.minInlierRatio, 2 );
		gd.addNumericField( "minimal_number_of_inliers :", p.minNumInliers, 0 );
		gd.addChoice( "expected_transformation :", Param.modelStrings, Param.modelStrings[ p.modelIndex ] );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		imp1 = WindowManager.getImage( ids[ gd.getNextChoiceIndex() ] );
		imp2 = WindowManager.getImage( ids[ gd.getNextChoiceIndex() ] );

		p.sift.initialSigma = ( float )gd.getNextNumber();
		p.sift.steps = ( int )gd.getNextNumber();
		p.sift.minOctaveSize = ( int )gd.getNextNumber();
		p.sift.maxOctaveSize = ( int )gd.getNextNumber();

		p.sift.fdSize = ( int )gd.getNextNumber();
		p.sift.fdBins = ( int )gd.getNextNumber();
		p.rod = ( float )gd.getNextNumber();

		p.useGeometricConsensusFilter = gd.getNextBoolean();
		p.maxEpsilon = ( float )gd.getNextNumber();
		p.minInlierRatio = ( float )gd.getNextNumber();
		p.minNumInliers = ( int )gd.getNextNumber();
		p.modelIndex = gd.getNextChoiceIndex();

		run();
	}

	public void run()
	{
		final FloatArray2DSIFT sift = new FloatArray2DSIFT( p.sift );
		final SIFT ijSIFT = new SIFT( sift );

		long start_time = System.currentTimeMillis();
		IJ.log( "Processing SIFT ..." );
		ijSIFT.extractFeatures( imp1.getProcessor(), fs1 );
		IJ.log( " took " + ( System.currentTimeMillis() - start_time ) + "ms." );
		IJ.log( fs1.size() + " features extracted." );

		start_time = System.currentTimeMillis();
		IJ.log( "Processing SIFT ..." );
		ijSIFT.extractFeatures( imp2.getProcessor(), fs2 );
		IJ.log( " took " + ( System.currentTimeMillis() - start_time ) + "ms." );
		IJ.log( fs2.size() + " features extracted." );

		start_time = System.currentTimeMillis();
		IJ.log( "Identifying correspondence candidates using brute force ..." );
		final List< PointMatch > candidates = new ArrayList< PointMatch >();
		FeatureTransform.matchFeatures( fs1, fs2, candidates, p.rod );
		IJ.log( " took " + ( System.currentTimeMillis() - start_time ) + "ms." );

		final RoiManager roiManager = RoiManager.getInstance() == null ? new RoiManager() : RoiManager.getInstance();
		final Overlay overlay1 = new Overlay();
		final Overlay overlay2 = new Overlay();

		final ArrayList< PointMatch > allInliers = new ArrayList< PointMatch >();

		final ColorProcessor ipVis1 = new ColorProcessor( imp1.getWidth(), imp1.getHeight() );
		final ColorProcessor ipVis2 = new ColorProcessor( imp1.getWidth(), imp1.getHeight() );

		if ( p.useGeometricConsensusFilter )
		{
			IJ.log( candidates.size() + " potentially corresponding features identified." );

			final List< PointMatch > inliers = new ArrayList< PointMatch >();

			AbstractModel< ? > model;
			switch ( p.modelIndex )
			{
			case 0:
				model = new TranslationModel2D();
				break;
			case 1:
				model = new RigidModel2D();
				break;
			case 2:
				model = new SimilarityModel2D();
				break;
			case 3:
				model = new AffineModel2D();
				break;
			case 4:
				model = new HomographyModel2D();
				break;
			default:
				return;
			}

			final RealPointSampleList< ARGBType > pl1 = new RealPointSampleList< ARGBType >( 2 );
			final RealPointSampleList< ARGBType > pl2 = new RealPointSampleList< ARGBType >( 2 );

			boolean modelFound = true;
			do
			{
				start_time = System.currentTimeMillis();
				IJ.log( "Filtering correspondence candidates by geometric consensus ..." );

				try
				{
					modelFound = model.filterRansac(
							candidates,
							inliers,
							1000,
							p.maxEpsilon,
							p.minInlierRatio,
							p.minNumInliers );
				}
				catch ( final NotEnoughDataPointsException e )
				{
					modelFound = false;
				}

				IJ.log( " took " + ( System.currentTimeMillis() - start_time ) + "ms." );

				if ( modelFound )
				{
					final float x1[] = new float[ inliers.size() ];
					final float y1[] = new float[ inliers.size() ];
					final float x2[] = new float[ inliers.size() ];
					final float y2[] = new float[ inliers.size() ];

					int i = 0;

					for ( final PointMatch m : inliers )
					{
						final double[] m_p1 = m.getP1().getL();
						final double[] m_p2 = m.getP2().getL();

						x1[ i ] = ( float )m_p1[ 0 ];
						y1[ i ] = ( float )m_p1[ 1 ];
						x2[ i ] = ( float )m_p2[ 0 ];
						y2[ i ] = ( float )m_p2[ 1 ];

						++i;
					}

					allInliers.addAll( inliers );

					final PointRoi pr1 = new PointRoi( x1, y1, inliers.size() );
					final PointRoi pr2 = new PointRoi( x2, y2, inliers.size() );

					final Color color = new Color( ColorStream.next() );

					pr1.setStrokeColor( color );
					pr2.setStrokeColor( color );

					overlay1.add( pr1 );
					overlay2.add( pr2 );

					roiManager.add( imp1, pr1, 0 );
					roiManager.add( imp2, pr2, 0 );

					candidates.removeAll( inliers );

					final ArrayList< Point > points1 = new ArrayList< Point >();
					final ArrayList< Point > points2 = new ArrayList< Point >();
					PointMatch.sourcePoints( inliers, points1 );
					PointMatch.targetPoints( inliers, points2 );

					colorPoints( points1, pl1, color );
					colorPoints( points2, pl2, color );

					IJ.log( inliers.size() + " corresponding features with an average displacement of " + String.format( "%.3f", PointMatch.meanDistance( inliers ) ) + "px identified." );
					IJ.log( "Estimated transformation model: " + model );
				}
				else
				{
					IJ.log( "No more correspondences found." );
				}
			}
			while ( modelFound );

			if ( allInliers.size() > 0 )
			{
				imp1.setOverlay( overlay1 );
				imp2.setOverlay( overlay2 );
				imp1.updateAndDraw();
				imp2.updateAndDraw();

				drawNearestNeighbor(
						ArrayImgs.argbs( ( int[] )ipVis1.getPixels(), imp1.getWidth(), imp1.getHeight() ),
						new NearestNeighborSearchOnKDTree< ARGBType >( new KDTree< ARGBType >( pl1 ) ) );
				drawNearestNeighbor(
						ArrayImgs.argbs( ( int[] )ipVis2.getPixels(), imp2.getWidth(), imp2.getHeight() ),
						new NearestNeighborSearchOnKDTree< ARGBType >( new KDTree< ARGBType >( pl2 ) ) );

				new ImagePlus( imp1.getTitle() + " consensus sets", ipVis1 ).show();
				new ImagePlus( imp2.getTitle() + " consensus sets", ipVis2 ).show();

				final ImageStack stack = imp2.getStack();
				stack.addSlice( transform( imp1.getProcessor(), allInliers ) );
				imp2.setStack( stack );
				imp2.updateAndDraw();
			}

		}
		else
		{
			final ArrayList< Point > p1 = new ArrayList< Point >();
			final ArrayList< Point > p2 = new ArrayList< Point >();
			PointMatch.sourcePoints( candidates, p1 );
			PointMatch.targetPoints( candidates, p2 );
			imp1.setRoi( Util.pointsToPointRoi( p1 ) );
			imp2.setRoi( Util.pointsToPointRoi( p2 ) );
			IJ.log( candidates.size() + " corresponding features identified." );
		}
	}
}
