import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Original logo for LOTRDL X Epic Fight (no third-party art). Usage: java Logo <out.png> [size] */
public class Logo {
    static final Color GOLD = new Color(0xE8C46A), GOLD_DARK = new Color(0x8A6420), GOLD_LIGHT = new Color(0xFFF1C2);
    static final Color STEEL = new Color(0xD9DEE4), STEEL_DARK = new Color(0x6F7A86);

    public static void main(String[] a) throws Exception {
        int S = a.length > 1 ? Integer.parseInt(a[1]) : 512;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(S / 512.0, S / 512.0);

        // background: ember glow on near-black
        g.setPaint(new RadialGradientPaint(new Point2D.Float(256, 270), 330,
                new float[]{0f, 0.45f, 1f}, new Color[]{new Color(0x7A2A10), new Color(0x2A0E08), new Color(0x07060A)}));
        g.fillRect(0, 0, 512, 512);

        // embers
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 70; i++) {
            float x = r.nextFloat() * 512, y = 120 + r.nextFloat() * 392, s = 1.5f + r.nextFloat() * 3.5f;
            g.setColor(new Color(255, 120 + r.nextInt(100), 40, 40 + r.nextInt(120)));
            g.fill(new Ellipse2D.Float(x, y, s, s));
        }

        // ring behind the swords
        g.setStroke(new BasicStroke(14f));
        g.setPaint(new GradientPaint(150, 150, GOLD_LIGHT, 360, 380, GOLD_DARK));
        g.draw(new Ellipse2D.Float(146, 158, 220, 220));
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(0x3A2608));
        g.draw(new Ellipse2D.Float(139, 151, 234, 234));
        g.draw(new Ellipse2D.Float(153, 165, 206, 206));

        // crossed swords = the "X"
        sword(g, 256, 268, -42);
        sword(g, 256, 268, 42);

        // titles
        title(g, "LOTRDL", 256, 104, 86f);
        title(g, "EPIC FIGHT", 256, 470, 62f);

        // frame
        g.setStroke(new BasicStroke(6f));
        g.setColor(GOLD_DARK);
        g.draw(new RoundRectangle2D.Float(10, 10, 492, 492, 36, 36));
        g.setStroke(new BasicStroke(2f));
        g.setColor(GOLD);
        g.draw(new RoundRectangle2D.Float(20, 20, 472, 472, 28, 28));

        g.dispose();
        ImageIO.write(img, "png", new File(a[0]));
    }

    static void sword(Graphics2D g0, float cx, float cy, double deg) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(cx, cy);
        g.rotate(Math.toRadians(deg));
        // shadow
        g.setColor(new Color(0, 0, 0, 110));
        g.fill(blade(4, 6));
        // blade (tip up), length ~300
        Shape blade = blade(0, 0);
        g.setPaint(new GradientPaint(-14, 0, STEEL, 14, 0, STEEL_DARK));
        g.fill(blade);
        g.setColor(new Color(0x2B3038));
        g.setStroke(new BasicStroke(2.5f));
        g.draw(blade);
        g.setColor(new Color(255, 255, 255, 150)); // fuller
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(-1, 60, -1, -150));
        // guard
        RoundRectangle2D guard = new RoundRectangle2D.Float(-54, 72, 108, 16, 10, 10);
        g.setPaint(new GradientPaint(0, 72, GOLD_LIGHT, 0, 88, GOLD_DARK));
        g.fill(guard);
        g.setColor(new Color(0x3A2608));
        g.draw(guard);
        // grip
        Rectangle2D grip = new Rectangle2D.Float(-8, 88, 16, 46);
        g.setPaint(new GradientPaint(-8, 0, new Color(0x5A3018), 8, 0, new Color(0x2A140A)));
        g.fill(grip);
        g.setColor(new Color(0x1A0C06));
        for (int y = 92; y < 134; y += 7) {
            g.draw(new Line2D.Float(-8, y, 8, y + 4));
        }
        // pommel
        Ellipse2D pommel = new Ellipse2D.Float(-13, 132, 26, 26);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(-4, 140), 16, new float[]{0f, 1f}, new Color[]{GOLD_LIGHT, GOLD_DARK}));
        g.fill(pommel);
        g.setColor(new Color(0x3A2608));
        g.draw(pommel);
        g.dispose();
    }

    static Shape blade(float dx, float dy) {
        Path2D p = new Path2D.Float();
        p.moveTo(dx, -182 + dy);
        p.lineTo(13 + dx, -150 + dy);
        p.lineTo(11 + dx, 72 + dy);
        p.lineTo(-11 + dx, 72 + dy);
        p.lineTo(-13 + dx, -150 + dy);
        p.closePath();
        return p;
    }

    static void title(Graphics2D g, String text, float cx, float baseline, float size) {
        Font font = new Font("Georgia", Font.BOLD, Math.round(size));
        FontRenderContext frc = g.getFontRenderContext();
        GlyphVector gv = font.createGlyphVector(frc, text);
        Rectangle2D b = gv.getVisualBounds();
        Shape s = gv.getOutline((float) (cx - b.getWidth() / 2 - b.getX()), baseline);
        Graphics2D g2 = (Graphics2D) g.create();
        // dark outline + drop shadow
        g2.translate(3, 4);
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fill(s);
        g2.translate(-3, -4);
        g2.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(0x1E1206));
        g2.draw(s);
        Rectangle2D sb = s.getBounds2D();
        g2.setPaint(new LinearGradientPaint((float) sb.getX(), (float) sb.getY(), (float) sb.getX(), (float) sb.getMaxY(),
                new float[]{0f, 0.5f, 1f}, new Color[]{GOLD_LIGHT, GOLD, GOLD_DARK}));
        g2.fill(s);
        g2.dispose();
    }
}
