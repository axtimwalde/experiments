package org.janelia.saalfeldlab;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import ij.ImageJ;
import ij.ImagePlus;
import ij.process.LUT;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.labeling.ConnectedComponentAnalysis;
import net.imglib2.algorithm.util.unionfind.IntArrayUnionFind;
import net.imglib2.algorithm.util.unionfind.UnionFind;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeView;
import net.imglib2.view.composite.RealComposite;

public class ConnectedComponentsAffinitiesCREMI {

	public static byte[] toByteArray(final IntStream stream) {
	    return stream.collect(ByteArrayOutputStream::new, (baos, i) -> baos.write((byte) i),
	            (baos1, baos2) -> baos1.write(baos2.toByteArray(), 0, baos2.size()))
	            .toByteArray();
	}

	public static <T extends RealType<T>> void padAffinities(
			final List<RandomAccessibleInterval<T>> affinities,
			final long[][] offsets) {

	}

	public static void main(final String... args) throws IOException {

		final long[] position = new long[3];

		IntervalIndexer.indexToPosition(434176, new long[] {25, 39, 11}, position);

		System.out.println(Arrays.toString(position));

		new ImageJ();

		final N5FSReader n5 = new N5FSReader("/home/saalfeld/projects/affinities/CREMI/sample_A.n5");
		final RandomAccessibleInterval<FloatType> affinities = N5Utils.open(n5, "/affinities");

		final long[] min = new long[]{128, 128, 8, 0};
		final long[] size = new long[]{256, 256, 32, affinities.dimension(3)};

		ImageJFunctions.show(Views.offsetInterval(
				affinities,
				min,
				size));


//		final ArrayList<RandomAccessibleInterval<FloatType>> slices = new ArrayList<>();
//		slices.add(Views.hyperSlice(affinities, 3, 0));
//		slices.add(Views.hyperSlice(affinities, 3, 4));
//		slices.add(Views.hyperSlice(affinities, 3, 8));
//		slices.add(Views.hyperSlice(affinities, 3, 1));
//		slices.add(Views.hyperSlice(affinities, 3, 5));
//		slices.add(Views.hyperSlice(affinities, 3, 9));
//		slices.add(Views.hyperSlice(affinities, 3, 2));
//		slices.add(Views.hyperSlice(affinities, 3, 6));
//		slices.add(Views.hyperSlice(affinities, 3, 10));
//		slices.add(Views.hyperSlice(affinities, 3, 3));
//		slices.add(Views.hyperSlice(affinities, 3, 7));
//		slices.add(Views.hyperSlice(affinities, 3, 11));

//		affinities = Views.stack(slices);

		final IntervalView<FloatType> affinitiesCrop =
				Views.offsetInterval(
					Views.extendValue(
						Views.offsetInterval(
							affinities,
							min,
							size),
						new FloatType(0)),
					Intervals.expand(new FinalInterval(size), 10, 10, 10, 0));

		ImageJFunctions.show(affinitiesCrop);

		final RandomAccessibleInterval<BoolType> affinitiesThresholded = Converters.convert(
				(RandomAccessibleInterval<FloatType>)affinitiesCrop,
				(a, b) -> {b.set(a.getRealFloat() > 0.95);},
				new BoolType());

		ImageJFunctions.show(affinitiesThresholded);

		final ExtendedRandomAccessibleInterval<BoolType, RandomAccessibleInterval<BoolType>> affinitiesThresholdedExtended = Views.extendValue(
						affinitiesThresholded,
						new BoolType(false));

		final CompositeView<BoolType, RealComposite<BoolType>> thresholdedAffinities = Views.collapseReal(
				affinitiesThresholdedExtended,
				3);

		final int numPixels = (int)Intervals.numElements(affinitiesCrop.dimension(0) * affinitiesCrop.dimension(1) * affinitiesCrop.dimension(2));
		final UnionFind uf = new IntArrayUnionFind(numPixels);

		final long[] labels = new long[numPixels];
		final ArrayImg< UnsignedLongType, LongArray > labelMap =
				ArrayImgs.unsignedLongs(
						labels,
						affinitiesCrop.dimension(0),
						affinitiesCrop.dimension(1),
						affinitiesCrop.dimension(2));

		ConnectedComponentAnalysis.connectedComponentsOnAffinities(
				thresholdedAffinities,
//				new long[][] {
//						{0, 0, -1},
//						{0, -1, 0},
//						{-1, 0, 0}
//				},
//				new long[][] {
//					{0, 0, -2},
//					{0, -2, 0},
//					{-2, 0, 0}
//				},
//				new long[][] {
//					{0, 0, -5},
//					{0, -5, 0},
//					{-5, 0, 0}
//				},
//				new long[][] {
//					{0, 0, -10},
//					{0, -10, 0},
//					{-10, 0, 0}
//				},
				new long[][] {
					{0, 0, -1},
					{0, 0, -2},
					{0, 0, -5},
					{0, 0, -10},
					{0, -1, 0},
					{0, -2, 0},
					{0, -5, 0},
					{0, -10, 0},
					{-1, 0, 0},
					{-2, 0, 0},
					{-5, 0, 0},
					{-10, 0, 0}},
				labelMap,
				uf,
				0 );

		final LUT lut = new LUT(
//				toByteArray(Arrays.stream(new int[] {255, 0, 255, 0, 0, 255, 0, 255, 0, 154, 0, 120, 31, 255, 177, 241, 254, 221, 32, 114, 118, 2, 200, 136, 255, 133, 161, 20, 0, 220, 147, 0, 0, 57, 238, 0, 171, 161, 164, 255, 71, 212, 251, 171, 117, 166, 0, 165, 98, 0, 0, 86, 159, 66, 255, 0, 252, 159, 167, 74, 0, 145, 207, 195, 253, 66, 106, 181, 132, 96, 255, 102, 254, 228, 17, 210, 91, 32, 180, 226, 0, 93, 166, 97, 98, 126, 0, 255, 7, 180, 148, 204, 55, 0, 150, 39, 206, 150, 180, 110, 147, 199, 115, 15, 172, 182, 216, 87, 216, 0, 243, 216, 1, 52, 255, 87, 198, 255, 123, 120, 162, 105, 198, 121, 0, 231, 217, 255, 209, 36, 87, 211, 203, 62, 0, 112, 209, 0, 105, 255, 233, 191, 69, 171, 14, 0, 118, 255, 94, 238, 159, 80, 189, 0, 88, 71, 1, 99, 2, 139, 171, 141, 85, 150, 0, 255, 222, 107, 30, 173, 255, 0, 138, 111, 225, 255, 229, 114, 111, 134, 99, 105, 200, 209, 198, 79, 174, 170, 199, 255, 146, 102, 111, 92, 172, 210, 199, 255, 250, 49, 254, 254, 68, 201, 199, 68, 147, 22, 8, 116, 104, 64, 164, 207, 118, 83, 0, 43, 160, 176, 29, 122, 214, 160, 106, 153, 192, 125, 149, 213, 22, 166, 109, 86, 255, 255, 255, 202, 67, 234, 191, 38, 85, 121, 254, 139, 141, 0, 63, 255, 17, 154, 149, 126, 58, 189})),
//				toByteArray(Arrays.stream(new int[] {255, 0, 0, 255, 0, 0, 83, 211, 159, 77, 255, 63, 150, 172, 204, 8, 143, 0, 26, 0, 108, 173, 255, 108, 183, 133, 3, 249, 71, 94, 212, 76, 66, 167, 112, 0, 245, 146, 255, 206, 0, 173, 118, 188, 0, 0, 115, 93, 132, 121, 255, 53, 0, 45, 242, 93, 255, 191, 84, 39, 16, 78, 149, 187, 68, 78, 1, 131, 233, 217, 111, 75, 100, 3, 199, 129, 118, 59, 84, 8, 1, 132, 250, 123, 0, 190, 60, 253, 197, 167, 186, 187, 0, 40, 122, 136, 130, 164, 32, 86, 0, 48, 102, 187, 164, 117, 220, 141, 85, 196, 165, 255, 24, 66, 154, 95, 241, 95, 172, 100, 133, 255, 82, 26, 238, 207, 128, 211, 255, 0, 163, 231, 111, 24, 117, 176, 24, 30, 200, 203, 194, 129, 42, 76, 117, 30, 73, 169, 55, 230, 54, 0, 144, 109, 223, 80, 93, 48, 206, 83, 0, 42, 83, 255, 152, 138, 69, 109, 0, 76, 134, 35, 205, 202, 75, 176, 232, 16, 82, 137, 38, 38, 110, 164, 210, 103, 165, 45, 81, 89, 102, 134, 152, 255, 137, 34, 207, 185, 148, 34, 81, 141, 54, 162, 232, 152, 172, 75, 84, 45, 60, 41, 113, 0, 1, 0, 82, 92, 217, 26, 3, 58, 209, 100, 157, 219, 56, 255, 0, 162, 131, 249, 105, 188, 109, 3, 0, 0, 109, 170, 165, 44, 185, 182, 236, 165, 254, 60, 17, 221, 26, 66, 157, 130, 6, 117})),
//				toByteArray(Arrays.stream(new int[] {255, 255, 0, 0, 51, 182, 0, 0, 255, 66, 190, 193, 152, 253, 113, 92, 66, 255, 1, 85, 149, 36, 0, 0, 159, 103, 0, 255, 158, 147, 255, 255, 80, 106, 254, 100, 204, 255, 115, 113, 21, 197, 111, 0, 215, 154, 254, 174, 2, 168, 131, 0, 63, 66, 187, 67, 124, 186, 19, 108, 166, 109, 0, 255, 64, 32, 0, 84, 147, 0, 211, 63, 0, 127, 174, 139, 124, 106, 255, 210, 20, 68, 255, 201, 122, 58, 183, 0, 226, 57, 138, 160, 49, 1, 129, 38, 180, 196, 128, 180, 185, 61, 255, 253, 100, 250, 254, 113, 34, 103, 105, 182, 219, 54, 0, 1, 79, 133, 240, 49, 204, 220, 100, 64, 70, 69, 233, 209, 141, 3, 193, 201, 79, 0, 223, 88, 0, 107, 197, 255, 137, 46, 145, 194, 61, 25, 127, 200, 217, 138, 33, 148, 128, 126, 96, 103, 159, 60, 148, 37, 255, 135, 148, 0, 123, 203, 200, 230, 68, 138, 161, 60, 0, 157, 253, 77, 57, 255, 101, 48, 80, 32, 0, 255, 86, 77, 166, 101, 175, 172, 78, 184, 255, 159, 178, 98, 147, 30, 141, 78, 97, 100, 23, 84, 240, 0, 58, 28, 121, 0, 255, 38, 215, 155, 35, 88, 232, 87, 146, 229, 36, 159, 207, 105, 160, 113, 207, 89, 34, 223, 204, 69, 97, 78, 81, 248, 73, 35, 18, 173, 0, 51, 2, 158, 212, 89, 193, 43, 40, 246, 146, 84, 238, 72, 101, 101})));
				toByteArray(Arrays.stream(new int[] {255, 0, 255, 0, 0, 255, 0, 255, 0, 154, 0, 120, 31, 255, 177, 241, 254, 221, 32, 114, 118, 2, 200, 136, 255, 133, 161, 20, 0, 220, 147, 0, 0, 57, 238, 0, 171, 161, 164, 255, 71, 212, 251, 171, 117, 166, 0, 165, 98, 0, 0, 86, 159, 66, 255, 0, 252, 159, 167, 74, 0, 145, 207, 195, 253, 66, 106, 181, 132, 96, 255, 102, 254, 228, 17, 210, 91, 32, 180, 226, 0, 93, 166, 97, 98, 126, 0, 255, 7, 180, 148, 204, 55, 0, 150, 39, 206, 150, 180, 110, 147, 199, 115, 15, 172, 182, 216, 87, 216, 0, 243, 216, 1, 52, 255, 87, 198, 255, 123, 120, 162, 105, 198, 121, 0, 231, 217, 255, 209, 36, 87, 211, 203, 62, 0, 112, 209, 0, 105, 255, 233, 191, 69, 171, 14, 0, 118, 255, 94, 238, 159, 80, 189, 0, 88, 71, 1, 99, 2, 139, 171, 141, 85, 150, 0, 255, 222, 107, 30, 173, 255, 0, 138, 111, 225, 255, 229, 114, 111, 134, 99, 105, 200, 209, 198, 79, 174, 170, 199, 255, 146, 102, 111, 92, 172, 210, 199, 255, 250, 49, 254, 254, 68, 201, 199, 68, 147, 22, 8, 116, 104, 64, 164, 207, 118, 83, 0, 43, 160, 176, 29, 122, 214, 160, 106, 153, 192, 125, 149, 213, 22, 166, 109, 86, 255, 255, 255, 202, 67, 234, 191, 38, 85, 121, 254, 139, 141, 0, 63, 255, 17, 154, 149, 126, 58, 0})),
				toByteArray(Arrays.stream(new int[] {255, 0, 0, 255, 0, 0, 83, 211, 159, 77, 255, 63, 150, 172, 204, 8, 143, 0, 26, 0, 108, 173, 255, 108, 183, 133, 3, 249, 71, 94, 212, 76, 66, 167, 112, 0, 245, 146, 255, 206, 0, 173, 118, 188, 0, 0, 115, 93, 132, 121, 255, 53, 0, 45, 242, 93, 255, 191, 84, 39, 16, 78, 149, 187, 68, 78, 1, 131, 233, 217, 111, 75, 100, 3, 199, 129, 118, 59, 84, 8, 1, 132, 250, 123, 0, 190, 60, 253, 197, 167, 186, 187, 0, 40, 122, 136, 130, 164, 32, 86, 0, 48, 102, 187, 164, 117, 220, 141, 85, 196, 165, 255, 24, 66, 154, 95, 241, 95, 172, 100, 133, 255, 82, 26, 238, 207, 128, 211, 255, 0, 163, 231, 111, 24, 117, 176, 24, 30, 200, 203, 194, 129, 42, 76, 117, 30, 73, 169, 55, 230, 54, 0, 144, 109, 223, 80, 93, 48, 206, 83, 0, 42, 83, 255, 152, 138, 69, 109, 0, 76, 134, 35, 205, 202, 75, 176, 232, 16, 82, 137, 38, 38, 110, 164, 210, 103, 165, 45, 81, 89, 102, 134, 152, 255, 137, 34, 207, 185, 148, 34, 81, 141, 54, 162, 232, 152, 172, 75, 84, 45, 60, 41, 113, 0, 1, 0, 82, 92, 217, 26, 3, 58, 209, 100, 157, 219, 56, 255, 0, 162, 131, 249, 105, 188, 109, 3, 0, 0, 109, 170, 165, 44, 185, 182, 236, 165, 254, 60, 17, 221, 26, 66, 157, 130, 6, 0})),
				toByteArray(Arrays.stream(new int[] {255, 255, 0, 0, 51, 182, 0, 0, 255, 66, 190, 193, 152, 253, 113, 92, 66, 255, 1, 85, 149, 36, 0, 0, 159, 103, 0, 255, 158, 147, 255, 255, 80, 106, 254, 100, 204, 255, 115, 113, 21, 197, 111, 0, 215, 154, 254, 174, 2, 168, 131, 0, 63, 66, 187, 67, 124, 186, 19, 108, 166, 109, 0, 255, 64, 32, 0, 84, 147, 0, 211, 63, 0, 127, 174, 139, 124, 106, 255, 210, 20, 68, 255, 201, 122, 58, 183, 0, 226, 57, 138, 160, 49, 1, 129, 38, 180, 196, 128, 180, 185, 61, 255, 253, 100, 250, 254, 113, 34, 103, 105, 182, 219, 54, 0, 1, 79, 133, 240, 49, 204, 220, 100, 64, 70, 69, 233, 209, 141, 3, 193, 201, 79, 0, 223, 88, 0, 107, 197, 255, 137, 46, 145, 194, 61, 25, 127, 200, 217, 138, 33, 148, 128, 126, 96, 103, 159, 60, 148, 37, 255, 135, 148, 0, 123, 203, 200, 230, 68, 138, 161, 60, 0, 157, 253, 77, 57, 255, 101, 48, 80, 32, 0, 255, 86, 77, 166, 101, 175, 172, 78, 184, 255, 159, 178, 98, 147, 30, 141, 78, 97, 100, 23, 84, 240, 0, 58, 28, 121, 0, 255, 38, 215, 155, 35, 88, 232, 87, 146, 229, 36, 159, 207, 105, 160, 113, 207, 89, 34, 223, 204, 69, 97, 78, 81, 248, 73, 35, 18, 173, 0, 51, 2, 158, 212, 89, 193, 43, 40, 246, 146, 84, 238, 72, 101, 0})));

		final ImagePlus imp = ImageJFunctions.show(
				Converters.convert(
						(RandomAccessibleInterval<UnsignedLongType>)labelMap,
						(a, b) -> {b.setReal(a.getIntegerLong());},
						new FloatType()));
		imp.setLut(lut);
	}

}
