/**
 * Created by Eric Farng on 10/7/2017.
 */

public class Ellipse {
    // arc numbers go counter-clockwise (following quadrants)
    protected EllipseDetector.EllipseQuarterArc arc3, arc2, arc1;

    protected EllipseDetector.ParallelChords chord3start2mid;
    protected EllipseDetector.ParallelChords chord3mid2end;
    protected EllipseDetector.ParallelChords chord2start1mid;
    protected EllipseDetector.ParallelChords chord2mid1end;

    protected float[] center32, center21;

    // x = center[0]
    // y = center[1]
    public float[] center;
    public float rho;
    public float aAxis;
    public float bAxis;

    // from 0.0 - 1.0
    public float ellipseScore;

}
