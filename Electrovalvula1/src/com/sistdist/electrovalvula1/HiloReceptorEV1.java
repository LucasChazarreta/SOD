package com.sistdist.controlador;

import java.io.*;
import java.net.*;

public class HiloReceptorEV1 extends Thread {
    private final int evId;
    private final BufferedReader br;

    public HiloReceptorEV1(Socket socket, int evId) throws IOException {
        this.evId = evId;
        this.br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    @Override
    public void run() {
        try {
            String entrada;
            while ((entrada = br.readLine()) != null) {
                System.out.println("[CTRL] Confirmación de EV" + evId + ": " + entrada);
            }
        } catch (IOException e) {
            System.out.println("[CTRL] Conexión cerrada con EV" + evId);
        }
    }
}

