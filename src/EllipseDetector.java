
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Eric Farng on 9/23/2017.
 *
 * This is a library to detect ellipses within an image.
 *
 * This is a java implementation of this paper:
 * [1] A fast and effective ellipse detector for embedded vision applications
 * Michele Fornaciariab, Andrea Pratibc, Rita Cucchiaraab
 * http://ieeexplore.ieee.org/document/6470150/
 *
 * Also used its reference implementation in c++
 * https://sourceforge.net/projects/yaed/
 *
 * This is also uses options from this paper
 * [2] A Fast Ellipse Detector Using Projective Invariant Pruning
 * Qi Jia, Xin Fan, Member, IEEE, Zhongxuan Luo, Lianbo Song, and Tie Qiu
 * http://ieeexplore.ieee.org/document/7929406/
 *
 * This is released into the public domain with no restrictions or guarantees.
 * This is intended for single threaded use only.
 *
 * Usage:
 *
 * CannyEdgeDetector cannyEdgeDetector = new CannyEdgeDetector();
 * // any parameters you want, these are the default
 * cannyEdgeDetector.setLowThreshold(2.5f);
 * cannyEdgeDetector.setHighThreshold(7.5f);
 * cannyEdgeDetector.setGaussianKernelRadius(2);
 * cannyEdgeDetector.setGaussianKernelWidth(16);
 * //apply it to an image
 * detector.setSourceImage(sourceImage);
 * detector.process();
 * BufferedImage edgeImage = detector.getEdgeImage();

 * EllipseDetector ellipseDetector = new EllipseDetector();
 * ellipseDetector.setEdgeImage(edgeImage);
 * ellipseDetector.process();
 * BufferedImage ellipseImage = detector.getEllipseImage();
 *
 */

public class EllipseDetector {

    // TH_length: minimum pixel count of arc. default=16
    private int minimumArcPixelCount = 16;

    // TH_obb: minimum height/imageWidth of bounding box avoiding straight lines. default = 3
    private int minimumBoundingBoxSize = 3;

    // if true, check all arc points for distance from diagonal
    // if false, check midpoint, 25%, 75% point for distnace from diagonal
    // not checking all points is inspired from [2]
    private boolean checkAllArcPointsForStraightLine = false;

    // TH_pos: percent tolerance when comparing top/bottom/left/right sides of arcs going past each other
    // pixel tolerance = min(image height, image imageWidth) * minimumArcSizePercent; default = 1
    private int mutualPositionBoundingBoxPixelTolerance = 1;

    // N_s: number of parallel chords to determine implied center of two arcs, default=16
    private int numberOfParallelChords = 16;

    // TH_centers: how far apart two centers can be and still be considered the same, default = 0.05
    // this is percent of image diagonal length
    private float centerDistancePercent = 0.05f;

    // distance from point to ellipse to count as on the ellipse, paper default = 0.1
    // on the synthetic test case here, using 0.5f has much better results, I don't know why
    private float distanceToEllipseContour = 0.5f;

    // TH_score : percent of number of points on ellipse, within distanceToEllipseContour, default = 0.4
    private float distanceToEllipseContourScoreCutoff = 0.4f;

    // additional metric to determine if ellipse is good, default = 0.4
    private float reliabilityCutoff = 0.4f;

    private List<Ellipse> ellipseSharedCenterList;

    private List<Ellipse> ellipseOnEdgePointList;

    private List<Ellipse> ellipseOnEdgedPointDeduplicatedList;

    // use median or mean to estimate center
    // [1] uses median. [2] uses mean
    private boolean useMedianCenter = true;

    private int totalLineSegmentCount;
    private int shortLineCount;
    private int straightLineCount;


    // https://sashat.me/2017/01/11/list-of-20-simple-distinct-colors/
    private int[] colorList = new int[] {
            getIntRGB(255, 230, 25, 75),
            getIntRGB(255, 60, 180, 75),
            getIntRGB(255, 255, 225, 25),
//            getIntRGB(255, 0, 130, 200),
            getIntRGB(255, 245, 130, 48),
//            getIntRGB(255, 145, 30, 180),
            getIntRGB(255, 70, 240, 240),
            getIntRGB(255, 240, 50, 230),
            getIntRGB(255, 210, 245, 60),
            getIntRGB(255, 250, 190, 190),
//            getIntRGB(255, 0, 128, 128),
            getIntRGB(255, 230, 190, 255),
            getIntRGB(255, 170, 110, 40),
            getIntRGB(255, 255, 250, 200),
//            getIntRGB(255, 128, 0, 0),
            getIntRGB(255, 170, 255, 195),
            getIntRGB(255, 128, 128, 0),
            getIntRGB(255, 255, 215, 180),
//            getIntRGB(255, 0, 0, 128),
//            getIntRGB(255, 128, 128, 128),
            getIntRGB(255, 255, 255, 255),
            getIntRGB(255, 0, 0, 0)
    };

    private static float pi = 3.14159265359f;

    private BufferedImage sourceImage = null;
    private CannyEdgeDetector edgeImage = null;

    private List<EllipseQuarterArc> positiveGradientArcLabelList;
    private List<EllipseQuarterArc> negativeGradientArcLabelList;

    private List<EllipseQuarterArc> positiveConvexityArcLabelList;
    private List<EllipseQuarterArc> negativeConvexityArcLabelList;

    private List<EllipseQuarterArc> quadrantOneArcLabelList;
    private List<EllipseQuarterArc> quadrantTwoArcLabelList;
    private List<EllipseQuarterArc> quadrantThreeArcLabelList;
    private List<EllipseQuarterArc> quadrantFourArcLabelList;

    public EllipseDetector() {
    }
    public EllipseDetector(BufferedImage bufferedImage) {
        this.sourceImage = bufferedImage;
    }

    public void setEdgeImage(CannyEdgeDetector bufferedImage) {
        this.edgeImage = bufferedImage;
    }
    public void setSourceImage(BufferedImage bufferedImage) {
        this.sourceImage = bufferedImage;
    }
    public void setMinimumArcPixelCount(int minimumArcPixelCount) {
        this.minimumArcPixelCount = minimumArcPixelCount;
    }

    public void setMinimumBoundingBoxSize(int minimumBoundingBoxSize) {
        this.minimumBoundingBoxSize = minimumBoundingBoxSize;
    }

    public void setMutualPositionBoundingBoxPixelTolerance(int mutualPositionBoundingBoxPixelTolerance) {
        this.mutualPositionBoundingBoxPixelTolerance = mutualPositionBoundingBoxPixelTolerance;
    }

    public void setNumberOfParallelChords(int numberOfParallelChords) {
        this.numberOfParallelChords = numberOfParallelChords;
    }

    public void setCenterDistancePercent(float centerDistancePercent) {
        this.centerDistancePercent = centerDistancePercent;
    }

    public void setDistanceToEllipseContour(float distanceToEllipseContour) {
        this.distanceToEllipseContour = distanceToEllipseContour;
    }

    public void setDistanceToEllipseContourScoreCutoff(float distanceToEllipseContourScoreCutoff) {
        this.distanceToEllipseContourScoreCutoff = distanceToEllipseContourScoreCutoff;
    }

    public void setReliabilityCutoff(float reliabilityCutoff) {
        this.reliabilityCutoff = reliabilityCutoff;
    }

    public void setUseMedianCenter(boolean useMedianCenter) {
        this.useMedianCenter = useMedianCenter;
    }

    public void setCheckAllArcPointsForStraightLine(boolean checkAllArcPointsForStraightLine) {
        this.checkAllArcPointsForStraightLine = checkAllArcPointsForStraightLine;
    }

    public void process() {
        long start = System.currentTimeMillis();
        System.out.println("Starting process() : " + start);
        if (sourceImage == null && edgeImage == null) {
            throw new NullPointerException("source and edge image are both null");
        }
        if (edgeImage == null) {
            this.edgeImage = new CannyEdgeDetector();
            edgeImage.setSourceImage(sourceImage);
            edgeImage.process();
        }
        System.out.println("Finished detecting edges : " + (System.currentTimeMillis() - start));
        findConnectedArcSameGradient();
        System.out.println("Finished finding arcs with same gradients : " + (System.currentTimeMillis() - start));
        findArcConvexity();
        System.out.println("Finished calculating convexity : " + (System.currentTimeMillis() - start));
        ellipseSharedCenterList = findArcTripletsQ1Q2Q3();
        ellipseSharedCenterList.addAll(findArcTripletsQ2Q3Q4());
        ellipseSharedCenterList.addAll(findArcTripletsQ3Q4Q1());
        ellipseSharedCenterList.addAll(findArcTripletsQ4Q1Q2());
        System.out.println("Finished finding matching triples : " + (System.currentTimeMillis() - start));

        parameterEstimation(ellipseSharedCenterList);
        System.out.println("Finished parameter estimation : " + (System.currentTimeMillis() - start));

        // calculate points on ellipse and somethign called reliability
        // return on the ones that meet the threshold
        ellipseOnEdgePointList = calculatePointsOnEllipse(ellipseSharedCenterList);
        System.out.println("Finished scoring triplets : " + (System.currentTimeMillis() - start));

        // merge/deduplicate ellipses
        ellipseOnEdgedPointDeduplicatedList = clusterEllipse(ellipseOnEdgePointList);
        System.out.println("Finished clustering triplets : " + (System.currentTimeMillis() - start));

    }

    // part 3.1.2 of paper
    private void findConnectedArcSameGradient() {
        HashMap<Integer, Integer> pointArcLabelMap = new HashMap<>();
        List<Integer> edgePointList = new ArrayList<>();
        HashMap<Integer, Integer> arcLabelEquivalenceMap = new HashMap<>();

        final int imageHeight = edgeImage.getEdgeImage().getHeight();
        final int imageWidth = edgeImage.getEdgeImage().getWidth();
        int[] data = edgeImage.getEdgeData();
        float[] xGradient = edgeImage.getXGradient();
        float[] yGradient = edgeImage.getYGradient();

        // label all arcs
        int currentOffset = 0;
        int nextArcLabel = 0;
        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                if (data[currentOffset] == -1 && y > 0 && x > 0 && y < (imageHeight - 1) && x < (imageWidth - 1)) {
                    edgePointList.add(currentOffset);
                    if (xGradient[currentOffset] != 0 && yGradient[currentOffset] != 0) {

                        int currentGradient = (int) Math.signum(xGradient[currentOffset]) * (int) Math.signum(yGradient[currentOffset]);
                        int currentArcLabel = Integer.MAX_VALUE;

                        // top left
                        int connectivityOffset = currentOffset - imageWidth - 1;
                        int connectivityData = data[connectivityOffset];
                        int connectivityGradient, connectivityArcLabel;
                        if (connectivityData == -1) {
                            connectivityGradient = (int) Math.signum(xGradient[connectivityOffset]) * (int) Math.signum(yGradient[connectivityOffset]);
                            if (currentGradient == connectivityGradient) {
                                connectivityArcLabel = pointArcLabelMap.get(connectivityOffset);
                                if (connectivityArcLabel < currentArcLabel) {
                                    arcLabelEquivalenceMap.put(currentArcLabel, connectivityArcLabel);
                                    currentArcLabel = connectivityArcLabel;
                                }
                            }
                        }

                        // top
                        connectivityOffset++;
                        connectivityData = data[connectivityOffset];
                        if (connectivityData == -1) {
                            connectivityGradient = (int) Math.signum(xGradient[connectivityOffset]) * (int) Math.signum(yGradient[connectivityOffset]);
                            if (currentGradient == connectivityGradient) {
                                connectivityArcLabel = pointArcLabelMap.get(connectivityOffset);
                                if (connectivityArcLabel < currentArcLabel) {
                                    arcLabelEquivalenceMap.put(currentArcLabel, connectivityArcLabel);
                                    currentArcLabel = connectivityArcLabel;
                                } else if (currentArcLabel < connectivityArcLabel) {
                                    arcLabelEquivalenceMap.put(connectivityArcLabel, currentArcLabel);
                                }
                            }
                        }

                        // top right
                        connectivityOffset++;
                        connectivityData = data[connectivityOffset];
                        if (connectivityData == -1) {
                            connectivityGradient = (int) Math.signum(xGradient[connectivityOffset]) * (int) Math.signum(yGradient[connectivityOffset]);
                            if (currentGradient == connectivityGradient) {
                                connectivityArcLabel = pointArcLabelMap.get(connectivityOffset);
                                if (connectivityArcLabel < currentArcLabel) {
                                    arcLabelEquivalenceMap.put(currentArcLabel, connectivityArcLabel);
                                    currentArcLabel = connectivityArcLabel;
                                } else if (currentArcLabel < connectivityArcLabel) {
                                    arcLabelEquivalenceMap.put(connectivityArcLabel, currentArcLabel);
                                }
                            }
                        }


                        // left
                        connectivityOffset = currentOffset - 1;
                        connectivityData = data[connectivityOffset];
                        if (connectivityData == -1) {
                            connectivityGradient = (int) Math.signum(xGradient[connectivityOffset]) * (int) Math.signum(yGradient[connectivityOffset]);
                            if (currentGradient == connectivityGradient) {
                                connectivityArcLabel = pointArcLabelMap.get(connectivityOffset);
                                if (connectivityArcLabel < currentArcLabel) {
                                    arcLabelEquivalenceMap.put(currentArcLabel, connectivityArcLabel);
                                    currentArcLabel = connectivityArcLabel;
                                } else if (currentArcLabel < connectivityArcLabel) {
                                    arcLabelEquivalenceMap.put(connectivityArcLabel, currentArcLabel);
                                }
                            }
                        }

                        if (currentArcLabel == Integer.MAX_VALUE) currentArcLabel = ++nextArcLabel;

                        pointArcLabelMap.put(currentOffset, currentArcLabel);


                    }

                }
                currentOffset++;
            }
        }

        // compress arcLabelEquivalenceMap to point to lowest value label
        for (int i = nextArcLabel; i > 0; i--) {
            Integer equivalence = arcLabelEquivalenceMap.get(i);
            if (equivalence != null) {
                Integer lastEquivalence = equivalence;
                while (equivalence != null) {
                    lastEquivalence = equivalence;
                    equivalence = arcLabelEquivalenceMap.get(equivalence);
                }
                arcLabelEquivalenceMap.put(i, lastEquivalence);
            }
        }

        // create arc objects
        Map<Integer, EllipseQuarterArc> arcMap = new HashMap<>(nextArcLabel - arcLabelEquivalenceMap.size());
        for (int point: edgePointList) {
            Integer arcLabel = pointArcLabelMap.get(point);
            if (arcLabel != null) {
                Integer equivalenceArcLabel = arcLabelEquivalenceMap.get(arcLabel);
                if (equivalenceArcLabel != null) {
                    arcLabel = equivalenceArcLabel;
                }
                EllipseQuarterArc arc = arcMap.get(arcLabel);
                if (arc == null) {
                    arc = new EllipseQuarterArc(imageWidth);
                    arcMap.put(arcLabel, arc);
                }
                arc.addPoint(point);
            }
        }

        straightLineCount = shortLineCount = 0;
        totalLineSegmentCount = arcMap.keySet().size();

        // remove small arc objects and straight lines, then sort point list
        positiveGradientArcLabelList = new ArrayList<>(arcMap.size());
        negativeGradientArcLabelList = new ArrayList<>(arcMap.size());
        for (int arcLabel: new ArrayList<>(arcMap.keySet())) {
            final EllipseQuarterArc arc = arcMap.get(arcLabel);
            if (arc.pointList.size() < minimumArcPixelCount) {
                // too small
                arcMap.remove(arcLabel);
                shortLineCount++;
            } else if (arc.isCurvedLine() == false) {
                // remove straight lines
                arcMap.remove(arcLabel);
                straightLineCount++;
            } else {
                // sort in this order for convexity algorithm to work (part 3.1.3)
                Collections.sort(arc.pointList, new Comparator<short[]>() {
                    @Override
                    public int compare(short[] o1, short[] o2) {
                        if (o1[0] == o2[0]) {
                            return o1[1] - o2[1];
                        }
                        return o1[0] - o2[0];
                    }
                });

                // for some reason, gradient is backwards, multiply by -1 (I think because y-axis is on the top?)
                int gradient = -1 * (int) Math.signum(xGradient[arc.getPoint(0)]) *
                        (int) Math.signum(yGradient[arc.getPoint(0)]);
                if (gradient > 0) positiveGradientArcLabelList.add(arc);
                else if (gradient < 0) negativeGradientArcLabelList.add(arc);
            }
        }
    }


    // Part 3.1.3 of the paper
    private void findArcConvexity() {
        positiveConvexityArcLabelList = new ArrayList<>(positiveGradientArcLabelList.size() + negativeGradientArcLabelList.size());
        negativeConvexityArcLabelList  = new ArrayList<>(positiveGradientArcLabelList.size() + negativeGradientArcLabelList.size());
        quadrantOneArcLabelList = new ArrayList<>(positiveGradientArcLabelList.size());
        quadrantTwoArcLabelList = new ArrayList<>(negativeGradientArcLabelList.size());
        quadrantThreeArcLabelList = new ArrayList<>(positiveGradientArcLabelList.size());
        quadrantFourArcLabelList = new ArrayList<>(negativeGradientArcLabelList.size());
        for (EllipseQuarterArc arc: new ArrayList<>(positiveGradientArcLabelList)) {
            int convexity = calculateSingleArcConvexity(arc);
            if (convexity == 0) {
                positiveGradientArcLabelList.remove(arc);
            } else if (convexity > 0) {
                positiveConvexityArcLabelList.add(arc);
                quadrantOneArcLabelList.add(arc);
                arc.quadrant = 1;
            } else {
                negativeConvexityArcLabelList.add(arc);
                quadrantThreeArcLabelList.add(arc);
                arc.quadrant = 3;
            }
        }
        for (EllipseQuarterArc arc: new ArrayList<>(negativeGradientArcLabelList)) {
            int convexity = calculateSingleArcConvexity(arc);
            if (convexity == 0) {
                negativeGradientArcLabelList.remove(arc);
            } else if (convexity > 0) {
                positiveConvexityArcLabelList.add(arc);
                quadrantTwoArcLabelList.add(arc);
                arc.quadrant = 2;
            } else {
                negativeConvexityArcLabelList.add(arc);
                quadrantFourArcLabelList.add(arc);
                arc.quadrant = 4;
            }
        }
    }
    private int calculateSingleArcConvexity(EllipseQuarterArc arc) {
        int areaOver = 0;
        short previousX = -1;
        for (int i = 0; i < arc.pointList.size(); i++) {
            short pointX = arc.pointList.get(i)[0];
            if (pointX != previousX) {
                short pointY = arc.pointList.get(i)[1];
                areaOver += Math.abs(pointY - arc.top);
            }
            previousX = pointX;
        }
        int areaBoundingBox = (arc.right - arc.left) * Math.abs(arc.bottom - arc.top);
        int areaUnder = areaBoundingBox - arc.pointList.size() - areaOver;

        if (areaBoundingBox == 0) {
            return 0;
        } else if (areaUnder > areaOver) {
            return 1;
        } else if (areaUnder < areaOver) {
            return -1;
        }
        return 0;
    }

    // Part 3.2.1 and 3.2.2 of the paper
    // find three arc segments that make up an ellipse
    private List<Ellipse> findArcTripletsQ1Q2Q3() {
        double imageDiagonalLength = Math.sqrt(Math.pow(edgeImage.getEdgeImage().getHeight(),2) +
                                            Math.pow(edgeImage.getEdgeImage().getWidth(), 2));
        double squaredAllowedDistance = centerDistancePercent * imageDiagonalLength;
        squaredAllowedDistance = Math.pow(squaredAllowedDistance, 2);

        List<Ellipse> arcTriples = new ArrayList<>();

        for (EllipseQuarterArc q1Arc: quadrantOneArcLabelList) {
            for (EllipseQuarterArc q2Arc: quadrantTwoArcLabelList) {
                if (q2Arc.right < q1Arc.left + mutualPositionBoundingBoxPixelTolerance) {
                    Object[] chordsAndCenter1 = getParallelChordsAndEstimateCenter(q2Arc, q1Arc);
                    if (chordsAndCenter1 != null) {
                        for (EllipseQuarterArc q3Arc : quadrantThreeArcLabelList) {
                            if (q3Arc.top > q2Arc.bottom - mutualPositionBoundingBoxPixelTolerance) {
                                Object[] chordsAndCenter2 = getParallelChordsAndEstimateCenter(q3Arc, q2Arc);
                                if (chordsAndCenter2 != null) {
                                    float[] center1 = (float[]) chordsAndCenter1[2];
                                    float[] center2 = (float[]) chordsAndCenter2[2];
                                    double distance = Math.pow(center1[0] - center2[0], 2) + Math.pow(center1[1] - center2[1], 2);
                                    if (distance < squaredAllowedDistance) {
//                                        try {
//                                            BufferedImage parallelChordImage = createParallelChordImage(q1Arc, q2Arc, q3Arc, chordsAndCenter1, chordsAndCenter2);
//                                            ImageIO.write(parallelChordImage, "png", new File("working3", "parallelChord." + (++parallelChordImageCounter) + ".png"));
//                                        } catch (IOException ex) {
//                                            ex.printStackTrace();
//                                        }
                                        Ellipse triplet = new Ellipse();
                                        triplet.arc3 = q3Arc;
                                        triplet.arc2 = q2Arc;
                                        triplet.arc1 = q1Arc;
                                        triplet.center32 = center2;
                                        triplet.center21 = center1;
                                        triplet.chord3start2mid = (ParallelChords) chordsAndCenter1[0];
                                        triplet.chord3mid2end = (ParallelChords) chordsAndCenter1[1];
                                        triplet.chord2start1mid = (ParallelChords) chordsAndCenter2[0];
                                        triplet.chord2mid1end = (ParallelChords) chordsAndCenter2[1];
                                        arcTriples.add(triplet);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return arcTriples;
    }

    private List<Ellipse> findArcTripletsQ2Q3Q4() {
        double imageDiagonalLength = Math.sqrt(Math.pow(edgeImage.getEdgeImage().getHeight(),2) +
                Math.pow(edgeImage.getEdgeImage().getWidth(), 2));
        double squaredAllowedDistance = centerDistancePercent * imageDiagonalLength;
        squaredAllowedDistance = Math.pow(squaredAllowedDistance, 2);

        List<Ellipse> arcTriples = new ArrayList<>();

        for (EllipseQuarterArc q2Arc: quadrantTwoArcLabelList) {
            for (EllipseQuarterArc q3Arc: quadrantThreeArcLabelList) {
                if (q3Arc.top > q2Arc.bottom - mutualPositionBoundingBoxPixelTolerance) {
                    Object[] chordsAndCenter1 = getParallelChordsAndEstimateCenter(q3Arc, q2Arc);
                    if (chordsAndCenter1 != null) {
                        float[] center1 = (float[]) chordsAndCenter1[2];
                        for (EllipseQuarterArc q4Arc : quadrantFourArcLabelList) {
                            if (q4Arc.left > q3Arc.right - mutualPositionBoundingBoxPixelTolerance) {
                                Object[] chordsAndCenter2 = getParallelChordsAndEstimateCenter(q4Arc, q3Arc);
                                if (chordsAndCenter2 != null) {
                                    float[] center2 = (float[]) chordsAndCenter2[2];
                                    double distance = Math.pow(center1[0] - center2[0], 2) + Math.pow(center1[1] - center2[1], 2);
                                    if (distance < squaredAllowedDistance) {
//                                        try {
//                                            BufferedImage parallelChordImage = createParallelChordImage(q2Arc, q3Arc, q4Arc, chordsAndCenter1, chordsAndCenter2);
//                                            ImageIO.write(parallelChordImage, "png", new File("working3", "parallelChord." + (++parallelChordImageCounter) + ".png"));
//                                        } catch (IOException ex) {
//                                            ex.printStackTrace();
//                                        }

                                        Ellipse triplet = new Ellipse();
                                        triplet.arc3 = q4Arc;
                                        triplet.arc2 = q3Arc;
                                        triplet.arc1 = q2Arc;
                                        triplet.center32 = center2;
                                        triplet.center21 = center1;
                                        triplet.chord3start2mid = (ParallelChords) chordsAndCenter1[0];
                                        triplet.chord3mid2end = (ParallelChords) chordsAndCenter1[1];
                                        triplet.chord2start1mid = (ParallelChords) chordsAndCenter2[0];
                                        triplet.chord2mid1end = (ParallelChords) chordsAndCenter2[1];
                                        arcTriples.add(triplet);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return arcTriples;
    }

    private List<Ellipse> findArcTripletsQ3Q4Q1() {
        double imageDiagonalLength = Math.sqrt(Math.pow(edgeImage.getEdgeImage().getHeight(),2) +
                Math.pow(edgeImage.getEdgeImage().getWidth(), 2));
        double squaredAllowedDistance = centerDistancePercent * imageDiagonalLength;
        squaredAllowedDistance = Math.pow(squaredAllowedDistance, 2);

        List<Ellipse> arcTriples = new ArrayList<>();

        for (EllipseQuarterArc q3Arc: quadrantThreeArcLabelList) {
            for (EllipseQuarterArc q4Arc: quadrantFourArcLabelList) {
                if (q4Arc.left > q3Arc.right - mutualPositionBoundingBoxPixelTolerance) {
                    Object[] chordsAndCenter1 = getParallelChordsAndEstimateCenter(q4Arc, q3Arc);
                    if (chordsAndCenter1 != null) {
                        float[] center1 = (float[]) chordsAndCenter1[2];
                        for (EllipseQuarterArc q1Arc : quadrantOneArcLabelList) {
                            if (q1Arc.bottom < q4Arc.top + mutualPositionBoundingBoxPixelTolerance) {
                                Object[] chordsAndCenter2 = getParallelChordsAndEstimateCenter(q1Arc, q4Arc);
                                if (chordsAndCenter2 != null) {
                                    float[] center2 = (float[]) chordsAndCenter2[2];
                                    double distance = Math.pow(center1[0] - center2[0], 2) + Math.pow(center1[1] - center2[1], 2);
                                    if (distance < squaredAllowedDistance) {
//                                        try {
//                                            BufferedImage parallelChordImage = createParallelChordImage(q3Arc, q4Arc, q1Arc, chordsAndCenter1, chordsAndCenter2);
//                                            ImageIO.write(parallelChordImage, "png", new File("working3", "parallelChord." + (++parallelChordImageCounter) + ".png"));
//                                        } catch (IOException ex) {
//                                            ex.printStackTrace();
//                                        }

                                        Ellipse triplet = new Ellipse();
                                        triplet.arc3 = q1Arc;
                                        triplet.arc2 = q4Arc;
                                        triplet.arc1 = q3Arc;
                                        triplet.center32 = center2;
                                        triplet.center21 = center1;
                                        triplet.chord3start2mid = (ParallelChords) chordsAndCenter1[0];
                                        triplet.chord3mid2end = (ParallelChords) chordsAndCenter1[1];
                                        triplet.chord2start1mid = (ParallelChords) chordsAndCenter2[0];
                                        triplet.chord2mid1end = (ParallelChords) chordsAndCenter2[1];
                                        arcTriples.add(triplet);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return arcTriples;
    }

    private List<Ellipse> findArcTripletsQ4Q1Q2() {
        double imageDiagonalLength = Math.sqrt(Math.pow(edgeImage.getEdgeImage().getHeight(),2) +
                Math.pow(edgeImage.getEdgeImage().getWidth(), 2));
        double squaredAllowedDistance = centerDistancePercent * imageDiagonalLength;
        squaredAllowedDistance = Math.pow(squaredAllowedDistance, 2);

        List<Ellipse> arcTriples = new ArrayList<>();

        for (EllipseQuarterArc q4Arc: quadrantFourArcLabelList) {
            for (EllipseQuarterArc q1Arc: quadrantOneArcLabelList) {
                if (q1Arc.bottom < q4Arc.top + mutualPositionBoundingBoxPixelTolerance) {
                    Object[] chordsAndCenter1 = getParallelChordsAndEstimateCenter(q1Arc, q4Arc);
                    if (chordsAndCenter1 != null) {
                        float[] center1 = (float[]) chordsAndCenter1[2];
                        for (EllipseQuarterArc q2Arc : quadrantTwoArcLabelList) {
                            if (q2Arc.right < q1Arc.left + mutualPositionBoundingBoxPixelTolerance) {
                                Object[] chordsAndCenter2 = getParallelChordsAndEstimateCenter(q2Arc, q1Arc);
                                if (chordsAndCenter2 != null) {
                                    float[] center2 = (float[]) chordsAndCenter2[2];
                                    double distance = Math.pow(center1[0] - center2[0], 2) + Math.pow(center1[1] - center2[1], 2);
                                    if (distance < squaredAllowedDistance) {
//                                        try {
//                                            BufferedImage parallelChordImage = createParallelChordImage(q4Arc, q1Arc, q2Arc, chordsAndCenter1, chordsAndCenter2);
//                                            ImageIO.write(parallelChordImage, "png", new File("working3", "parallelChord." + (++parallelChordImageCounter) + ".png"));
//                                        } catch (IOException ex) {
//                                            ex.printStackTrace();
//                                        }

                                        Ellipse triplet = new Ellipse();
                                        triplet.arc3 = q2Arc;
                                        triplet.arc2 = q1Arc;
                                        triplet.arc1 = q4Arc;
                                        triplet.center32 = center2;
                                        triplet.center21 = center1;
                                        triplet.chord3start2mid = (ParallelChords) chordsAndCenter1[0];
                                        triplet.chord3mid2end = (ParallelChords) chordsAndCenter1[1];
                                        triplet.chord2start1mid = (ParallelChords) chordsAndCenter2[0];
                                        triplet.chord2mid1end = (ParallelChords) chordsAndCenter2[1];
                                        arcTriples.add(triplet);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return arcTriples;
    }

    // algorithm is taken from CEllipseDetectorYaed::GetFastCenter
    // arc1 is clockwise next to arc2, following quadrant numbering convention
    private Object[] getParallelChordsAndEstimateCenter(EllipseQuarterArc arc2, EllipseQuarterArc arc1) {

        ParallelChords parallelChords21 = getParallelChords(arc2, arc1, true);
        if (parallelChords21 == null) return null;
        setMedianSlopeAndCentroid(parallelChords21);

        ParallelChords parallelChords12 = getParallelChords(arc1, arc2, false);
        if (parallelChords12 == null) return null;
        setMedianSlopeAndCentroid(parallelChords12);
        Object[] returnValue = new Object[3];
        returnValue[0] = parallelChords21;
        returnValue[1] = parallelChords12;
        returnValue[2] = estimateCenter(parallelChords21, parallelChords12);
        return returnValue;
    }

    // algorithm is taken from CEllipseDetectorYaed::GetFastCenter
    private float[] estimateCenter(ParallelChords chord2, ParallelChords chord1) {

        float centerX = (chord1.medianCentroidY - chord1.medianSlope * chord1.medianCentroidX -
                chord2.medianCentroidY + chord2.medianSlope * chord2.medianCentroidX)
                / (chord2.medianSlope - chord1.medianSlope);

        float centerY = (chord2.medianSlope * chord1.medianCentroidY - chord1.medianSlope * chord2.medianCentroidY +
                chord2.medianSlope * chord1.medianSlope * (chord2.medianCentroidX - chord1.medianCentroidX))
                / (chord2.medianSlope - chord1.medianSlope);
        return new float[] {centerX, centerY};
    }

    // part 3.2.2 of paper
    // referring to figure 6, if startOfArc2=true, then this is the red lines
    private ParallelChords getParallelChords(EllipseQuarterArc arc2, EllipseQuarterArc arc1, boolean startOfArc2) {

        ParallelChords parallelChords = new ParallelChords();
        // chords going from start/end of arc2 to middle of arc1
        short[] middle1 = arc1.pointList.get(arc1.pointList.size() / 2);

        int arc2index;
        if (arc2.quadrant == 1 || arc2.quadrant == 2) {
            arc2index = startOfArc2 ? 0 : arc2.pointList.size() - 1;
        } else if (arc2.quadrant == 3 || arc2.quadrant == 4) {
            // arcs are sorted left to right, but this function assumes sorted clockwise
            arc2index = startOfArc2 ? arc2.pointList.size() - 1 : 0;
        } else {
            throw new IllegalStateException("Wha???");
        }

        float dxReference = arc2.pointList.get(arc2index)[0] - middle1[0];
        float dyReference = arc2.pointList.get(arc2index)[1] - middle1[1];
        float slopeReference = dyReference / dxReference;
        parallelChords.referenceSlope = slopeReference;
        if (dyReference == 0) dyReference = 0.00001f;

        // find indexes on first arc
        int arc1HalfSize = (arc1.pointList.size()) / 2;
        int minPoints = numberOfParallelChords < arc1HalfSize ? numberOfParallelChords : arc1HalfSize;
        int[] arc1Indexes = new int[minPoints];
        if (numberOfParallelChords < arc1HalfSize) {
            int stepDirection;
            if (arc1.quadrant == 1 || arc1.quadrant == 2) {
                stepDirection = startOfArc2 ? -1 : 1;
            } else if (arc1.quadrant == 3 || arc1.quadrant == 4) {
                // arcs are sorted left to right, but this function assumes sorted clockwise
                stepDirection = startOfArc2 ? 1 : -1;
            } else {
                throw new IllegalStateException("Wha???");
            }

            float indexStepSize = (float) arc1HalfSize / (float) numberOfParallelChords * stepDirection;
            float currentIndex = arc1HalfSize + indexStepSize / 2;
            for (int i = 0; i < numberOfParallelChords; i++) {
                arc1Indexes[i] = (int) currentIndex;
                currentIndex += indexStepSize;
            }
        } else {
            boolean firstHalf = startOfArc2;
            if (arc1.quadrant == 3 || arc1.quadrant == 4) firstHalf = firstHalf == false;
            if (firstHalf) {
                for (int i = 0; i < arc1HalfSize; i++) arc1Indexes[i] = i;
            } else {
                for (int i = arc1HalfSize; i < arc1Indexes.length + arc1HalfSize; i++) arc1Indexes[i - arc1HalfSize] = i;
            }
        }

        parallelChords.midpointList = new ArrayList<>(minPoints);
        parallelChords.slopeList = new ArrayList<>(minPoints);
        for (int i = 0; i < minPoints; i++) {
            // for each point in arc1
            short[] arc1point = arc1.pointList.get(arc1Indexes[i]);
            int arc1x = arc1point[0];
            int arc1y = arc1point[1];

            // is the start of the arc matching?
            int arc2StartIndex = 0;
            short[] arc2Point = arc2.pointList.get(arc2StartIndex);
            float slopeDiffBegin = (arc2Point[0] - arc1x) * dyReference - (arc2Point[1] - arc1y) * dxReference;
            int signBegin = (int) Math.signum(slopeDiffBegin);
            if (signBegin == 0) {
                parallelChords.midpointList.add(new float[]{(arc2Point[0] + arc1x) / 2.0f, (arc2Point[1] + arc1y) / 2.0f});
                float slope = ((float) arc2Point[1] - (float) arc1y) / ((float) arc2Point[0] - (float) arc1x);
                parallelChords.slopeList.add(slope);
                continue;
            }

            // is the end of the arc matching?
            int arc2EndIndex = arc2.pointList.size() - 1;
            arc2Point = arc2.pointList.get(arc2EndIndex);
            float slopeDiffEnd = (arc2Point[0] - arc1x) * dyReference - (arc2Point[1] - arc1y) * dxReference;
            int signEnd = (int) Math.signum(slopeDiffEnd);
            if (signEnd == 0) {
                parallelChords.midpointList.add(new float[]{(arc2Point[0] + arc1x) / 2.0f, (arc2Point[1] + arc1y) / 2.0f});
                float slope = ((float) arc2Point[1] - (float) arc1y) / ((float) arc2Point[0] - (float) arc1x);
                parallelChords.slopeList.add(slope);
                continue;
            }

            // if the signs are the same direction, then there are no slopes inbetween
            if (signBegin + signEnd != 0) continue;

            // binary search to find a point in arc1 that makes the closest slope to the reference slope
            float slopeDiffMid;
            int signMid;
            int arc2MidIndex = (arc2EndIndex + arc2StartIndex) / 2;
            while (arc2EndIndex - arc2StartIndex > 2) {
                arc2Point = arc2.pointList.get(arc2MidIndex);
//                slope = ((float) arc2Point[1] - (float) arc1y) / ((float) arc2Point[0] - (float) arc1x);
                slopeDiffMid = (arc2Point[0] - arc1x) * dyReference - (arc2Point[1] - arc1y) * dxReference;
                signMid = (int) Math.signum(slopeDiffMid);

                if (signMid == 0) {
                    break;
                }

                if (signMid + signBegin == 0) {
                    signEnd = signMid;
                    slopeDiffEnd = slopeDiffMid;
                    arc2EndIndex = arc2MidIndex;
                } else {
                    signBegin = signMid;
                    slopeDiffBegin = slopeDiffMid;
                    arc2StartIndex = arc2MidIndex;
                }


                arc2MidIndex = (arc2EndIndex + arc2StartIndex) / 2;
            }
            arc2Point = arc2.pointList.get(arc2MidIndex);
            slopeDiffMid = (arc2Point[0] - arc1x) * dyReference - (arc2Point[1] - arc1y) * dxReference;
            signMid = (int) Math.signum(slopeDiffMid);
            if (signMid == 0) {
                // add this middle point at arc2MidIndex;
                arc2Point = arc2.pointList.get(arc2MidIndex);
                parallelChords.midpointList.add(new float[]{(arc2Point[0] + arc1x) / 2.0f, (arc2Point[1] + arc1y) / 2.0f});
                float slope = ((float) arc2Point[1] - (float) arc1y) / ((float) arc2Point[0] - (float) arc1x);
                parallelChords.slopeList.add(slope);
            } else {
                short[] arc2OtherPoint;
                if (signMid + signEnd == 0) {
                    arc2OtherPoint = arc2.pointList.get(arc2EndIndex);
                } else if (signMid + signBegin == 0) {
                    arc2OtherPoint = arc2.pointList.get(arc2StartIndex);
                } else {
                    throw new IllegalStateException("Huh??");
                }
                float intersectionX, intersectionY;
                if (arc2OtherPoint[0] == arc2Point[0]) {
                    intersectionX = arc2OtherPoint[0];
                    intersectionY = slopeReference * (arc2OtherPoint[0] - arc1x) + arc1y;
                    if (intersectionY < Math.min(arc2OtherPoint[1], arc2Point[1]) ||
                            intersectionY > Math.max(arc2OtherPoint[1], arc2Point[1])) {
                        throw new IllegalArgumentException("ugh");
                    }
                } else {
                    float slopeOfArcLine = (arc2OtherPoint[1] - arc2Point[1]) / (arc2OtherPoint[0] - arc2Point[0]);
                    float interceptOfArcLine = arc2Point[1] - arc2Point[0] * slopeOfArcLine;
                    float interceptOfReferenceLine = arc1point[1] - arc1point[0] * slopeReference;
                    intersectionX = (interceptOfReferenceLine - interceptOfArcLine) / (slopeOfArcLine - slopeReference);
                    intersectionY = slopeOfArcLine * intersectionX + interceptOfArcLine;
                }
                parallelChords.midpointList.add(new float[]{(intersectionX + arc1x) / 2.0f, (intersectionY + arc1y) / 2.0f});
                float slope = ((float) intersectionY - (float) arc1y) / ((float) intersectionX - (float) arc1x);
                parallelChords.slopeList.add(slope);
            }
        }

        if (parallelChords.midpointList.size() < 2) {
            return null;
        }
        return parallelChords;
    }

    private void setMedianSlopeAndCentroid(ParallelChords parallelChords) {
        int size = parallelChords.midpointList.size();
        int middle = size / 2;
        float[] slope = new float[middle];
        float[] xCoord = new float[size];
        float[] yCoord = new float[size];
        parallelChords.perpendicularSlopeList = new ArrayList<>(middle);
        for (int i = 0; i < middle; i++) {
            float[] point1 = parallelChords.midpointList.get(i);
            float[] point2 = parallelChords.midpointList.get(i+middle);
            slope[i] = (point2[1] - point1[1]) / (point2[0] - point1[0]);
            parallelChords.perpendicularSlopeList.add(slope[i]);

            xCoord[i] = point1[0];
            xCoord[i+middle] = point2[0];

            yCoord[i] = point1[1];
            yCoord[i+middle] = point2[1];
        }
        if (parallelChords.midpointList.size() % 2 == 1) {
            float[] lastPoint = parallelChords.midpointList.get(size - 1);
            xCoord[xCoord.length-1] = lastPoint[0];
            yCoord[yCoord.length-1] = lastPoint[1];
        }
        parallelChords.medianCentroidX = median(xCoord, size);
        parallelChords.medianCentroidY = median(yCoord, size);

        parallelChords.medianSlope = median(slope, middle);
    }

    // part 3.2.3 of paper
    private void parameterEstimation(List<Ellipse> tripletList) {

        for (Ellipse triplet : tripletList) {
            // center point estimation
            float[] x = new float[7];
            float[] y = new float[7];
            x[0] = triplet.center32[0];
            y[0] = triplet.center32[1];
            x[1] = triplet.center21[0];
            y[1] = triplet.center21[1];

            float[] c = estimateCenter(triplet.chord3start2mid, triplet.chord2start1mid);
            x[2] = c[0];
            y[2] = c[1];
            c = estimateCenter(triplet.chord3mid2end, triplet.chord2start1mid);
            x[3] = c[0];
            y[3] = c[1];
            c = estimateCenter(triplet.chord3start2mid, triplet.chord2mid1end);
            x[4] = c[0];
            y[4] = c[1];
            c = estimateCenter(triplet.chord3mid2end, triplet.chord2mid1end);
            x[5] = c[0];
            y[5] = c[1];

            triplet.center = new float[2];

            if (useMedianCenter) {
                x[6] = (x[0] + x[1]) * 0.5f;
                y[6] = (y[0] + y[1]) * 0.5f;
                triplet.center[0] = median(x, 7);
                triplet.center[1] = median(y, 7);
            } else {
                triplet.center[0] = mean(x, 6);
                triplet.center[1] = mean(y, 6);
            }

            Map<Integer, Integer> nAccumulator = new HashMap<>();
            Map<Integer, Integer> rhoAccumulator = new HashMap<>();

            calculateNAndRhoAccumulator(triplet.chord3start2mid, triplet.chord2start1mid, nAccumulator, rhoAccumulator);
            calculateNAndRhoAccumulator(triplet.chord3start2mid, triplet.chord2mid1end, nAccumulator, rhoAccumulator);
            calculateNAndRhoAccumulator(triplet.chord3mid2end, triplet.chord2start1mid, nAccumulator, rhoAccumulator);
            calculateNAndRhoAccumulator(triplet.chord3mid2end, triplet.chord2mid1end, nAccumulator, rhoAccumulator);

            float n = calculateAccumulator(nAccumulator) / 100f;

            float rho = calculateAccumulator(rhoAccumulator) * pi / 180f;

            Map<Integer, Integer> aAxisAccumulator = new HashMap<>();
            calculateASemiaxisAndAccumulator(triplet.arc3, triplet.center, n, rho, aAxisAccumulator);
            calculateASemiaxisAndAccumulator(triplet.arc2, triplet.center, n, rho, aAxisAccumulator);
            calculateASemiaxisAndAccumulator(triplet.arc1, triplet.center, n, rho, aAxisAccumulator);

            float a = calculateAccumulator(aAxisAccumulator);

            // equation 23
            float b = a * n;

            triplet.rho = rho;
            triplet.aAxis = a;
            triplet.bAxis = b;

        }
    }

    private float calculateAccumulator(Map<Integer, Integer> accumulator) {
        ArrayList<Integer> maxList = new ArrayList<>();
        int maxCount = -1;
        for (Integer aInt: accumulator.keySet()) {
            if (accumulator.get(aInt) > maxCount) {
                maxList.clear();
                maxList.add(aInt);
                maxCount = accumulator.get(aInt);
            } else if (accumulator.get(aInt) == maxCount) {
                maxList.add(aInt);
            }
        }
        int sum = 0;
        for (int v: maxList) sum+= v;
        return (float) sum / maxList.size();
    }

    // angle 3 degrees
    // center 313, 225.50
    // ratio 2:1

    // 150, 150 to 450x300
    // center = 300 / 225

    private void calculateNAndRhoAccumulator(ParallelChords chord2, ParallelChords chord1,
                                             Map<Integer, Integer> nAccumulator, Map<Integer, Integer> rhoAccumulator) {
        float q1 = chord2.referenceSlope; // q1
        float q3 = chord1.referenceSlope; // q3
        for (float q2: chord2.perpendicularSlopeList) { // q2
            float q1xq2 = q1*q2;
            for (float q4: chord1.perpendicularSlopeList) { // q4

                float q3xq4 = q3 * q4;

                // equation 15
                float gamma = q1xq2 - q3xq4;
                // equation 16
                float beta = (q3xq4 + 1) * (q1 + q2) - (q1xq2 + 1) * (q3 + q4);
                // equation 17
                float kPlus = (-beta + (float) Math.sqrt(beta * beta + 4 * gamma * gamma)) / (2 * gamma);

                // equation 18
                float zPlus = ((q1 - kPlus) * (q2 - kPlus)) / ((1 + q1 * kPlus) * (1 + q2 * kPlus));
                if (zPlus < 0) {
                    float nPlus = (float) Math.sqrt(-zPlus);

                    // equation 14
                    float rho = (float) (Math.atan(kPlus) + (nPlus <= 1 ? 0 : (pi / 2)));
                    // equation 13
                    float n = nPlus <= 1 ? nPlus : (1.0f / nPlus);

                    int rhoInt = Math.round(rho * 180 / pi + 180) % 180;
                    int nInt = Math.round(n * 100);

                    Integer rhoCount = rhoAccumulator.get(rhoInt);
                    rhoAccumulator.put(rhoInt, rhoCount == null ? 1 : rhoCount + 1);

                    Integer nCount = nAccumulator.get(nInt);
                    nAccumulator.put(nInt, nCount == null ? 1 : nCount + 1);
                }
            }
        }
    }

    private void calculateASemiaxisAndAccumulator(EllipseQuarterArc arc, float[] center, float n, float rho,
                                                  Map<Integer, Integer> accumulator) {
        float kPlus = (float) Math.tan(rho);
        float cosRho = (float) Math.cos(rho);
        float nSquared = n * n;
        float denomRecip = 1 / (float) Math.sqrt(kPlus * kPlus + 1);
        for (short[] point: arc.pointList) {
            // equation 20
            float x0 = ((point[0] - center[0]) + (point[1] - center[1]) * kPlus) * denomRecip;
            // equation 21
            float y0 = (-(point[0] - center[0]) * kPlus + (point[1] - center[1])) * denomRecip;

            // equation 22
            float aX = (float) Math.sqrt((x0 * x0 * nSquared + y0 * y0) / nSquared) * denomRecip;

            //equation 19
            float a = Math.abs(aX / cosRho);
            int aInt = Math.round(a);
            Integer aCount = accumulator.get(aInt);
            accumulator.put(aInt, aCount == null ? 1 : aCount + 1);

        }
    }

    // part 3.3.1: Validation. Throw out false positives.
    private List<Ellipse> calculatePointsOnEllipse(List<Ellipse> tripletList) {
        List<Ellipse> newTripletList = new ArrayList<>(tripletList.size());
        for (Ellipse triplet: tripletList) {

            int count = calculatePointsOnEllipse(triplet, triplet.arc1);
            count += calculatePointsOnEllipse(triplet, triplet.arc2);
            count += calculatePointsOnEllipse(triplet, triplet.arc3);

            // equastion 25
            float score = 0;
            if (count > 0) {
                score = ((float) count) / (triplet.arc1.pointList.size() + triplet.arc2.pointList.size() + triplet.arc3.pointList.size());
                triplet.ellipseScore = score;
            }

            if (score > distanceToEllipseContourScoreCutoff) {
                // this metric is in the reference implementation, but not in the paper. The entire
                // comment is repeated here:

                // Compute reliability
                // this metric is not described in the paper, mostly due to space limitations.
                // The main idea is that for a given ellipse (TD) even if the score is high, the arcs
                // can cover only a small amount of the contour of the estimated ellipse.
                // A low reliability indicate that the arcs form an elliptic shape by chance, but do not underlie
                // an actual ellipse. The value is normalized between 0 and 1.
                // The default value is 0.4.

                // It is somehow similar to the "Angular Circumference Ratio" saliency criteria
                // as in the paper:
                // D. K. Prasad, M. K. Leung, S.-Y. Cho, Edge curvature and convexity
                // based ellipse detection method, Pattern Recognition 45 (2012) 3204-3221.

                float reliability = calculateReliability(triplet, triplet.arc1);
                reliability += calculateReliability(triplet, triplet.arc2);
                reliability += calculateReliability(triplet, triplet.arc3);

                reliability = Math.min(1, reliability / (3 * (triplet.aAxis + triplet.bAxis)));

                if (reliability > reliabilityCutoff) {
                    triplet.ellipseScore = (score + reliability) * 0.5f;
                    newTripletList.add(triplet);
                } else {
                    System.out.println("Failed Angular Circumference Ratio:" + reliability);
                }
            } else {
                System.out.println("Failed pointOnEdge score:" + score);
            }
        }
        return newTripletList;
    }

    // part 3.3.2: clustering : find ellipse triplets that are the same ellipse and keep only best one.
    private List<Ellipse> clusterEllipse(List<Ellipse> tripletList) {
        List<Ellipse> newTripletList = new ArrayList<>(tripletList.size());
        Collections.sort(tripletList, new Comparator<Ellipse>() {
            @Override
            public int compare(Ellipse o1, Ellipse o2) {
                float diff = o2.ellipseScore - o1.ellipseScore;
                if (diff > 0) return 1;
                if (diff < 0) return -1;
                return 0;
            }
        });
        for (Ellipse triplet: tripletList) {
            while (triplet.rho < 0) triplet.rho += pi;
            while (triplet.rho > pi) triplet.rho -= pi;

            boolean foundCluster = false;
            for (Ellipse cluster: newTripletList) {

                // equation 26
                float minBaxisSquared = Math.min(triplet.bAxis, cluster.bAxis) * 0.1f;
                minBaxisSquared *= minBaxisSquared;

                float distanceSquared = (float) (Math.pow(triplet.center[0] - cluster.center[0], 2) +
                        Math.pow(triplet.center[1] - cluster.center[1], 2));

                if (distanceSquared > minBaxisSquared) continue;

                // equation 27
                if (Math.abs(triplet.aAxis - cluster.aAxis) / Math.max(triplet.aAxis, cluster.aAxis) > 1) {
                    continue;
                }

                // equation 28
                if (Math.abs(triplet.bAxis - cluster.bAxis) / Math.max(triplet.bAxis, cluster.bAxis) > 1) {
                    continue;
                }

                // equation 29
                float minAngle = Math.abs(triplet.rho - cluster.rho);
                if (Math.min(pi - minAngle, minAngle) / pi > 0.1 &&
                        triplet.bAxis / triplet.aAxis < 0.9 && cluster.bAxis / cluster.aAxis < 0.9) {
                    continue;
                }

                foundCluster = true;
                break;
            }

            if (foundCluster == false) {
                newTripletList.add(triplet);
            }

        }
        return newTripletList;

    }

    private int calculatePointsOnEllipse(Ellipse triplet, EllipseQuarterArc arc) {
        float cos = (float) Math.cos(triplet.rho);
        float sin = (float) Math.sin(triplet.rho);
        float inverseASquared = 1f / (triplet.aAxis * triplet.aAxis);
        float inverseBSquared = 1f / (triplet.bAxis * triplet.bAxis);

        int counter = 0;
        for (short[] point: arc.pointList) {
            float xDelta = point[0] - triplet.center[0];
            float yDelta = point[1] - triplet.center[1];
            float rx = (xDelta * cos - yDelta * sin);
            float ry = (xDelta * sin - yDelta * cos);
            float h = (rx * rx * inverseASquared + ry * ry * inverseBSquared);
            if (Math.abs(h - 1) < distanceToEllipseContour) {
                counter++;
            }
        }
        return counter;
    }

    private float calculateReliability(Ellipse triplet, EllipseQuarterArc arc) {
        float startX = arc.pointList.get(0)[0] - triplet.center[0];
        float startY = arc.pointList.get(0)[1] - triplet.center[1];
        float endX = arc.pointList.get(arc.pointList.size() - 1)[0] - triplet.center[0];
        float endY = arc.pointList.get(arc.pointList.size() - 1)[1] - triplet.center[1];

        float cos = (float) Math.cos(triplet.rho);
        float sin = (float) Math.sin(triplet.rho);
        float r1x = (startX * cos - startY * sin);
        float r1y = (startX * sin + startY *cos);
        float r2x = (endX * cos - endY * sin);
        float r2y = (endX * sin + endY * cos);

        return Math.abs(r2x - r1x) + Math.abs(r2y - r1y);
    }

    public BufferedImage createArcImage() {
        BufferedImage writeImage = cloneImage(edgeImage.getEdgeImage());
        int colorIndex = 0;
        for (EllipseQuarterArc arc: positiveConvexityArcLabelList) {
            int color = colorList[colorIndex++ % colorList.length];
            for (short[] point: arc.pointList) {
                writeImage.setRGB(point[0], point[1], color);
            }
        }
        for (EllipseQuarterArc arc: negativeConvexityArcLabelList) {
            int color = colorList[colorIndex++ % colorList.length];
            for (short[] point: arc.pointList) {
                writeImage.setRGB(point[0], point[1], color);
            }
        }
        return writeImage;
    };

    public BufferedImage createGradientImage() {
        BufferedImage writeImage = cloneImage(edgeImage.getEdgeImage());
        int color = colorList[1];
        for (EllipseQuarterArc arc: positiveGradientArcLabelList) {
            for (short[] point: arc.pointList) {
                writeImage.setRGB(point[0], point[1], color);
            }
        }
        color = colorList[2];
        for (EllipseQuarterArc arc: negativeGradientArcLabelList) {
            for (short[] point: arc.pointList) {
                writeImage.setRGB(point[0], point[1], color);
            }
        }
        return writeImage;
    };

    public BufferedImage createConvexityImage() {
        BufferedImage writeImage = cloneImage(edgeImage.getEdgeImage());
        int color = colorList[0];
        for (EllipseQuarterArc arc: positiveConvexityArcLabelList) {
            for (short[] point: arc.pointList) {
                writeImage.setRGB(point[0], point[1], color);
            }
        }
        color = colorList[4];
        for (EllipseQuarterArc arc: negativeConvexityArcLabelList) {
            for (short[] point: arc.pointList) {
                writeImage.setRGB(point[0], point[1], color);
            }
        }
        return writeImage;
    };

    public BufferedImage createQuadrantImage() {
        BufferedImage writeImage = cloneImage(edgeImage.getEdgeImage());
        int color = colorList[0];
        for (EllipseQuarterArc arc: quadrantOneArcLabelList) {
            for (short[] point: arc.pointList) {
                writeImage.setRGB(point[0], point[1], color);
            }
        }
        color = colorList[1];
        for (EllipseQuarterArc arc: quadrantTwoArcLabelList) {
            for (short[] point: arc.pointList) {
                writeImage.setRGB(point[0], point[1], color);
            }
        }
        color = colorList[4];
        for (EllipseQuarterArc arc: quadrantThreeArcLabelList) {
            for (short[] point: arc.pointList) {
                writeImage.setRGB(point[0], point[1], color);
            }
        }
        color = colorList[2];
        for (EllipseQuarterArc arc: quadrantFourArcLabelList) {
            for (short[] point: arc.pointList) {
                writeImage.setRGB(point[0], point[1], color);
            }
        }
        return writeImage;
    };

    private BufferedImage createParallelChordImage(EllipseQuarterArc arc1,
                                                  EllipseQuarterArc arc2,
                                                  EllipseQuarterArc arc3,
                                                  Object[] parallelChordsAndCenter1,
                                                  Object[] parallelChordsAndCenter2) {
        BufferedImage writeImage = new BufferedImage(edgeImage.getEdgeImage().getWidth(),
                edgeImage.getEdgeImage().getHeight(), edgeImage.getEdgeImage().getType());

        int top = Math.min(arc1.top, Math.min(arc2.top, arc3.top));
        int bottom = Math.max(arc1.bottom, Math.max(arc2.bottom, arc3.bottom));
        int left = Math.min(arc1.left, Math.min(arc2.left, arc3.left));
        int right = Math.max(arc1.right, Math.max(arc2.right, arc3.right));

        ParallelChords chord = (ParallelChords) parallelChordsAndCenter1[0];
        addParallelChord(writeImage, chord, colorList[1], top, bottom, left, right);

        chord = (ParallelChords) parallelChordsAndCenter1[1];
        addParallelChord(writeImage, chord, colorList[0], top, bottom, left, right);

        chord = (ParallelChords) parallelChordsAndCenter2[0];
        addParallelChord(writeImage, chord, colorList[2], top, bottom, left, right);

        chord = (ParallelChords) parallelChordsAndCenter2[1];
        addParallelChord(writeImage, chord, colorList[4], top, bottom, left, right);


        for (short[] point: arc1.pointList)  writeImage.setRGB(point[0], point[1], colorList[14]);
        for (short[] point: arc2.pointList)  writeImage.setRGB(point[0], point[1], colorList[14]);
        for (short[] point: arc3.pointList)  writeImage.setRGB(point[0], point[1], colorList[14]);

        return writeImage;
    }

    private void addParallelChord(BufferedImage writeImage, ParallelChords chord, int color, int top, int bottom, int left, int right) {
        for (int i = 0; i < chord.midpointList.size(); i++) {
            float[] midPoint = chord.midpointList.get(i);
            float slope = chord.slopeList.get(i);

            float[] currentPoint = new float[] {midPoint[0], midPoint[1]};
            while (currentPoint[1] >= top && currentPoint[1] <= bottom && currentPoint[0] >= left) {
                writeImage.setRGB((int) currentPoint[0], (int) currentPoint[1], color);

                currentPoint[0] -= 1;
                currentPoint[1] = slope * (currentPoint[0] - midPoint[0]) + midPoint[1];
            }

            currentPoint = new float[] {midPoint[0], midPoint[1]};
            while (currentPoint[1] >= top && currentPoint[1] <= bottom && currentPoint[0] <= right) {
                writeImage.setRGB((int) currentPoint[0], (int) currentPoint[1], color);

                currentPoint[0] += 1;
                currentPoint[1] = slope * (currentPoint[0] - midPoint[0]) + midPoint[1];
            }
        }
    }
    public BufferedImage createEllipseSharedCenterImage() {
        return createEllipseImage(ellipseSharedCenterList);
    }
    public BufferedImage createEllipseOnEdgePointsImage() {
        return createEllipseImage(ellipseOnEdgePointList);
    }
    public BufferedImage createEllipseOnEdgePointDeduplicatedImage() {
        return createEllipseImage(ellipseOnEdgedPointDeduplicatedList);
    }

    public BufferedImage createFinalEllipseImage() {
        return createEllipseImage(ellipseOnEdgedPointDeduplicatedList);
    }

    public List<Ellipse> getFinalEllipseList() {
        return ellipseOnEdgedPointDeduplicatedList;
    }

    private BufferedImage createEllipseImage(List<Ellipse> drawEllipseList) {
        BufferedImage writeImage = cloneImage(edgeImage.getEdgeImage());
        int colorIndex = 0;
        for (Ellipse triplet: drawEllipseList) {
            int color = colorList[colorIndex++ % colorList.length];
            for (float angle = 0; angle < 360; angle += 0.25) {
                float radians = angle * pi / 180;
                float x = (float) (triplet.aAxis * Math.cos(radians) * Math.cos(triplet.rho) + triplet.bAxis * Math.sin(radians) * Math.sin(triplet.rho));
                float y = (float) (-1 * triplet.aAxis * Math.cos(radians) * Math.sin(triplet.rho) +  triplet.bAxis * Math.sin(radians) * Math.cos(triplet.rho));
                x = Math.round(triplet.center[0] + x);
                y = Math.round(triplet.center[1] - y);
                if (x >= 0 && y >= 0 && x < writeImage.getWidth() && y < writeImage.getHeight()) {
                    writeImage.setRGB((int) x, (int) y, color);
                }
            }
        }
        return writeImage;
    };


    private int[] getPixelARGB(int pixel) {
        int alpha = (pixel >> 24) & 0xff;
        int red = (pixel >> 16) & 0xff;
        int green = (pixel >> 8) & 0xff;
        int blue = (pixel) & 0xff;
        return new int[]{alpha, red, green, blue};
    }

    private int getIntRGB(int alpha, int red, int green, int blue) {
        int intRGB = alpha & 0xff;
        intRGB = intRGB << 8;
        intRGB += red & 0xff;
        intRGB = intRGB << 8;
        intRGB += green & 0xff;
        intRGB = intRGB << 8;
        intRGB += blue & 0xff;
        return intRGB;
    }

    protected class EllipseQuarterArc {

        int imageWidth;

        EllipseQuarterArc(int imageWidth) {
            this.imageWidth = imageWidth;
        }
        List<short[]> pointList = new ArrayList<>();
        short top = Short.MAX_VALUE;
        short bottom = 0;
        short left = Short.MAX_VALUE;
        short right = 0;

        short quadrant = 0;

        // find top/bottom
        private void addPoint(int point) {
            short x = (short) (point % imageWidth);
            short y = (short) (point / imageWidth);
            pointList.add(new short[]{x, y});
            top = y < top ? y : top;
            bottom = y > bottom ? y : bottom;
            left = x < left ? x : left;
            right = x > right ? x : right;
        }

        private int getPoint(int index) {
            short[] point = pointList.get(index);
            return point[1] * imageWidth + point[0];
        }


        // this just checks points for distance from diagonal of bounding box;
        private boolean isCurvedLine() {
            if ((right - left) < minimumBoundingBoxSize || (bottom - top) < minimumBoundingBoxSize) {
                return false;
            }
            float[] xGradient = edgeImage.getXGradient();
            float[] yGradient = edgeImage.getYGradient();
            // for some reason, gradient is backwards, multiply by -1 (I think because y-axis is on the top?)
            int gradient = -1 * (int) Math.signum(xGradient[getPoint(0)]) * (int) Math.signum(yGradient[getPoint(0)]);
            int x1 = left, x2 = right, y1, y2;
            if (gradient > 0) {
                y1 = top;
                y2 = bottom;
            } else {
                y1 = bottom;
                y2 = top;
            }

            List<short[]> checkPointList;
            if (checkAllArcPointsForStraightLine || pointList.size() <= 3) {
                checkPointList = pointList;
            } else {
                checkPointList = new ArrayList<>(3);
                checkPointList.add(pointList.get((int) (pointList.size() * 0.25)));
                checkPointList.add(pointList.get((int) (pointList.size() * 0.5)));
                checkPointList.add(pointList.get((int) (pointList.size() * 0.75)));
            }

            // distance from each point to diagonal of bounding box:
            // https://en.wikipedia.org/wiki/Distance_from_a_point_to_a_line#Line_defined_by_two_points
            double denominator = Math.sqrt( (y2-y1)*(y2-y1)  + (x2-x1)*(x2-x1));
            double numerator_part = x2*y1 - y2*x1;
            for (int i = 0; i < checkPointList.size(); i++) {
                int x0 = checkPointList.get(i)[0];
                int y0 = checkPointList.get(i)[1];
                double dist = Math.abs( (y2-y1)*x0 - (x2-x1)*y0 + numerator_part) / denominator;
                // since distance is from middle line, double to match bounding box concept
                if (dist * 2 > minimumBoundingBoxSize) return true;
            }
            return false;
        }
    }

    private BufferedImage cloneImage(BufferedImage image) {
        ColorModel cm = image.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = image.copyData(null);
        BufferedImage writeImage = new BufferedImage(cm, raster, isAlphaPremultiplied, null);
        return writeImage;
    }

    protected class ParallelChords {

        // slopes and midpoints of parallel chords
        float referenceSlope;
        List<float[]> midpointList;
        List<Float> slopeList;

        // perpendicular lines, through the center of the ellipse. (Theil-Sen estimator: Algorithm 2)
        List<Float> perpendicularSlopeList;
        float medianSlope;
        float medianCentroidX;
        float medianCentroidY;
    }



    private float median(float[] array, int length) {
        float result;
        int n = length/2;

        if (array.length % 2 == 0)  // even number of items; find the middle two and average them
            result = (selectNth(array, 0, length-1, n-1) + selectNth(array, 0, length-1, n)) / 2.0f;
        else                      // odd number of items; return the one in the middle
            result = selectNth(array, 0, length-1, n);

        return result;
    }


    private float selectNth(float[] array, int left, int right, int n) {
        if (left == right) return array[left];

        int pivotIndex = (left + right) / 2;
        pivotIndex = partition(array, left, right, pivotIndex);
        if (n == pivotIndex) return array[n];
        if (n < pivotIndex) return selectNth(array, left, pivotIndex - 1, n);
        return selectNth(array, pivotIndex + 1, right, n);
    }

    private float mean(float[] array, int length) {
        float sum = 0;
        for (int i = 0; i < length; i++) {
            sum += array[i];
        }
        return sum / length;
    }

    private int partition(float[] array, int left, int right, int pivotIndex) {
        float pivotValue = array[pivotIndex]; // move pivot to the end;
        array[pivotIndex] = array[right];
        array[right] = pivotValue;
        int finalPivotIndex = left;
        for (int i = left; i < right; i++) {
            if (array[i] <  pivotValue) {
                float swap = array[finalPivotIndex];
                array[finalPivotIndex] = array[i];
                array[i] = swap;
                finalPivotIndex++;
            }
        }
        array[right] = array[finalPivotIndex]; // move pivot to final place
        array[finalPivotIndex] = pivotValue;
        return finalPivotIndex;
    }

    public int getTotalLineSegmentCount() {
        return totalLineSegmentCount;
    }

    public int getShortLineCount() {
        return shortLineCount;
    }

    public int getStraightLineCount() {
        return straightLineCount;
    }
}
