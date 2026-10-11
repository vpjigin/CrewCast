package com.solocrew.crewcast.share;

import java.util.Comparator;

/** Case-insensitive natural sort (file2 before file10). */
public final class NaturalOrder {

    private NaturalOrder() {
    }

    public static int compareIgnoreCase(String a, String b) {
        int ia = 0;
        int ib = 0;
        int na = a.length();
        int nb = b.length();
        while (ia < na && ib < nb) {
            char ca = a.charAt(ia);
            char cb = b.charAt(ib);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int ja = ia;
                int jb = ib;
                while (ja < na && a.charAt(ja) == '0') {
                    ja++;
                }
                while (jb < nb && b.charAt(jb) == '0') {
                    jb++;
                }
                int ka = ja;
                int kb = jb;
                while (ka < na && Character.isDigit(a.charAt(ka))) {
                    ka++;
                }
                while (kb < nb && Character.isDigit(b.charAt(kb))) {
                    kb++;
                }
                int lenA = ka - ja;
                int lenB = kb - jb;
                if (lenA != lenB) {
                    return Integer.compare(lenA, lenB);
                }
                for (int k = 0; k < lenA; k++) {
                    char da = a.charAt(ja + k);
                    char db = b.charAt(jb + k);
                    if (da != db) {
                        return da - db;
                    }
                }
                ia = ka;
                ib = kb;
            } else {
                char xa = Character.toLowerCase(ca);
                char xb = Character.toLowerCase(cb);
                if (xa != xb) {
                    return xa - xb;
                }
                ia++;
                ib++;
            }
        }
        return Integer.compare(na - ia, nb - ib);
    }

    public static Comparator<String> stringsIgnoreCase() {
        return NaturalOrder::compareIgnoreCase;
    }
}
