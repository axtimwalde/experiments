package org.janelia.saalfeldlab;

import java.util.ArrayList;

import ij.IJ;
import ij.ImageJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.labeling.ConnectedComponentAnalysis;
import net.imglib2.algorithm.util.unionfind.IntArrayUnionFind;
import net.imglib2.algorithm.util.unionfind.UnionFind;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeView;
import net.imglib2.view.composite.RealComposite;

public class ConnectedComponentsAffinities {

	public static void main(final String... args) {

		new ImageJ();

		final ImagePlusImg<FloatType, ?> affinitiesX = ImagePlusImgs.from(IJ.openImage("/home/saalfeld/projects/stardist-affinities/aff_inner_x.tif"));
		final ImagePlusImg<FloatType, ?> affinitiesY = ImagePlusImgs.from(IJ.openImage("/home/saalfeld/projects/stardist-affinities/aff_inner_y.tif"));
		final ImagePlusImg<FloatType, ?> affinitiesZ = ImagePlusImgs.from(IJ.openImage("/home/saalfeld/projects/stardist-affinities/aff_inner_z.tif"));

		final ArrayList<ImagePlusImg<FloatType, ?>> affinityList = new ArrayList<>();
		affinityList.add(affinitiesX);
		affinityList.add(affinitiesY);
		affinityList.add(affinitiesZ);

		final RandomAccessibleInterval<FloatType> affinityStack = Views.stack(affinityList);

		ImageJFunctions.show(affinityStack);

		final CompositeView<BoolType, RealComposite<BoolType>> thresholdedAffinities = Views.collapseReal(
				Views.extendValue(
						Converters.convert(
								affinityStack,
								(a, b) -> {b.set(a.get() > 40);},
								new BoolType()),
						new BoolType()),
				3);

		final int numPixels = (int)Intervals.numElements(affinitiesX);
		final UnionFind uf = new IntArrayUnionFind(numPixels);

		final long[] labels = new long[numPixels];
		final ArrayImg< UnsignedLongType, LongArray > labelMap =
				ArrayImgs.unsignedLongs(
						labels,
						Intervals.dimensionsAsLongArray(affinitiesX));

		ConnectedComponentAnalysis.connectedComponentsOnAffinities(
				thresholdedAffinities,
				new long[][] {
					{1, 0, 0},
					{0, 1, 0},
					{0, 0, 1}},
				labelMap,
				uf,
				0 );

		ImageJFunctions.show(
				Converters.convert(
						(RandomAccessibleInterval<UnsignedLongType>)labelMap,
						(a, b) -> {b.setReal(a.getIntegerLong());},
						new FloatType()));
	}

}
