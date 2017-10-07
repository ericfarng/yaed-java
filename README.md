# yaed-java

## Fast Ellipse Detection - Java


This is a library to detect ellipses within an image.

### Usage:
```java
File imageFile = new File("image.png");
BufferedImage bufferedImage = ImageIO.read(imageFile);

CannyEdgeDetector cannyEdgeDetector = new CannyEdgeDetector();
// any parameters you want, these are the defaults from the ellipse reference implementation
cannyEdgeDetector.setAutoThreshold(true);
cannyEdgeDetector.setGaussianKernelRadius(1);
cannyEdgeDetector.setGaussianKernelWidth(5);
cannyEdgeDetector.setSourceImage(bufferedImage);
cannyEdgeDetector.process();
BufferedImage edgeImage = cannyEdgeDetector.getEdgeImage();

EllipseDetector ellipseDetector = new EllipseDetector();
ellipseDetector.setEdgeImage(edgeImage);
ellipseDetector.process();
BufferedImage ellipseImage = ellipseDetector.createFinalEllipseImage();
List<Ellipse> ellipseList = ellipseDetector.getFinalEllipseList();
```

### Tips:

1. Play with the 4 parameters of the Canny edge detector and make sure the ellipse edge is visible here.
2. Parameters for the ellipse detection:
  1. ```minimumArcPixelCount``` = remove small ellipses / noise.
  2. ```minimumBoundingBoxSize``` = remove straight lines
  3. ```distanceToEllipseContour```, ```distanceToEllipseContourScoreCutoff```, ```reliabilityCutoff``` = remove poorly fitting ellipses and ellipse noise
3. If things aren't working, check out the example for details how to output intermediate debug images 
4. Auto-threshold for the Canny edge detector is not fast and linear with the size of the image.
Setting low/high threshold is much faster. Also, auto-threshold still has some parameters. Check out the code if it is
not working
5. Finding straight lines only uses 5 points, but this can be turned off to use all points (but slower)

### References

##### This is a java implementation of this paper:

**[1]** A fast and effective ellipse detector for embedded vision applications

Michele Fornaciariab, Andrea Pratibc, Rita Cucchiaraab

http://ieeexplore.ieee.org/document/6470150/


##### Also uses the reference implementation in c++

https://sourceforge.net/projects/yaed/


##### The Canny edge detector came from here:

http://www.tomgibara.com/computer-vision/CannyEdgeDetector.java


##### This is also uses options from this paper

**[2]** A Fast Ellipse Detector Using Projective Invariant Pruning

Qi Jia, Xin Fan, Member, IEEE, Zhongxuan Luo, Lianbo Song, and Tie Qiu

http://ieeexplore.ieee.org/document/7929406/

### Differences from paper/reference implementation

1. The canny edge detector is not ported from the reference ellipse implementation. An unrelated implementation was
found instead. However, the automatic high/low threshold option was taken from the ellipse implementation and added
to this one.
2. The paper detects straight lines was done using an oriented bounding box and checking its width. Instead, an
axis-oriented bounding box was used and points were checked for distance from the diagonal.
3. In the reference implementation, finding parallel chords across two arcs was done by picking one point, then binary
searching for a matching point with the closest slope. In this implementation, after the binary search narrowed down
to two points, a matching point was interpolated to get the exact slope.


### Disclaimer

This is released into the public domain with no restrictions or guarantees. This project is not associated with the
original authors of any of the papers in any way.

This is intended for single threaded use only.
