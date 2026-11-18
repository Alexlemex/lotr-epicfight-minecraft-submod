package lotr_epicfight.olog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the LOTR troll model (lotr.modern.client.ag.t(inflate), a LayerDefinition) from the installed LOTR jar, at
 * runtime, by reflection only: no client class is referenced at compile time, so this class also loads on a dedicated
 * server (where read() simply fails because the client model classes do not exist).
 * Units: model pixels, Minecraft model space (y down, ground at y = 24); u/v normalised (0..1).
 */
public final class TrollGeometry {
    private static final String MODEL_CLASS = "lotr.modern.client.ag";
    private static final String MODEL_FACTORY = "t";

    public record Poly(double[] normal, double[][] vertices) {} // vertex = {x, y, z, u, v}
    public record Cube(List<Poly> polys) {}
    public record Part(String name, double x, double y, double z, List<Cube> cubes) {}

    private TrollGeometry() {}

    /** Top-level parts of the troll model baked with the given cube inflation (0 = body, 1.0 = shirt, 0.75 = trousers). */
    public static Map<String, Part> read(float inflate) throws ReflectiveOperationException {
        Class<?> modelClass = Class.forName(MODEL_CLASS);
        Method factory = modelClass.getMethod(MODEL_FACTORY, float.class);
        Object layerDefinition = factory.invoke(null, inflate);
        Object root = layerDefinition.getClass().getMethod("bakeRoot").invoke(layerDefinition);
        Map<String, Part> parts = new LinkedHashMap<>();
        collect(root, parts);
        return parts;
    }

    private static void collect(Object modelPart, Map<String, Part> out) throws ReflectiveOperationException {
        Map<?, ?> children = (Map<?, ?>) field(modelPart, "children");
        for (Map.Entry<?, ?> e : children.entrySet()) {
            Object child = e.getValue();
            String name = (String) e.getKey();
            out.putIfAbsent(name, part(name, child));
            collect(child, out);
        }
    }

    private static Part part(String name, Object modelPart) throws ReflectiveOperationException {
        List<Cube> cubes = new ArrayList<>();
        for (Object cube : (List<?>) field(modelPart, "cubes")) {
            List<Poly> polys = new ArrayList<>();
            for (Object poly : (Object[]) field(cube, "polygons")) {
                Object n = field(poly, "normal");
                double[] normal = {num(n, "x"), num(n, "y"), num(n, "z")};
                Object[] verts = (Object[]) field(poly, "vertices");
                double[][] vs = new double[verts.length][];
                for (int i = 0; i < verts.length; i++) {
                    Object pos = field(verts[i], "pos");
                    vs[i] = new double[]{num(pos, "x"), num(pos, "y"), num(pos, "z"), num(verts[i], "u"), num(verts[i], "v")};
                }
                polys.add(new Poly(normal, vs));
            }
            cubes.add(new Cube(polys));
        }
        return new Part(name, num(modelPart, "x"), num(modelPart, "y"), num(modelPart, "z"), cubes);
    }

    /** float -> double through its decimal form, like the offline dump (keeps 0.1f as 0.1). */
    private static double num(Object o, String name) throws ReflectiveOperationException {
        return Double.parseDouble(Float.toString(((Number) field(o, name)).floatValue()));
    }

    private static Object field(Object o, String name) throws ReflectiveOperationException {
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
