package com.sistdist.controlador;

import java.util.concurrent.locks.ReentrantLock;

public class Bomba {

    private final ReentrantLock lock = new ReentrantLock(true); // fair

    public void adquirir(String quien) {
        lock.lock();
        System.out.println("[" + quien + "] adquirió la bomba.");
    }

    public void liberar(String quien) {
        System.out.println("[" + quien + "] liberó la bomba.");
        lock.unlock();
    }
}
