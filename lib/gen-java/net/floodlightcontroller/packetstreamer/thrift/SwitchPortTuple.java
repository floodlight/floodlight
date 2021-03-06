/**
 * Autogenerated by Thrift Compiler (0.14.1)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package net.floodlightcontroller.packetstreamer.thrift;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
/**
 * A struct that defines switch port tuple
 */
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.14.1)", date = "2021-04-13")
public class SwitchPortTuple implements org.apache.thrift.TBase<SwitchPortTuple, SwitchPortTuple._Fields>, java.io.Serializable, Cloneable, Comparable<SwitchPortTuple> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("SwitchPortTuple");

  private static final org.apache.thrift.protocol.TField DPID_FIELD_DESC = new org.apache.thrift.protocol.TField("dpid", org.apache.thrift.protocol.TType.I64, (short)1);
  private static final org.apache.thrift.protocol.TField PORT_FIELD_DESC = new org.apache.thrift.protocol.TField("port", org.apache.thrift.protocol.TType.I16, (short)2);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new SwitchPortTupleStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new SwitchPortTupleTupleSchemeFactory();

  public long dpid; // required
  public short port; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    DPID((short)1, "dpid"),
    PORT((short)2, "port");

    private static final java.util.Map<java.lang.String, _Fields> byName = new java.util.HashMap<java.lang.String, _Fields>();

    static {
      for (_Fields field : java.util.EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    @org.apache.thrift.annotation.Nullable
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // DPID
          return DPID;
        case 2: // PORT
          return PORT;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new java.lang.IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    @org.apache.thrift.annotation.Nullable
    public static _Fields findByName(java.lang.String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final java.lang.String _fieldName;

    _Fields(short thriftId, java.lang.String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public java.lang.String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  private static final int __DPID_ISSET_ID = 0;
  private static final int __PORT_ISSET_ID = 1;
  private byte __isset_bitfield = 0;
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.DPID, new org.apache.thrift.meta_data.FieldMetaData("dpid", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    tmpMap.put(_Fields.PORT, new org.apache.thrift.meta_data.FieldMetaData("port", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I16)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(SwitchPortTuple.class, metaDataMap);
  }

  public SwitchPortTuple() {
  }

  public SwitchPortTuple(
    long dpid,
    short port)
  {
    this();
    this.dpid = dpid;
    setDpidIsSet(true);
    this.port = port;
    setPortIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public SwitchPortTuple(SwitchPortTuple other) {
    __isset_bitfield = other.__isset_bitfield;
    this.dpid = other.dpid;
    this.port = other.port;
  }

  public SwitchPortTuple deepCopy() {
    return new SwitchPortTuple(this);
  }

  @Override
  public void clear() {
    setDpidIsSet(false);
    this.dpid = 0;
    setPortIsSet(false);
    this.port = 0;
  }

  public long getDpid() {
    return this.dpid;
  }

  public SwitchPortTuple setDpid(long dpid) {
    this.dpid = dpid;
    setDpidIsSet(true);
    return this;
  }

  public void unsetDpid() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __DPID_ISSET_ID);
  }

  /** Returns true if field dpid is set (has been assigned a value) and false otherwise */
  public boolean isSetDpid() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __DPID_ISSET_ID);
  }

  public void setDpidIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __DPID_ISSET_ID, value);
  }

  public short getPort() {
    return this.port;
  }

  public SwitchPortTuple setPort(short port) {
    this.port = port;
    setPortIsSet(true);
    return this;
  }

  public void unsetPort() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __PORT_ISSET_ID);
  }

  /** Returns true if field port is set (has been assigned a value) and false otherwise */
  public boolean isSetPort() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __PORT_ISSET_ID);
  }

  public void setPortIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __PORT_ISSET_ID, value);
  }

  public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
    switch (field) {
    case DPID:
      if (value == null) {
        unsetDpid();
      } else {
        setDpid((java.lang.Long)value);
      }
      break;

    case PORT:
      if (value == null) {
        unsetPort();
      } else {
        setPort((java.lang.Short)value);
      }
      break;

    }
  }

  @org.apache.thrift.annotation.Nullable
  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case DPID:
      return getDpid();

    case PORT:
      return getPort();

    }
    throw new java.lang.IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new java.lang.IllegalArgumentException();
    }

    switch (field) {
    case DPID:
      return isSetDpid();
    case PORT:
      return isSetPort();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof SwitchPortTuple)
      return this.equals((SwitchPortTuple)that);
    return false;
  }

  public boolean equals(SwitchPortTuple that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_dpid = true;
    boolean that_present_dpid = true;
    if (this_present_dpid || that_present_dpid) {
      if (!(this_present_dpid && that_present_dpid))
        return false;
      if (this.dpid != that.dpid)
        return false;
    }

    boolean this_present_port = true;
    boolean that_present_port = true;
    if (this_present_port || that_present_port) {
      if (!(this_present_port && that_present_port))
        return false;
      if (this.port != that.port)
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(dpid);

    hashCode = hashCode * 8191 + port;

    return hashCode;
  }

  @Override
  public int compareTo(SwitchPortTuple other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetDpid(), other.isSetDpid());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetDpid()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.dpid, other.dpid);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetPort(), other.isSetPort());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetPort()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.port, other.port);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  @org.apache.thrift.annotation.Nullable
  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    scheme(iprot).read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    scheme(oprot).write(oprot, this);
  }

  @Override
  public java.lang.String toString() {
    java.lang.StringBuilder sb = new java.lang.StringBuilder("SwitchPortTuple(");
    boolean first = true;

    sb.append("dpid:");
    sb.append(this.dpid);
    first = false;
    if (!first) sb.append(", ");
    sb.append("port:");
    sb.append(this.port);
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, java.lang.ClassNotFoundException {
    try {
      // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
      __isset_bitfield = 0;
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class SwitchPortTupleStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public SwitchPortTupleStandardScheme getScheme() {
      return new SwitchPortTupleStandardScheme();
    }
  }

  private static class SwitchPortTupleStandardScheme extends org.apache.thrift.scheme.StandardScheme<SwitchPortTuple> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, SwitchPortTuple struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // DPID
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.dpid = iprot.readI64();
              struct.setDpidIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // PORT
            if (schemeField.type == org.apache.thrift.protocol.TType.I16) {
              struct.port = iprot.readI16();
              struct.setPortIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      struct.validate();
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot, SwitchPortTuple struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      oprot.writeFieldBegin(DPID_FIELD_DESC);
      oprot.writeI64(struct.dpid);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(PORT_FIELD_DESC);
      oprot.writeI16(struct.port);
      oprot.writeFieldEnd();
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class SwitchPortTupleTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public SwitchPortTupleTupleScheme getScheme() {
      return new SwitchPortTupleTupleScheme();
    }
  }

  private static class SwitchPortTupleTupleScheme extends org.apache.thrift.scheme.TupleScheme<SwitchPortTuple> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, SwitchPortTuple struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetDpid()) {
        optionals.set(0);
      }
      if (struct.isSetPort()) {
        optionals.set(1);
      }
      oprot.writeBitSet(optionals, 2);
      if (struct.isSetDpid()) {
        oprot.writeI64(struct.dpid);
      }
      if (struct.isSetPort()) {
        oprot.writeI16(struct.port);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, SwitchPortTuple struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet incoming = iprot.readBitSet(2);
      if (incoming.get(0)) {
        struct.dpid = iprot.readI64();
        struct.setDpidIsSet(true);
      }
      if (incoming.get(1)) {
        struct.port = iprot.readI16();
        struct.setPortIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

