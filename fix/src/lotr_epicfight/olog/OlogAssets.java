package lotr_epicfight.olog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the Olog-hai Epic Fight assets AT RUNTIME (Java port of compat-generator/monsters.py), so the mod jar ships no
 * file derived from Epic Fight's or LOTR's assets:
 *  - armature: Epic Fight's own biped armature (read from the installed Epic Fight jar), same joints and orientations,
 *    joints moved to the troll's pivots (our numbers, troll_joints) -> every biped animation plays on it;
 *  - meshes (client only): the installed LOTR troll model (TrollGeometry), skinned rigidly onto that armature.
 * Served to Epic Fight by OlogAssetMixin when it asks for assets/lotr_epicfight/animmodels/entity/olog_hai*.json.
 *
 * Spaces: "EF" = Epic Fight model space (blocks, y up, feet at 0, front = -z) = (-x/16, (24-y)/16, z/16) of MC model
 * space. Files store Blender space: (X, -Z, Y) of EF space (Epic Fight's loader applies a -90 deg X rotation).
 */
public final class OlogAssets {
    public static final String PREFIX = "/assets/lotr_epicfight/animmodels/entity/";
    private static final String BIPED = "/assets/epicfight/animmodels/entity/biped.json";
    private static final String ENTITY = "olog_hai";
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private static Boolean client;

    private OlogAssets() {}

    /** True in the game client (meshes can be built from LOTR's client model), false on a dedicated server. */
    public static boolean isClient() {
        if (client == null) {
            boolean found;
            try {
                Class.forName("net.minecraft.client.Minecraft", false, OlogAssets.class.getClassLoader());
                found = true;
            } catch (Throwable t) {
                found = false;
            }
            client = found;
        }
        return client;
    }

    /** JSON text for a requested classpath path, or null if the path is not one of ours / cannot be built. */
    public static String forPath(String path, boolean client) {
        if (path == null || !path.startsWith(PREFIX)) {
            return null;
        }
        String file = path.substring(PREFIX.length());
        String key = file + (client ? "#client" : "#server");
        String cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        String json;
        try {
            json = build(file, client);
        } catch (Throwable t) { // never break Epic Fight's loading: fall back to "file not found"
            org.slf4j.LoggerFactory.getLogger("lotr_epicfight").error("Could not build Olog-hai asset " + file, t);
            return null;
        }
        if (json != null) {
            CACHE.put(key, json);
        }
        return json;
    }

    static String build(String file, boolean client) throws Exception {
        JsonObject biped = readBipedArmature();
        if (file.equals(ENTITY + ".json")) {
            JsonObject out = new JsonObject();
            if (client) {
                Map<String, TrollGeometry.Part> model = TrollGeometry.read(0.0F);
                out.add("vertices", trollMesh(biped, model, model));
            }
            out.add("armature", armature(biped, trollJoints()));
            return out.toString();
        }
        if (!client) {
            return null;
        }
        if (file.equals(ENTITY + "_weapon.json")) {
            JsonObject out = new JsonObject();
            out.add("vertices", weaponMesh(biped, TrollGeometry.read(0.0F), "warhammer"));
            return out.toString();
        }
        if (file.equals(ENTITY + "_armor.json")) {
            // LOTR TrollRenderer$a: 'shirt' model t(1.0) for head/body/arms, 'trousers' t(0.75) for the legs
            JsonObject out = new JsonObject();
            out.add("vertices", trollMesh(biped, TrollGeometry.read(1.0F), TrollGeometry.read(0.75F)));
            out.add("armature", armature(biped, trollJoints()));
            return out.toString();
        }
        return null;
    }

    // ------------------------------------------------------------------ armature

    static JsonObject readBipedArmature() throws Exception {
        try (InputStream in = OlogAssets.class.getClassLoader().getResourceAsStream(BIPED.substring(1))) {
            InputStream stream = in != null ? in : yesmanStream();
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("armature");
            }
        }
    }

    private static InputStream yesmanStream() throws Exception {
        InputStream in = Class.forName("yesman.epicfight.main.EpicFightMod").getResourceAsStream(BIPED);
        if (in == null) {
            throw new IllegalStateException("Epic Fight biped armature not found");
        }
        return in;
    }

    static double[] mcToEf(double x, double y, double z) {
        return new double[]{-x / 16.0, (24.0 - y) / 16.0, z / 16.0};
    }

    static double[] efToBlender(double[] p) {
        return new double[]{p[0], -p[2], p[1]};
    }

    /** Joint positions (EF space) on the LOTR troll model: arm cubes span x 12..24 -> arm joints on their centre line. */
    static Map<String, double[]> trollJoints() {
        double armX = 18.0, legX = 6.5;
        Map<String, double[]> j = new LinkedHashMap<>();
        j.put("Root", mcToEf(0, 0, 0));
        j.put("Thigh_R", mcToEf(-legX, 0, 0)); j.put("Leg_R", mcToEf(-legX, 12, 0)); j.put("Knee_R", mcToEf(-legX, 12, 0));
        j.put("Thigh_L", mcToEf(legX, 0, 0)); j.put("Leg_L", mcToEf(legX, 12, 0)); j.put("Knee_L", mcToEf(legX, 12, 0));
        j.put("Torso", mcToEf(0, -1, 0)); j.put("Chest", mcToEf(0, -14, 0)); j.put("Head", mcToEf(0, -27, -6));
        j.put("Shoulder_R", mcToEf(0, -27, 0)); j.put("Shoulder_L", mcToEf(0, -27, 0));
        j.put("Arm_R", mcToEf(-armX, -23, 0)); j.put("Elbow_R", mcToEf(-armX, -14, 0));
        j.put("Hand_R", mcToEf(-armX, -14, 0)); j.put("Tool_R", mcToEf(-armX, 3, -2));
        j.put("Arm_L", mcToEf(armX, -23, 0)); j.put("Elbow_L", mcToEf(armX, -14, 0));
        j.put("Hand_L", mcToEf(armX, -14, 0)); j.put("Tool_L", mcToEf(armX, 3, -2));
        return j;
    }

    static JsonObject armature(JsonObject biped, Map<String, double[]> jointPosEf) {
        JsonObject out = new JsonObject();
        out.add("joints", biped.get("joints").deepCopy());
        JsonArray hierarchy = new JsonArray();
        hierarchy.add(convert(biped.getAsJsonArray("hierarchy").get(0).getAsJsonObject(), identity(), identity(), jointPosEf));
        out.add("hierarchy", hierarchy);
        return out;
    }

    private static JsonObject convert(JsonObject node, double[][] parentOld, double[][] parentNew, Map<String, double[]> jointPosEf) {
        String name = node.get("name").getAsString();
        double[][] gOld = mul(parentOld, rows(node.getAsJsonArray("transform")));
        double[] b = efToBlender(jointPosEf.get(name));
        double[][] gNew = {
                {gOld[0][0], gOld[0][1], gOld[0][2], b[0]},
                {gOld[1][0], gOld[1][1], gOld[1][2], b[1]},
                {gOld[2][0], gOld[2][1], gOld[2][2], b[2]},
                {0, 0, 0, 1}};
        double[][] local = mul(invRigid(parentNew), gNew);
        JsonObject out = new JsonObject();
        out.addProperty("name", name);
        JsonArray t = new JsonArray();
        for (double[] r : local) {
            for (double v : r) {
                t.add(r6(v));
            }
        }
        out.add("transform", t);
        JsonArray children = node.getAsJsonArray("children");
        if (children != null && !children.isEmpty()) {
            JsonArray outChildren = new JsonArray();
            for (JsonElement c : children) {
                outChildren.add(convert(c.getAsJsonObject(), gOld, gNew, jointPosEf));
            }
            out.add("children", outChildren);
        }
        return out;
    }

    private static double[][] identity() {
        return new double[][]{{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}};
    }

    private static double[][] rows(JsonArray t) {
        double[][] m = new double[4][4];
        for (int i = 0; i < 16; i++) {
            m[i / 4][i % 4] = t.get(i).getAsDouble();
        }
        return m;
    }

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] m = new double[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double s = 0;
                for (int k = 0; k < 4; k++) {
                    s += a[i][k] * b[k][j];
                }
                m[i][j] = s;
            }
        }
        return m;
    }

    private static double[][] invRigid(double[][] m) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i][j] = m[j][i];
            }
        }
        double[][] out = new double[4][4];
        for (int i = 0; i < 3; i++) {
            double t = 0;
            for (int k = 0; k < 3; k++) {
                t -= r[i][k] * m[k][3];
            }
            out[i] = new double[]{r[i][0], r[i][1], r[i][2], t};
        }
        out[3] = new double[]{0, 0, 0, 1};
        return out;
    }

    static double r6(double v) {
        return Math.round(v * 1e6) / 1e6 + 0.0; // + 0.0 turns -0.0 into 0.0
    }

    // ------------------------------------------------------------------ meshes

    interface JointRule {
        String joint(int cubeIndex, double[] localPoint);
    }

    /** Body mesh (or armour mesh when legs come from another inflated model). */
    static JsonObject trollMesh(JsonObject biped, Map<String, TrollGeometry.Part> model, Map<String, TrollGeometry.Part> legs) {
        MeshBuilder mb = new MeshBuilder(biped.getAsJsonArray("joints"));
        addPart(mb, model, "head", "head", (ci, p) -> "Head", 0);
        // body: bottom vertices follow the waist (Torso), top ones the Chest -> bends smoothly, arms stay attached
        addPart(mb, model, "body", "torso", (ci, p) -> p[1] < -14 ? "Chest" : "Torso", 0);
        addPart(mb, model, "right_arm", "rightArm", (ci, p) -> ci == 0 ? "Arm_R" : "Hand_R", 0);
        addPart(mb, model, "left_arm", "leftArm", (ci, p) -> ci == 0 ? "Arm_L" : "Hand_L", 0);
        addPart(mb, legs, "right_leg", "rightLeg", (ci, p) -> ci == 0 ? "Thigh_R" : "Leg_R", 0);
        addPart(mb, legs, "left_leg", "leftLeg", (ci, p) -> ci == 0 ? "Thigh_L" : "Leg_L", 0);
        return mb.toJson();
    }

    /** Weapon (drawn with LOTR's weapon texture): rigid on the right hand, held at arm.xRot - 90 deg like LOTR's model. */
    static JsonObject weaponMesh(JsonObject biped, Map<String, TrollGeometry.Part> model, String weapon) {
        MeshBuilder mb = new MeshBuilder(biped.getAsJsonArray("joints"));
        List<String> parts = switch (weapon) {
            case "spiked_club" -> List.of("club", "club_spikes");
            case "battleaxe" -> List.of("battleaxe");
            default -> List.of("warhammer");
        };
        for (String p : parts) {
            addPart(mb, model, p, p, (ci, pt) -> "Hand_R", -Math.PI / 2);
        }
        return mb.toJson();
    }

    private static double[] rotX(double x, double y, double z, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[]{x, y * c - z * s, y * s + z * c};
    }

    private static void addPart(MeshBuilder mb, Map<String, TrollGeometry.Part> model, String partName, String meshPart,
                                JointRule rule, double extraRotX) {
        TrollGeometry.Part part = model.get(partName);
        if (part == null) {
            throw new IllegalStateException("LOTR troll model has no part " + partName);
        }
        for (int ci = 0; ci < part.cubes().size(); ci++) {
            for (TrollGeometry.Poly poly : part.cubes().get(ci).polys()) {
                double[][] verts = new double[4][];
                double[][] uvs = new double[4][];
                String[] joints = new String[4];
                for (int k = 0; k < 4; k++) {
                    double[] v = poly.vertices()[k];
                    double[] local = rotX(v[0], v[1], v[2], extraRotX);
                    verts[k] = mcToEf(local[0] + part.x(), local[1] + part.y(), local[2] + part.z());
                    uvs[k] = new double[]{v[3], v[4]};
                    joints[k] = rule.joint(ci, new double[]{v[0], v[1], v[2]});
                }
                double[] n = rotX(poly.normal()[0], poly.normal()[1], poly.normal()[2], extraRotX);
                mb.addQuad(meshPart, verts, uvs, new double[]{-n[0], -n[1], n[2]}, joints);
            }
        }
    }

    static final class MeshBuilder {
        private final Map<String, Integer> jointId = new LinkedHashMap<>();
        private final Map<String, Integer> posIdx = new LinkedHashMap<>(), uvIdx = new LinkedHashMap<>(), nrmIdx = new LinkedHashMap<>();
        private final List<double[]> pos = new ArrayList<>(), uv = new ArrayList<>(), nrm = new ArrayList<>();
        private final List<Integer> vjoint = new ArrayList<>();
        private final Map<String, List<Integer>> parts = new LinkedHashMap<>();

        MeshBuilder(JsonArray joints) {
            for (int i = 0; i < joints.size(); i++) {
                jointId.put(joints.get(i).getAsString(), i);
            }
        }

        private static String key(double... v) {
            StringBuilder sb = new StringBuilder();
            for (double d : v) {
                sb.append(d).append(',');
            }
            return sb.toString();
        }

        private static int index(Map<String, Integer> idx, List<double[]> store, String key, double[] value) {
            Integer i = idx.get(key);
            if (i == null) {
                i = store.size();
                idx.put(key, i);
                store.add(value);
            }
            return i;
        }

        void addQuad(String part, double[][] vertsEf, double[][] uvs, double[] normalEf, String[] joints) {
            int[][] ids = new int[4][];
            for (int k = 0; k < 4; k++) {
                double[] b = efToBlender(vertsEf[k]);
                double[] bp = {r6(b[0]), r6(b[1]), r6(b[2])};
                int before = pos.size();
                int pi = index(posIdx, pos, key(bp) + joints[k], bp);
                if (pos.size() > before) {
                    Integer jid = jointId.get(joints[k]);
                    if (jid == null) {
                        throw new IllegalStateException("Unknown joint " + joints[k]);
                    }
                    vjoint.add(jid);
                }
                double[] u = {r6(uvs[k][0]), r6(uvs[k][1])};
                int ui = index(uvIdx, uv, key(u), u);
                double[] nb = efToBlender(normalEf);
                double[] nr = {r6(nb[0]), r6(nb[1]), r6(nb[2])};
                int ni = index(nrmIdx, nrm, key(nr), nr);
                ids[k] = new int[]{pi, ui, ni};
            }
            List<Integer> tris = parts.computeIfAbsent(part, p -> new ArrayList<>());
            for (int[] tri : new int[][]{{0, 1, 2}, {0, 2, 3}}) {
                for (int k : tri) {
                    tris.add(ids[k][0]);
                    tris.add(ids[k][1]);
                    tris.add(ids[k][2]);
                }
            }
        }

        private static JsonObject block(int stride, int count, JsonArray array) {
            JsonObject o = new JsonObject();
            o.addProperty("stride", stride);
            o.addProperty("count", count);
            o.add("array", array);
            return o;
        }

        private static JsonArray flat(List<double[]> list) {
            JsonArray a = new JsonArray();
            for (double[] v : list) {
                for (double d : v) {
                    a.add(d);
                }
            }
            return a;
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.add("positions", block(3, pos.size(), flat(pos)));
            o.add("uvs", block(2, uv.size(), flat(uv)));
            o.add("normals", block(3, nrm.size(), flat(nrm)));
            JsonArray vcounts = new JsonArray();
            JsonArray vindices = new JsonArray();
            for (int j : vjoint) {
                vcounts.add(1);
                vindices.add(j);
                vindices.add(0);
            }
            o.add("vcounts", block(1, pos.size(), vcounts));
            JsonArray weights = new JsonArray();
            weights.add(1.0);
            o.add("weights", block(1, 1, weights));
            o.add("vindices", block(1, 2 * pos.size(), vindices));
            JsonObject partsJson = new JsonObject();
            for (Map.Entry<String, List<Integer>> e : parts.entrySet()) {
                JsonArray a = new JsonArray();
                for (Integer i : e.getValue()) {
                    a.add(i);
                }
                partsJson.add(e.getKey(), block(3, e.getValue().size() / 3, a));
            }
            o.add("parts", partsJson);
            return o;
        }
    }
}
