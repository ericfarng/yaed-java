
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Created by Eric Farng on 9/21/2017.
 */

public class TestEllipseDetection {


    public static void main(String[] args) throws IOException {
        File directory = new File("testEllipseOut");
        if (directory.exists() == false) {
            directory.mkdirs();
        }

        File testDirectory = new File("testEllipse");
        int foundCounter = 0;
        for (File file: testDirectory.listFiles()) {
            int found = processFile(file, directory, ImageIO.read(file));
            if (found > 0) foundCounter++;
        }
        System.out.println("images with ellipse detected: " + foundCounter);
    }

    public static int processFile(File screenshot, File outputDirectory, BufferedImage bufferedImage) throws IOException {
        CannyEdgeDetector cannyEdgeDetector = new CannyEdgeDetector();
        cannyEdgeDetector.setLowThreshold(2.5f); // 2.5
        cannyEdgeDetector.setHighThreshold(7.5f); // 7.5
        // 2 is default for CannyEdgeDetector
        // but 1 is setting from Ellipse reference code
        cannyEdgeDetector.setGaussianKernelRadius(1f); // 2
        // again, 16 is default for Canny Edge Detector, but 5 is setting from ellipse reference code.
        cannyEdgeDetector.setGaussianKernelWidth(5); // 16
        cannyEdgeDetector.setSourceImage(bufferedImage);
        cannyEdgeDetector.process();
        EllipseDetector ellipseDetector = new EllipseDetector();
        ellipseDetector.setEdgeImage(cannyEdgeDetector);
        ellipseDetector.setUseMedianCenter(true);
        ellipseDetector.setDistanceToEllipseContour(0.5f);
        ellipseDetector.setCheckAllArcPointsForStraightLine(false);
        ellipseDetector.process();


        System.out.println("file," + screenshot.getName() +
                "," + (ellipseDetector.getTotalLineSegmentCount()) +
                "," + (ellipseDetector.getShortLineCount()) +
                "," + (ellipseDetector.getStraightLineCount()) +
                "," + (ellipseDetector.getFinalEllipseList().size() > 0));
        File arcFile = new File(outputDirectory, screenshot.getName().replaceFirst("[.][^.]+$", "") + ".ellipse.png");
        ImageIO.write(ellipseDetector.createFinalEllipseImage(), "png", arcFile);

        return ellipseDetector.getFinalEllipseList().size();
    }
}
