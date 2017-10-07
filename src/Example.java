
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Created by Eric Farng on 9/21/2017.
 */

public class Example {


    public static void main(String[] args) throws IOException {
        File directory = new File("screenshot default name");
        File file = new File(directory, "Screenshot_2017-02-21-00-47-38.png");
//        File directory = new File("testEllipse");
//        File file = new File(directory, "x-195.12825-y-197.94994-rho-1.375566-n-0.26007044-aAxis-173.52641-bAxis-45.12909.png");

        File outDirectory = new File("working");
        if (outDirectory.exists() == false) outDirectory.mkdirs();
        processFile(file, outDirectory, ImageIO.read(file));
    }

    public static void processFile(File screenshot, File outputDirectory, BufferedImage bufferedImage) throws IOException {
        CannyEdgeDetector cannyEdgeDetector = new CannyEdgeDetector();
//        cannyEdgeDetector.setLowThreshold(0.3125f); // 2.5
//        cannyEdgeDetector.setHighThreshold(3.75f); // 7.5
        cannyEdgeDetector.setAutoThreshold(true);
        // 2 is default for CannyEdgeDetector but 1 is setting from Ellipse reference code
        cannyEdgeDetector.setGaussianKernelRadius(1f); // 2
        // 16 is default for Canny Edge Detector, but 5 is setting from ellipse reference code.
        cannyEdgeDetector.setGaussianKernelWidth(5); // 16
        cannyEdgeDetector.setSourceImage(bufferedImage);
        cannyEdgeDetector.process();
        File edgeFile = new File(outputDirectory, screenshot.getName().replaceFirst("[.][^.]+$", "") + ".edge.png");
        ImageIO.write(cannyEdgeDetector.getEdgeImage(), "png", edgeFile);

        EllipseDetector ellipseDetector = new EllipseDetector();
        ellipseDetector.setEdgeImage(cannyEdgeDetector);
        ellipseDetector.setUseMedianCenter(true);
        ellipseDetector.setDistanceToEllipseContour(1);
        ellipseDetector.process();

        File arcFile = new File(outputDirectory, screenshot.getName().replaceFirst("[.][^.]+$", "") + ".arc.png");
        ImageIO.write(ellipseDetector.createArcImage(), "png", arcFile);

        arcFile = new File(outputDirectory, screenshot.getName().replaceFirst("[.][^.]+$", "") + ".gradient.png");
        ImageIO.write(ellipseDetector.createGradientImage(), "png", arcFile);

        arcFile = new File(outputDirectory, screenshot.getName().replaceFirst("[.][^.]+$", "") + ".convexity.png");
        ImageIO.write(ellipseDetector.createConvexityImage(), "png", arcFile);

        arcFile = new File(outputDirectory, screenshot.getName().replaceFirst("[.][^.]+$", "") + ".quadrant.png");
        ImageIO.write(ellipseDetector.createQuadrantImage(), "png", arcFile);

        arcFile = new File(outputDirectory, screenshot.getName().replaceFirst("[.][^.]+$", "") + ".ellipseSharedCenter.png");
        ImageIO.write(ellipseDetector.createEllipseSharedCenterImage(), "png", arcFile);

        arcFile = new File(outputDirectory, screenshot.getName().replaceFirst("[.][^.]+$", "") + ".ellipseOnEdgePoints.png");
        ImageIO.write(ellipseDetector.createEllipseOnEdgePointsImage(), "png", arcFile);

        arcFile = new File(outputDirectory, screenshot.getName().replaceFirst("[.][^.]+$", "") + ".ellipseOnEdgePointDeduplicated.png");
        ImageIO.write(ellipseDetector.createEllipseOnEdgePointDeduplicatedImage(), "png", arcFile);

        arcFile = new File(outputDirectory, screenshot.getName().replaceFirst("[.][^.]+$", "") + ".ellipse.png");
        ImageIO.write(ellipseDetector.createFinalEllipseImage(), "png", arcFile);

        List<Ellipse> ellipseList = ellipseDetector.getFinalEllipseList();

    }
}
