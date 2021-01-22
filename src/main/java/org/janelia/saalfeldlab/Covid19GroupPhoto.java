 /**
 *
 */
package org.janelia.saalfeldlab;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.util.Lazy;

import bdv.util.BdvFunctions;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.converter.Converters;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class Covid19GroupPhoto implements Callable<Void> {

	@Option(names = {"--n5Path", "-i"}, required = false, description = "N5 container path, e.g. '/home/saalfeld/Videos/mobile/20200711_111229.n5'")
	private String n5Path = "/home/saalfeld/Videos/mobile/20200711_111229.n5";

	@Option(names = {"--n5Dataset", "-d"}, required = false, description = "N5 dataset, e.g. '/raw'")
	private String n5Dataset = "/s2";

	@Option(names = {"--frames", "-n"}, required = false, description = "number of frames, e.g. 5")
	private int nFrames = 5;

	@Option(names = {"--backgroundFrame", "-b"}, required = false, description = "background frame, e.g. 288")
	private int[] bgIndex = {288, 1632, 5207};

	private static class MaskOp implements Consumer<RandomAccessibleInterval<UnsignedByteType>> {

		private final RandomAccessible<UnsignedByteType>[] frame;
		private final RandomAccessible<UnsignedByteType>[] bg;
		private final int nChannels;
		private final double norm;

		@SafeVarargs
		public MaskOp(
				final RandomAccessible<UnsignedByteType>... frameBg) {

			this.frame = Arrays.copyOf(frameBg, frameBg.length / 2);
			this.bg = Arrays.copyOfRange(frameBg, frame.length, frameBg.length);
			nChannels = frame.length;
			norm = 1.0 / nChannels;
		}

		@Override
		public void accept(final RandomAccessibleInterval<UnsignedByteType> cell) {

			@SuppressWarnings("unchecked")
			final Cursor<UnsignedByteType>[] frameCursor = new Cursor[nChannels];
			@SuppressWarnings("unchecked")
			final Cursor<UnsignedByteType>[] bgCursor = new Cursor[nChannels];
			for (int i = 0; i < nChannels; ++i) {
				frameCursor[i] = Views.flatIterable(Views.interval(frame[i], cell)).cursor();
				bgCursor[i] = Views.flatIterable(Views.interval(Views.addDimension(bg[i]), cell)).cursor();
			}

			final Cursor<UnsignedByteType> cellCursor = Views.flatIterable(cell).cursor();

			double sum;
			while (cellCursor.hasNext()) {
				sum = 0;
				for (int i = 0; i < nChannels; ++i) {
					sum += Math.abs(frameCursor[i].next().getRealDouble() - bgCursor[i].next().getRealDouble());
					sum *= norm;
				}
				cellCursor.next().setReal(sum);
			}
		}
	}

	private static class ComposeOp implements Consumer<RandomAccessibleInterval<UnsignedByteType>> {

		private final RandomAccessible<UnsignedByteType> a;
		private final RandomAccessible<UnsignedByteType> b;
		private final RandomAccessible<UnsignedByteType> mask;

		final double norm = 1.0 / 255;

		public ComposeOp(
				final RandomAccessible<UnsignedByteType> a,
				final RandomAccessible<UnsignedByteType> b,
				final RandomAccessible<UnsignedByteType> mask) {

			this.a = a;
			this.b = b;
			this.mask = mask;
		}

		@Override
		public void accept(final RandomAccessibleInterval<UnsignedByteType> cell) {

			final Cursor<UnsignedByteType> aCursor = Views.flatIterable(Views.interval(a, cell)).cursor();
			final Cursor<UnsignedByteType> bCursor = Views.flatIterable(Views.interval(b, cell)).cursor();
			final Cursor<UnsignedByteType> maskCursor = Views.flatIterable(Views.interval(mask, cell)).cursor();

			final Cursor<UnsignedByteType> cellCursor = Views.flatIterable(cell).cursor();

			while (cellCursor.hasNext()) {
				final double m = maskCursor.next().getRealDouble() * norm;
				final double m1 = 1.0 - m;
				final double composite = aCursor.next().getRealDouble() * m1 + bCursor.next().getRealDouble() * m;
				cellCursor.next().setReal(composite);
			}
		}
	}

	/**
	 * @param args
	 */
	public static void main(final String[] args) {

		new CommandLine(new Covid19GroupPhoto()).execute(args);
	}

	@Override
	public Void call() throws Exception {

		final SharedQueue queue = new SharedQueue(Math.min(8, Math.max(1, Runtime.getRuntime().availableProcessors() / 2)));

		final N5FSReader n5 = new N5FSReader(n5Path);
		final RandomAccessibleInterval<UnsignedIntType> source = N5Utils.openVolatile(n5, n5Dataset);
		final RandomAccessibleInterval<ARGBType> argb = Converters.convert(
				source,
				(a, b) -> {
					b.set(a.getInt());
				},
				new ARGBType());
		final RandomAccessibleInterval<UnsignedByteType> r = Converters.convert(
				source,
				(a, c) -> {
					c.set((a.getInt() >> 16) & 0xff);
				},
				new UnsignedByteType());
		final RandomAccessibleInterval<VolatileUnsignedByteType> rV = Converters.convert(
				VolatileViews.wrapAsVolatile(source, queue),
				(a, c) -> {
					c.get().set((a.get().getInt() >> 16) & 0xff);
					c.setValid(a.isValid());
				},
				new VolatileUnsignedByteType());
		final RandomAccessibleInterval<UnsignedByteType> g = Converters.convert(
				source,
				(a, c) -> {
					c.set((a.getInt() >> 8) & 0xff);
				},
				new UnsignedByteType());
		final RandomAccessibleInterval<UnsignedByteType> b = Converters.convert(
				source,
				(a, c) -> {
					c.set(a.getInt() & 0xff);
				},
				new UnsignedByteType());

		final IntervalView<UnsignedByteType> bgR = Views.hyperSlice(r, 2, bgIndex[2]);
		final IntervalView<UnsignedByteType> bgG = Views.hyperSlice(g, 2, bgIndex[2]);
		final IntervalView<UnsignedByteType> bgB = Views.hyperSlice(b, 2, bgIndex[2]);

		final MaskOp maskOp = new MaskOp(r, g, b, bgR, bgG, bgB);
		final CachedCellImg<UnsignedByteType, ?> mask = Lazy.generate(source, new int[] {128, 128, 1}, new UnsignedByteType(), AccessFlags.setOf(AccessFlags.VOLATILE), maskOp);

		final ComposeOp composeOp1 = new ComposeOp(r, Views.translate(r, 0, 0, -1000), Views.translate(mask, 0, 0, -1000));
		final CachedCellImg<UnsignedByteType, ?> composite1 = Lazy.generate(source, new int[] {128, 128, 1}, new UnsignedByteType(), AccessFlags.setOf(AccessFlags.VOLATILE), composeOp1);
		final ComposeOp composeOp2 = new ComposeOp(composite1, Views.translate(r, 0, 0, -2000), Views.translate(mask, 0, 0, -2000));
		final CachedCellImg<UnsignedByteType, ?> composite2 = Lazy.generate(source, new int[] {128, 128, 1}, new UnsignedByteType(), AccessFlags.setOf(AccessFlags.VOLATILE), composeOp2);

//		BdvFunctions.show(source, null, "");
//		BdvFunctions.show(VolatileViews.wrapAsVolatile(composite2, queue), "mask");
		BdvFunctions.show(rV, "red");


		return null;
	}

}
