package net.floodlightcontroller.util;

import java.util.concurrent.Semaphore;

/**
 * @author Souvik Das (souvikdas95@yahoo.co.in)
 * 
 * Reader-Writer Synchronization Class
 * 
 */
public class RWSync {
    private final Semaphore in;
    private final Semaphore out;
    private final Semaphore wrt;
    private int ctrin;
    private int ctrout;
    private boolean wait;
    
    public RWSync() {
        in = new Semaphore(1);
        out = new Semaphore(1);
        wrt = new Semaphore(0);
        ctrin = 0;
        ctrout = 0;
        wait = false;
    }
    
    public void reset() {
        in.release();
        out.release();
        wrt.tryAcquire();
        ctrin = 0;
        ctrout = 0;
        wait = false;
    }
    
    public void readLock() {
        try {
            in.acquire();
            ctrin++;
            in.release();
        }
        catch (InterruptedException e) {
            reset();
        }
    }
    
    public void readUnlock() {
        try {
            out.acquire();
            ctrout++;
            if (wait && ctrin == ctrout) {
                wrt.release();
            }
            out.release();
        }
        catch (InterruptedException e) {
            reset();
        }
    }
    
    public void writeLock() {
        try {
            in.acquire();
            out.acquire();
            if (ctrin == ctrout) {
                out.release();
            }
            else {
                wait = true;
                out.release();
                wrt.acquire();
                wait = false;
            }
        }
        catch (InterruptedException e) {
            reset();
        }
    }
    
    public void writeUnlock() {
        in.release();
    }
};

