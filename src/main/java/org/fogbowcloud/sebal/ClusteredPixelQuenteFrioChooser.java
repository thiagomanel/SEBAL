package org.fogbowcloud.sebal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.fogbowcloud.sebal.model.image.Image;
import org.fogbowcloud.sebal.model.image.ImagePixel;
import org.fogbowcloud.sebal.model.image.ImagePixelOutput;
import org.fogbowcloud.sebal.model.image.NDVIComparator;
import org.fogbowcloud.sebal.model.image.TSComparator;
import org.python.google.common.primitives.Doubles;


public class ClusteredPixelQuenteFrioChooser extends AbstractPixelQuenteFrioChooser {

	private int clusterWidth = 5;  			// 5 is default value
	private int clusterHeight = 5; 			// 5 is default value
	private double maxCVForNDVI = 0.2; 		// 20% is default value
	private int maxInvalidNDVIValues = 10;	// 10 is default value
	private int minTotalWater = 1;			// 1 is default value
	private int minLatWater = 1;			// 1 is default value
	private int minLonWater = 1;			// 1 is default value
	private double maxDiffFromTSMean = 0.2;	// 0.2 is default value
	private double maxDiffFromAlbedoMean = 0.02; // 0.02 is default value
	
	public ClusteredPixelQuenteFrioChooser() {
		
	}
	
	public ClusteredPixelQuenteFrioChooser(Properties properties) {
		if (properties.getProperty("cluster_width") != null) {
			clusterWidth = Integer.parseInt(properties.getProperty("cluster_width"));
		}

		if (properties.getProperty("cluster_height") != null) {
			clusterHeight = Integer.parseInt(properties.getProperty("cluster_height"));
		}

		if (properties.getProperty("cluster_max_cv_for_ndvi") != null) {
			maxCVForNDVI = Double.parseDouble(properties.getProperty("cluster_max_cv_for_ndvi"));
		}

		if (properties.getProperty("cluster_max_invalid_ndvi") != null) {
			maxInvalidNDVIValues = Integer.parseInt(properties
					.getProperty("cluster_max_invalid_ndvi"));
		}

		if (properties.getProperty("cluster_min_total_water_pixels") != null) {
			minTotalWater = Integer.parseInt(properties
					.getProperty("cluster_min_total_water_pixels"));
		}

		if (properties.getProperty("cluster_min_lat_water_pixels") != null) {
			minLatWater = Integer.parseInt(properties.getProperty("cluster_min_lat_water_pixels"));
		}

		if (properties.getProperty("cluster_min_lon_water_pixels") != null) {
			minLonWater = Integer.parseInt(properties.getProperty("cluster_min_lon_water_pixels"));
		}

		if (properties.getProperty("cluster_max_difference_from_ts_mean") != null) {
			maxDiffFromTSMean = Double.parseDouble(properties
					.getProperty("cluster_max_difference_from_ts_mean"));
		}
		
		if (properties.getProperty("cluster_max_difference_from_albedo_mean") != null) {
			maxDiffFromAlbedoMean  = Double.parseDouble(properties
					.getProperty("cluster_max_difference_from_albedo_mean"));
		}
	}

	@Override
	public void choosePixelsQuenteFrio(Image image) {
		System.out.println("image is null? " + (image == null));
		long now = System.currentTimeMillis();
		ImagePixel pixelFrioInTheWater = findPixelFrioInTheWater(image);
		
		System.out.println("PixelFrioInTheWater Time=" + (System.currentTimeMillis() - now));
		now = System.currentTimeMillis();
		List<ImagePixel> pixelFrioCandidates = new ArrayList<ImagePixel>();		
		List<ImagePixel> pixelQuenteCandidates = new ArrayList<ImagePixel>();
		
		for (int x0 = 0; x0 < image.width(); x0 += clusterWidth) {
			for (int y0 = 0; y0 < image.height(); y0 += clusterHeight) {
				List<ImagePixel> cluster = createCluster(image.pixels(),
						linear(x0, y0, image.width()), image.width(),
						Math.min(clusterWidth, image.width() - x0),
						Math.min(clusterHeight, image.height() - y0));

				proccessCluster(pixelFrioCandidates, pixelQuenteCandidates, cluster);
			}
		}
		
		System.out.println("ProcessingClusters Time=" + (System.currentTimeMillis() - now));
		now = System.currentTimeMillis();
		
		selectPixelFrioOutOfWater(pixelFrioInTheWater, pixelFrioCandidates);
		selectPixelQuente(pixelQuenteCandidates);
		
		System.out.println("Selecting Time=" + (System.currentTimeMillis() - now));
		
		if (pixelFrio != null) {
			System.out.println("PixelFrio: " + pixelFrio.output().getTs());
		}
		if (pixelQuente != null) {
			System.out.println("PixelQuente: " + pixelQuente.output().getTs());
		}
	}

	private int linear(int x, int y, int width) {
		return x + y * width;
	}

	private void proccessCluster(List<ImagePixel> pixelFrioCandidates,
			List<ImagePixel> pixelQuenteCandidates, List<ImagePixel> cluster) {

		double CVForNDVI = calcCVForNDVI(cluster);

		if (CVForNDVI < maxCVForNDVI) {
			pixelFrioCandidates.addAll(cluster);
			pixelQuenteCandidates.addAll(cluster);
		}
	}
	
	private double calcCVForNDVI(List<ImagePixel> cluster) {
		List<Double> validNDVIValues = new ArrayList<Double>();
		int invalidNDVIValues = 0;
		for (int index = 0; index < cluster.size(); index++) {
			ImagePixelOutput pixelOutput = cluster.get(index).output();
			if (pixelOutput.isCloud() || pixelOutput.getNDVI() <= 0) {
				invalidNDVIValues++;
				if (invalidNDVIValues == maxInvalidNDVIValues) {
					return 1;
				}
				continue;
			}
			validNDVIValues.add(cluster.get(index).output().getNDVI());
		}
		
		double mean = calcMean(Doubles.toArray(validNDVIValues));
		double variance = new Variance().evaluate(Doubles.toArray(validNDVIValues));
		double standarDeviation = Math.sqrt(variance);
		
		return standarDeviation / mean;
	}

	private ImagePixel findPixelFrioInTheWater(Image image) {		
		Map<String, PixelSample> waterSamples = findWater(image);
		if (waterSamples.isEmpty()) {
			return null;
		}		
		refineSamples(waterSamples);		
		PixelSample bestSample = selectBestSample(waterSamples);
		
		if (bestSample == null) {
			return null;
		}
		return selectPixelFrioInTheWater(bestSample);
	}

	private ImagePixel selectPixelFrioInTheWater(PixelSample waterSample) {	
		double[] tsValues = new double[waterSample.pixels().size()];
		for (int index = 0; index < waterSample.pixels().size(); index++) {
			tsValues[index] = waterSample.pixels().get(index).output().getTs();
		}
		double mean = calcMean(tsValues);
	
		for (ImagePixel pixel : waterSample.pixels()) {
			if (pixel.output().getTs() >= (mean - maxDiffFromTSMean)
					&& pixel.output().getTs() <= (mean + maxDiffFromTSMean)) {
				return pixel;
			}
		}
		return null;
	}

	private void refineSamples(Map<String, PixelSample> waterSamples) {
		Collection<String> keys = new ArrayList<String>(waterSamples.keySet());
		for (String key : keys) {
			PixelSample pixelSample = waterSamples.get(key);
			if (pixelSample.pixels().size() < minTotalWater
					|| pixelSample.getNumberOfLonPixels() < minLonWater
					|| pixelSample.getNumberOfLatPixels() < minLatWater) {
				waterSamples.remove(key);
			}
		}
	}

	protected PixelSample selectBestSample(Map<String, PixelSample> samples) {
		PixelSample bestSample = null;
		for (PixelSample sample : samples.values()) {
			if (bestSample == null || (sample.pixels().size() > bestSample.pixels().size())) {
				bestSample = sample;
			}
		}
		return bestSample;
	}

	protected Map<String, PixelSample> findWater(Image image) {
		Map<String, PixelSample> samples = new HashMap<String, PixelSample>();
		List<ImagePixel> pixels = image.pixels();
		System.out.println("pixels size=" + pixels.size());
		boolean[] visited = new boolean[pixels.size()];
		
		for (int i = 0; i < image.width(); i++) {
			for (int j = 0; j < image.height(); j++) {
				if (visited[linear(i, j, image.width())] == true) {
					continue;
				}

				ImagePixelOutput pixelOutput = pixels.get(linear(i, j, image.width())).output();
				if (!pixelOutput.isCloud() && pixelOutput.getWaterTest()) {
					findWater(pixels, visited, i, j, image.width(), image.height(), i + "_" + j,
							samples);
				}
			}
		}
		System.out.println("amount of water samples=" + samples.size());
		return samples;
	}

	private void findWater(List<ImagePixel> pixels, boolean[] visited, int i, int j, int width,
			int height, String sampleId, Map<String, PixelSample> samples) {
		if (i < 0 || i > width -1 || j < 0 || j > height -1) {
			return;
		}
		
		if (visited[linear(i, j, width)] == true) {
			return;
		}
		visited[linear(i, j, width)] = true;

		if (pixels.get(linear(i, j, width)).output().getNDVI() < 0) {
			if (!samples.containsKey(sampleId)) {				
				samples.put(sampleId, new PixelSample());
			}
			samples.get(sampleId).addPixel(pixels.get(linear(i, j, width)), i, j);
		} else {
			return;
		}
		
		findWater(pixels, visited, i + 1, j, width, height, sampleId, samples);
		findWater(pixels, visited, i - 1, j, width, height, sampleId, samples);
		findWater(pixels, visited, i, j + 1, width, height, sampleId, samples);
		findWater(pixels, visited, i, j - 1, width, height, sampleId, samples);
	}

	private void selectPixelQuente(List<ImagePixel> pixelQuenteCandidates) {
		/*
		 * Choosing pixel quente 
		 * Pixel Quente candidates: 10% smallest NDVI and 20% biggest TS
		 */
		System.out.println("amount of pixelQuenteCandidates=" + pixelQuenteCandidates.size());
		pixelQuenteCandidates = filterSmallestNDVI(pixelQuenteCandidates, 10);
		pixelQuenteCandidates = filterBiggestTS(pixelQuenteCandidates, 20);
				
		double[] tsValuesQuenteCandidates = new double[pixelQuenteCandidates.size()];
		for (int i = 0; i < pixelQuenteCandidates.size(); i++) {
			tsValuesQuenteCandidates[i] = pixelQuenteCandidates.get(i).output().getTs();
		}
		double tsQuenteMean = calcMean(tsValuesQuenteCandidates);
		for (ImagePixel pixel : pixelQuenteCandidates) {
			if (pixel.output().getTs() >= (tsQuenteMean - maxDiffFromTSMean)
					&& pixel.output().getTs() <= (tsQuenteMean + maxDiffFromTSMean)){
				pixelQuente = pixel;
				break;
			}
		}
	}

	private void selectPixelFrioOutOfWater(ImagePixel pixelFrioInTheWater,
			List<ImagePixel> pixelFrioCandidates) {
		System.out.println("amount of pixelFrioCandidates=" + pixelFrioCandidates.size());
		/*
		 * Choosing pixel frio out of the water 
		 * Pixel Frio candidates: 5% biggest NDVI and 20% smallest TS
		 */
		pixelFrioCandidates = filterBiggestNDVI(pixelFrioCandidates, 5);
		pixelFrioCandidates = filterSmallestTS(pixelFrioCandidates, 20);
		
		double[] tsValuesFrioCandidates = new double[pixelFrioCandidates.size()];
		double[] alphaValuesFrioCandidates = new double[pixelFrioCandidates.size()];
		for (int i = 0; i < pixelFrioCandidates.size(); i++) {
			tsValuesFrioCandidates[i] = pixelFrioCandidates.get(i).output().getTs();
			alphaValuesFrioCandidates[i] = pixelFrioCandidates.get(i).output().getAlpha();
		}
		double tsFrioMean = calcMean(tsValuesFrioCandidates);
		double alphaFrioMean = calcMean(alphaValuesFrioCandidates);
		ImagePixel pixelFrioOutOfWater = null;
		for (ImagePixel pixel : pixelFrioCandidates) {
			if (pixel.output().getTs() >= (tsFrioMean - maxDiffFromTSMean)
					&& pixel.output().getTs() <= (tsFrioMean + maxDiffFromTSMean)
					&& pixel.output().getAlpha() >= (alphaFrioMean - maxDiffFromAlbedoMean)
					&& pixel.output().getAlpha() <= (alphaFrioMean + maxDiffFromAlbedoMean)) {
				pixelFrioOutOfWater = pixel;
				break;
			}
		}
		if (pixelFrioInTheWater != null) {
			pixelFrio = (pixelFrioInTheWater.output().getTs() < pixelFrioOutOfWater.output()
					.getTs() ? pixelFrioInTheWater : pixelFrioOutOfWater);
		} else {
			pixelFrio = pixelFrioOutOfWater;
		}
	}
	
	protected List<ImagePixel> filterBiggestTS(List<ImagePixel> pixels, double percent) {
		Collections.sort(pixels, new TSComparator());
		Collections.reverse(pixels);
		List<ImagePixel> percentBiggestTS = new ArrayList<ImagePixel>();
		for (int index = 0; index < Math.round(pixels.size() * (percent / 100) + 0.4); index++) {
			percentBiggestTS.add(pixels.get(index));
		}
		return percentBiggestTS;
	}

	protected List<ImagePixel> filterSmallestNDVI(List<ImagePixel> pixels, double percent) {
		Collections.sort(pixels, new NDVIComparator());
		
		//excluding pixels where ndvi is 0
		for (ImagePixel imagePixel : new ArrayList<ImagePixel>(pixels)) {
			if (imagePixel.output().getNDVI() <= 0) {
				pixels.remove(imagePixel);
			}
		}
		
		List<ImagePixel> percentSmallestNDVI = new ArrayList<ImagePixel>();
		for (int index = 0; index < Math.round(pixels.size() * (percent / 100) + 0.4); index++) {
			percentSmallestNDVI.add(pixels.get(index));
		}
		return percentSmallestNDVI;
	}

	protected List<ImagePixel> filterSmallestTS(List<ImagePixel> pixels, double percent) {
		Collections.sort(pixels, new TSComparator());
		List<ImagePixel> percentSmallestTS = new ArrayList<ImagePixel>();
		for (int index = 0; index < Math.round(pixels.size() * (percent / 100) + 0.4); index++) {
			percentSmallestTS.add(pixels.get(index));
		}
		return percentSmallestTS;
	}

	protected List<ImagePixel> filterBiggestNDVI(List<ImagePixel> pixels, double percent) {
		Collections.sort(pixels, new NDVIComparator());
		Collections.reverse(pixels);
		List<ImagePixel> percentBiggestNDVI = new ArrayList<ImagePixel>();
		for (int index = 0; index < Math.round(pixels.size() * (percent / 100) + 0.4); index++) {
			percentBiggestNDVI.add(pixels.get(index));
		}
		return percentBiggestNDVI;
	}

	private double calcMean(double[] values) {
		double total = 0d;		
		for (int i = 0; i < values.length; i++) {
			total += values[i];
		}
		return total / values.length;
	}

	protected List<ImagePixel> createCluster(List<ImagePixel> pixels, int firstIndex, int imageWidth,
			int clusterWidth, int clusterHeight) {
		List<ImagePixel> cluster = new ArrayList<ImagePixel>();
		for (int i = firstIndex; i < firstIndex + clusterWidth; i++) {
			for (int j = 0; j < clusterHeight; j++) {
				cluster.add(pixels.get(i + j * imageWidth));
			}
		}
		return cluster;
	}
}
