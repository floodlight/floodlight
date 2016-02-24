package org.sdnplatform.sync.internal.store;

import java.io.Serializable;

/**
 * Represent a key in the sync system.  Keys consist of a namespace and a
 * key name.  Namespaces should be dot-separated such as "com.bigswitch.device"
 * @author readams
 *
 */
public class Key implements Serializable {

    private static final long serialVersionUID = -3998115385199627376L;

    private String namespace;
    private String key;

    public Key() {
        super();
    }

    public Key(String namespace, String key) {
        super();
        this.namespace = namespace;
        this.key = key;
    }

    public String getNamespace() {
        return namespace;
    }
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    public String getKey() {
        return key;
    }
    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result =
                prime * result
                        + ((namespace == null) ? 0 : namespace.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Key other = (Key) obj;
        if (key == null) {
            if (other.key != null) return false;
        } else if (!key.equals(other.key)) return false;
        if (namespace == null) {
            if (other.namespace != null) return false;
        } else if (!namespace.equals(other.namespace)) return false;
        return true;
    }

}
