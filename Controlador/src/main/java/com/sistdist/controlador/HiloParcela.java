package com.sistdist.controlador;

import java.io.PrintWriter;
import java.util.Map;

public class HiloParcela extends Thread {
    private final int parcelaId;
    private final Map<Integer, Double> humedades;
    private final Map<Integer, PrintWriter> conexionesEV;

    public HiloParcela(int parcelaId, Map<Integer, Double> humedades, Map<Integer, PrintWriter> conexionesEV) {
        this.parcelaId = parcelaId;
        this.humedades = humedades;
        this.conexionesEV = conexionesEV;
        setName("HiloParcela-" + parcelaId);
        setDaemon(true);
    }

    @Override
    public void run() {
        // Este hilo ya no decide ni riega. El scheduler (impresor) decide y encola.
        try {
            while (true) {
                Thread.sleep(10_000);
            }
        } catch (InterruptedException e) {
            // salir
        }
    }
}
