package com.sistdist.controlador;

public final class PuertoEM {
    public static int porUid(long uid) {
        if (uid == 101) return 10000;
        if (uid == 102) return 10001;
        if (uid == 103) return 10002;
        return 10000;
    }
}
