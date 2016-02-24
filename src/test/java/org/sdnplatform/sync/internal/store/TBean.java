package org.sdnplatform.sync.internal.store;

public class TBean {
    String s;
    int i;

    public TBean(String s, int i) {
        super();
        this.s = s;
        this.i = i;
    }
    public TBean() {
        super();
    }
    public String getS() {
        return s;
    }
    public void setS(String s) {
        this.s = s;
    }
    public int getI() {
        return i;
    }
    public void setI(int i) {
        this.i = i;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + i;
        result = prime * result + ((s == null) ? 0 : s.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        TBean other = (TBean) obj;
        if (i != other.i) return false;
        if (s == null) {
            if (other.s != null) return false;
        } else if (!s.equals(other.s)) return false;
        return true;
    }
    @Override
    public String toString() {
        return "TestBean [s=" + s + ", i=" + i + "]";
    }
}
