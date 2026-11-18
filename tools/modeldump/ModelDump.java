import java.lang.reflect.*;
import java.util.*;

/** Dumps a LOTR LayerDefinition (static factory) as JSON: parts tree, poses, cube polygons (pos, uv, normal).
 *  Usage: java -cp <mc+lotr> ModelDump <class> <staticMethod> [floatArg] */
public class ModelDump {
    static StringBuilder sb = new StringBuilder();
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName(a[0], false, ModelDump.class.getClassLoader());
        Object ld = null;
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(a[1]) && Modifier.isStatic(m.getModifiers())) {
                m.setAccessible(true);
                ld = m.getParameterCount() == 1 ? m.invoke(null, Float.parseFloat(a.length > 2 ? a[2] : "0")) : m.invoke(null);
            }
        }
        Object mat = field(ld, "material");
        Object root = ld.getClass().getMethod("bakeRoot").invoke(ld);
        sb.append("{\"tex\":[").append(field(mat, "xTexSize")).append(",").append(field(mat, "yTexSize")).append("],\"root\":");
        part("root", root);
        sb.append("}");
        System.out.println(sb);
    }
    static Object field(Object o, String n) throws Exception {
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass())
            try { Field f = k.getDeclaredField(n); f.setAccessible(true); return f.get(o); } catch (NoSuchFieldException e) {}
        throw new NoSuchFieldException(n);
    }
    static void part(String name, Object p) throws Exception {
        sb.append("{\"name\":\"").append(name).append("\",\"pose\":[");
        String[] fs = {"x","y","z","xRot","yRot","zRot"};
        for (int i = 0; i < 6; i++) sb.append(i > 0 ? "," : "").append(field(p, fs[i]));
        sb.append("],\"visible\":").append(field(p, "visible")).append(",\"cubes\":[");
        List<?> cubes = (List<?>) field(p, "cubes");
        for (int ci = 0; ci < cubes.size(); ci++) {
            Object cube = cubes.get(ci);
            sb.append(ci > 0 ? "," : "").append("{\"min\":[").append(field(cube,"minX")).append(",").append(field(cube,"minY")).append(",").append(field(cube,"minZ"))
              .append("],\"max\":[").append(field(cube,"maxX")).append(",").append(field(cube,"maxY")).append(",").append(field(cube,"maxZ")).append("],\"polys\":[");
            Object[] polys = (Object[]) field(cube, "polygons");
            for (int pi = 0; pi < polys.length; pi++) {
                Object poly = polys[pi];
                Object n = field(poly, "normal");
                sb.append(pi > 0 ? "," : "").append("{\"n\":[").append(field(n,"x")).append(",").append(field(n,"y")).append(",").append(field(n,"z")).append("],\"v\":[");
                Object[] vs = (Object[]) field(poly, "vertices");
                for (int vi = 0; vi < vs.length; vi++) {
                    Object v = vs[vi]; Object pos = field(v, "pos");
                    sb.append(vi > 0 ? "," : "").append("[").append(field(pos,"x")).append(",").append(field(pos,"y")).append(",").append(field(pos,"z"))
                      .append(",").append(field(v,"u")).append(",").append(field(v,"v")).append("]");
                }
                sb.append("]}");
            }
            sb.append("]}");
        }
        sb.append("],\"children\":[");
        Map<?, ?> ch = (Map<?, ?>) field(p, "children");
        int i = 0;
        for (Map.Entry<?, ?> e : ch.entrySet()) { if (i++ > 0) sb.append(","); part((String) e.getKey(), e.getValue()); }
        sb.append("]}");
    }
}
