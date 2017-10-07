
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Created by Eric Farng on 10/7/2017.
 *
 * not exactly efficient ...
 */

public class TestEllipseCreate {
    public static void main(String[] args) throws IOException {
        Random random = new Random();

        File directory = new File("testEllipse");
        if (directory.exists() == false) {
            directory.mkdirs();
        }

        for (int i = 0; i < 1000; i++) {
            float centerX = 200 + (random.nextFloat() * 10 - 5);
            float centerY = 200 + (random.nextFloat() * 10 - 5);
            float rho = random.nextFloat() * 3.14159265359f;
            float n = random.nextFloat() * 0.95f + 0.05f;
            float aAxis = random.nextFloat() * 175 + 15;
            float bAxis = aAxis * n;

            if (bAxis < 25) {
                i--;
                continue;
            }

            BufferedImage image = new BufferedImage(400, 400, BufferedImage.TYPE_3BYTE_BGR);


            for (float angle = 0; angle < 360; angle += 0.2) {
                for (float radius = aAxis; radius >= 0; radius -= 0.2) {
                    float aAxisT = radius;
                    float bAxisT  = aAxisT * n;
                    float radians = angle * 3.14159265359f / 180;
                    float x = (float) (aAxisT * Math.cos(radians) * Math.cos(rho) + bAxisT * Math.sin(radians) * Math.sin(rho));
                    float y = (float) (-1 * aAxisT * Math.cos(radians) * Math.sin(rho) + bAxisT * Math.sin(radians) * Math.cos(rho));
                    x = Math.round(centerX + x);
                    y = Math.round(centerY - y);
                    if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) {
                        image.setRGB((int) x, (int) y, -1);
                    }
                }
            }

            File file = new File(directory, "x-" + centerX+ "-y-" + centerY + "-rho-" + rho + "-n-" + n + "-aAxis-" + aAxis + "-bAxis-" + bAxis + ".png");
            ImageIO.write(image, "png", file);

        }
    }
}
