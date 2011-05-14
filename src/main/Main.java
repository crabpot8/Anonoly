package main;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import data.RectilinearPixelPoly;
import data.Regions;

public class Main {

	public static final Logger log = Logger.getLogger(Main.class
			.getCanonicalName());

	public static final int K = 10;
	static List<Point> randomReadings = new ArrayList<Point>();

	public static void main(String[] args) {

		Regions r = new Regions(new Dimension(50, 50));
		r.resetDataReadingCount();
		r.resetRegionUsage();

		Random rand = new Random();
		rand.setSeed(10);
		for (int i = 0; i < 100; i++)
			randomReadings.add(new Point(rand.nextInt(50), rand.nextInt(50)));

		// for (int x = 0; x < 3; x++)
		// for (int y = 0; y < 3; y++)
		// randomReadings.add(new Point(x, y));

		log.info("Generated Random Data Reading Locations");

		int cycle = 0;
		while (true) {

			log.info("Cycle is " + cycle++);
			printImage(r, cycle);

			// Add data readings
			r.resetDataReadingCount();
			for (Point p : randomReadings)
				r.addDataReading(p);
			log.info("Added data reading locations");

			// Order and reset usage
			r.orderRegions(OptimialityRanking);
			r.resetRegionUsage();
			log.info("Ordered regions and reset usage data");

			log.info("Running algorithm");
			runAlgorithm(r);

		}
	}

	private static void printImage(Regions r, int cycle) {
		BufferedImage bi = r.getImage();
		StringBuilder name = new StringBuilder("images/cycle");
		if (cycle < 10)
			name.append("00").append(cycle).append(".png");
		else if (cycle > 9 && cycle < 100)
			name.append("0").append(cycle).append(".png");
		else
			name.append(cycle).append(".png");

		try {
			File f = new File(name.toString());
			ImageIO.write(bi, "png", f);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void runAlgorithm(Regions reg) {
		List<RectilinearPixelPoly> regions = reg.getRegions();

		// We use a copy of the original list so that Java does not complain
		// about concurrent modification exceptions (our calls to regions inside
		// of this loop can end up modifying the list aka a split will modify
		// the list)
		List<RectilinearPixelPoly> regionsCopy = new ArrayList<RectilinearPixelPoly>(
				regions);

		for (RectilinearPixelPoly poly : regionsCopy) {

			if (poly.getIsUsed())
				continue;

			log.info("Next region: " + poly);

			int count = poly.getDataReadingCount();
			if (count == K) {
				log.info("Needs no attention");
				poly.setIsUsed(true);
				continue;
			}

			// We are not large enough, time to merge!
			if (count < K) {
				log.info("Needs to merge");
				List<RectilinearPixelPoly> neighbors = reg.findNeighbors(poly);

				// Remove all used neighbors
				Iterator<RectilinearPixelPoly> it = neighbors.iterator();
				while (it.hasNext())
					if (it.next().getIsUsed())
						it.remove();

				log.fine("Found " + neighbors.size() + " neighbors");
				Collections.sort(neighbors, OptimialityRanking);
				log.fine("Sorted neighbors");

				if (log.isLoggable(Level.FINEST))
					for (RectilinearPixelPoly p : neighbors)
						log.finest("\t" + p);

				int desiredChange = getDesiredChange(K - count, poly.getArea());
				log.fine("Needs to grow by " + desiredChange);

				for (RectilinearPixelPoly resource : neighbors) {
					log.finest("Considering Neighbor " + resource);

					// We only want to consume from neighbors that need to
					// shrink
					// if (resource.getDataReadingCount() < K &&
					// resource.getArea() > change) {
					// log.finest("Neighbor is unusable (they will likely consume us!)");
					// continue;
					// }

					if (resource.getDataReadingCount() + count <= K)
						desiredChange = resource.getArea();

					// If desired change is greater than or equal to the area
					// available, we can rest assured that the consumeArea
					// method will not fail
					if (desiredChange >= resource.getArea()) {
						Point start = poly.getStartPoint(resource);
						Collection<Point> points = resource.consumeArea(
								desiredChange, start);
						poly.merge(points);
						desiredChange -= points.size();

						log.fine("Got " + points.size()
								+ " from neighbor, don't need more");
					} else {
						// If desired change is less than the area available, we
						// have to be sure the consumeArea method does not fail
						int consumedSoFar = 0;
						int origDesiredChange = desiredChange;
						do {

							Point start = poly.getStartPoint(resource);
							Collection<Point> consumable = resource
									.consumeArea(desiredChange, start);
							consumedSoFar += consumable.size();
							poly.merge(consumable);
							desiredChange -= consumable.size();

						} while (consumedSoFar < origDesiredChange);

						log.fine("Got " + consumedSoFar
								+ " from neighbor, still needs "
								+ desiredChange);
					}

					// Mark the region we used as used
					resource.setIsUsed(true);

					// If we fully eat a poly we can shrink below our expected
					// change
					if (desiredChange <= 0)
						break;

				}

				poly.setIsUsed(true);

				continue;
			}

			// We are too large, time to shrink!
			if (count > K) {
				log.info("Needs to shrink");
				int change = getDesiredChange(K - count, poly.getArea());
				log.fine("Needs to shrink by " + change);
				poly.split(change);

				poly.setIsUsed(true);
			}

		} // End r.hasNext

	}

	public static int getDesiredChange(int differenceFromK, int currentArea) {
		// For now it's just a simple scaling
		if (differenceFromK > 0) {
			double diffPerct = (double) differenceFromK / (double) K;
			diffPerct += 1;

			double finalArea = (double) currentArea * diffPerct;
			double change = finalArea - currentArea;
			return (int) change;
		}

		if (differenceFromK < 0) {
			int total = -1 * differenceFromK + K;
			double diffPerct = (double) K / (double) total;

			double finalArea = (double) currentArea * (diffPerct);
			double change = currentArea - finalArea;
			return Math.max((int) change, 0);
		}

		throw new IllegalStateException();
	}

	static Comparator<RectilinearPixelPoly> OptimialityRanking = new Comparator<RectilinearPixelPoly>() {

		@Override
		public int compare(RectilinearPixelPoly o1, RectilinearPixelPoly o2) {
			int rc1 = o1.getDataReadingCount();
			int rc2 = o2.getDataReadingCount();

			if (rc1 < K && rc2 > K)
				return -1;
			else if (rc2 < K && rc1 > K)
				return 1;
			else if (rc2 == rc1)
				return 0;
			else if (rc1 < K && rc2 == K)
				return -1;
			else if (rc1 > K && rc2 == K)
				return -1;
			else if (rc2 < K && rc1 == K)
				return 1;
			else if (rc2 > K && rc1 == K)
				return 1;

			// At this point they are either both >K or both <K, but not equal

			// Are they <K
			if (rc1 < K) {
				if (rc1 < rc2)
					return -1;
				else
					return 1;
			}

			// Are they >K
			if (rc1 > K) {
				if (rc1 < rc2)
					return 1;
				else
					return -1;
			}

			throw new IllegalStateException();
		}
	};

	// static Comparator<RectilinearPixelPoly> MergeRanking = new
	// Comparator<RectilinearPixelPoly>() {
	// @Override
	// public int compare(RectilinearPixelPoly o1, RectilinearPixelPoly o2) {
	// // Return -1 if o1 should be more to the front, 1 if o2 shoudl be more,
	// etc
	//			
	// int o1count = o1.getDataReadingCount();
	// int o2count =
	//			
	// if ()
	//			
	// return 0;
	// }
	//
	// };
}
