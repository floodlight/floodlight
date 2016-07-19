package net.floodlightcontroller.core.types;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import net.floodlightcontroller.core.web.serializers.JsonObjectWrapperSerializer;

/**
 * Use this class to wrap return types that will otherwise be 
 * serialized by Jackson as arrays. The outer-most type
 * of JSON must be an object. The end result will be:
 * 
 * {
 *      "result": <Object-you-provide>
 * }
 * 
 * instead of Jackson-default for your type:s
 * 
 * [
 *      <Object-you-provide's data>
 * ]
 * 
 * which is an illegal JSON construct.
 * 
 * @author rizard
 */
@JsonSerialize(using=JsonObjectWrapperSerializer.class)
public class JsonObjectWrapper {
    private Object o;
    
    private JsonObjectWrapper() { }
    
    private JsonObjectWrapper(Object o) {
        this.o = o;
    }
    
    public static JsonObjectWrapper of(Object o) {
        return new JsonObjectWrapper(o);
    }
    
    public Object getObject() {
        return o;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((o == null) ? 0 : o.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        JsonObjectWrapper other = (JsonObjectWrapper) obj;
        if (o == null) {
            if (other.o != null)
                return false;
        } else if (!o.equals(other.o))
            return false;
        return true;
    }
}