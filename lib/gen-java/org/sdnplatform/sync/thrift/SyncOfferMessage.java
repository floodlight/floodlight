/**
 * Autogenerated by Thrift Compiler (0.14.1)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package org.sdnplatform.sync.thrift;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.14.1)", date = "2021-04-13")
public class SyncOfferMessage implements org.apache.thrift.TBase<SyncOfferMessage, SyncOfferMessage._Fields>, java.io.Serializable, Cloneable, Comparable<SyncOfferMessage> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("SyncOfferMessage");

  private static final org.apache.thrift.protocol.TField HEADER_FIELD_DESC = new org.apache.thrift.protocol.TField("header", org.apache.thrift.protocol.TType.STRUCT, (short)1);
  private static final org.apache.thrift.protocol.TField STORE_FIELD_DESC = new org.apache.thrift.protocol.TField("store", org.apache.thrift.protocol.TType.STRUCT, (short)2);
  private static final org.apache.thrift.protocol.TField VERSIONS_FIELD_DESC = new org.apache.thrift.protocol.TField("versions", org.apache.thrift.protocol.TType.LIST, (short)3);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new SyncOfferMessageStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new SyncOfferMessageTupleSchemeFactory();

  public @org.apache.thrift.annotation.Nullable AsyncMessageHeader header; // required
  public @org.apache.thrift.annotation.Nullable Store store; // required
  public @org.apache.thrift.annotation.Nullable java.util.List<KeyedVersions> versions; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    HEADER((short)1, "header"),
    STORE((short)2, "store"),
    VERSIONS((short)3, "versions");

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
        case 1: // HEADER
          return HEADER;
        case 2: // STORE
          return STORE;
        case 3: // VERSIONS
          return VERSIONS;
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
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.HEADER, new org.apache.thrift.meta_data.FieldMetaData("header", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, AsyncMessageHeader.class)));
    tmpMap.put(_Fields.STORE, new org.apache.thrift.meta_data.FieldMetaData("store", org.apache.thrift.TFieldRequirementType.REQUIRED, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, Store.class)));
    tmpMap.put(_Fields.VERSIONS, new org.apache.thrift.meta_data.FieldMetaData("versions", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.ListMetaData(org.apache.thrift.protocol.TType.LIST, 
            new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, KeyedVersions.class))));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(SyncOfferMessage.class, metaDataMap);
  }

  public SyncOfferMessage() {
  }

  public SyncOfferMessage(
    AsyncMessageHeader header,
    Store store,
    java.util.List<KeyedVersions> versions)
  {
    this();
    this.header = header;
    this.store = store;
    this.versions = versions;
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public SyncOfferMessage(SyncOfferMessage other) {
    if (other.isSetHeader()) {
      this.header = new AsyncMessageHeader(other.header);
    }
    if (other.isSetStore()) {
      this.store = new Store(other.store);
    }
    if (other.isSetVersions()) {
      java.util.List<KeyedVersions> __this__versions = new java.util.ArrayList<KeyedVersions>(other.versions.size());
      for (KeyedVersions other_element : other.versions) {
        __this__versions.add(new KeyedVersions(other_element));
      }
      this.versions = __this__versions;
    }
  }

  public SyncOfferMessage deepCopy() {
    return new SyncOfferMessage(this);
  }

  @Override
  public void clear() {
    this.header = null;
    this.store = null;
    this.versions = null;
  }

  @org.apache.thrift.annotation.Nullable
  public AsyncMessageHeader getHeader() {
    return this.header;
  }

  public SyncOfferMessage setHeader(@org.apache.thrift.annotation.Nullable AsyncMessageHeader header) {
    this.header = header;
    return this;
  }

  public void unsetHeader() {
    this.header = null;
  }

  /** Returns true if field header is set (has been assigned a value) and false otherwise */
  public boolean isSetHeader() {
    return this.header != null;
  }

  public void setHeaderIsSet(boolean value) {
    if (!value) {
      this.header = null;
    }
  }

  @org.apache.thrift.annotation.Nullable
  public Store getStore() {
    return this.store;
  }

  public SyncOfferMessage setStore(@org.apache.thrift.annotation.Nullable Store store) {
    this.store = store;
    return this;
  }

  public void unsetStore() {
    this.store = null;
  }

  /** Returns true if field store is set (has been assigned a value) and false otherwise */
  public boolean isSetStore() {
    return this.store != null;
  }

  public void setStoreIsSet(boolean value) {
    if (!value) {
      this.store = null;
    }
  }

  public int getVersionsSize() {
    return (this.versions == null) ? 0 : this.versions.size();
  }

  @org.apache.thrift.annotation.Nullable
  public java.util.Iterator<KeyedVersions> getVersionsIterator() {
    return (this.versions == null) ? null : this.versions.iterator();
  }

  public void addToVersions(KeyedVersions elem) {
    if (this.versions == null) {
      this.versions = new java.util.ArrayList<KeyedVersions>();
    }
    this.versions.add(elem);
  }

  @org.apache.thrift.annotation.Nullable
  public java.util.List<KeyedVersions> getVersions() {
    return this.versions;
  }

  public SyncOfferMessage setVersions(@org.apache.thrift.annotation.Nullable java.util.List<KeyedVersions> versions) {
    this.versions = versions;
    return this;
  }

  public void unsetVersions() {
    this.versions = null;
  }

  /** Returns true if field versions is set (has been assigned a value) and false otherwise */
  public boolean isSetVersions() {
    return this.versions != null;
  }

  public void setVersionsIsSet(boolean value) {
    if (!value) {
      this.versions = null;
    }
  }

  public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable java.lang.Object value) {
    switch (field) {
    case HEADER:
      if (value == null) {
        unsetHeader();
      } else {
        setHeader((AsyncMessageHeader)value);
      }
      break;

    case STORE:
      if (value == null) {
        unsetStore();
      } else {
        setStore((Store)value);
      }
      break;

    case VERSIONS:
      if (value == null) {
        unsetVersions();
      } else {
        setVersions((java.util.List<KeyedVersions>)value);
      }
      break;

    }
  }

  @org.apache.thrift.annotation.Nullable
  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case HEADER:
      return getHeader();

    case STORE:
      return getStore();

    case VERSIONS:
      return getVersions();

    }
    throw new java.lang.IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new java.lang.IllegalArgumentException();
    }

    switch (field) {
    case HEADER:
      return isSetHeader();
    case STORE:
      return isSetStore();
    case VERSIONS:
      return isSetVersions();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that instanceof SyncOfferMessage)
      return this.equals((SyncOfferMessage)that);
    return false;
  }

  public boolean equals(SyncOfferMessage that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_header = true && this.isSetHeader();
    boolean that_present_header = true && that.isSetHeader();
    if (this_present_header || that_present_header) {
      if (!(this_present_header && that_present_header))
        return false;
      if (!this.header.equals(that.header))
        return false;
    }

    boolean this_present_store = true && this.isSetStore();
    boolean that_present_store = true && that.isSetStore();
    if (this_present_store || that_present_store) {
      if (!(this_present_store && that_present_store))
        return false;
      if (!this.store.equals(that.store))
        return false;
    }

    boolean this_present_versions = true && this.isSetVersions();
    boolean that_present_versions = true && that.isSetVersions();
    if (this_present_versions || that_present_versions) {
      if (!(this_present_versions && that_present_versions))
        return false;
      if (!this.versions.equals(that.versions))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetHeader()) ? 131071 : 524287);
    if (isSetHeader())
      hashCode = hashCode * 8191 + header.hashCode();

    hashCode = hashCode * 8191 + ((isSetStore()) ? 131071 : 524287);
    if (isSetStore())
      hashCode = hashCode * 8191 + store.hashCode();

    hashCode = hashCode * 8191 + ((isSetVersions()) ? 131071 : 524287);
    if (isSetVersions())
      hashCode = hashCode * 8191 + versions.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(SyncOfferMessage other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.compare(isSetHeader(), other.isSetHeader());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetHeader()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.header, other.header);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetStore(), other.isSetStore());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetStore()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.store, other.store);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.compare(isSetVersions(), other.isSetVersions());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetVersions()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.versions, other.versions);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("SyncOfferMessage(");
    boolean first = true;

    sb.append("header:");
    if (this.header == null) {
      sb.append("null");
    } else {
      sb.append(this.header);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("store:");
    if (this.store == null) {
      sb.append("null");
    } else {
      sb.append(this.store);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("versions:");
    if (this.versions == null) {
      sb.append("null");
    } else {
      sb.append(this.versions);
    }
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    if (header == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'header' was not present! Struct: " + toString());
    }
    if (store == null) {
      throw new org.apache.thrift.protocol.TProtocolException("Required field 'store' was not present! Struct: " + toString());
    }
    // check for sub-struct validity
    if (header != null) {
      header.validate();
    }
    if (store != null) {
      store.validate();
    }
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
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class SyncOfferMessageStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public SyncOfferMessageStandardScheme getScheme() {
      return new SyncOfferMessageStandardScheme();
    }
  }

  private static class SyncOfferMessageStandardScheme extends org.apache.thrift.scheme.StandardScheme<SyncOfferMessage> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, SyncOfferMessage struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // HEADER
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.header = new AsyncMessageHeader();
              struct.header.read(iprot);
              struct.setHeaderIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // STORE
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.store = new Store();
              struct.store.read(iprot);
              struct.setStoreIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // VERSIONS
            if (schemeField.type == org.apache.thrift.protocol.TType.LIST) {
              {
                org.apache.thrift.protocol.TList _list40 = iprot.readListBegin();
                struct.versions = new java.util.ArrayList<KeyedVersions>(_list40.size);
                @org.apache.thrift.annotation.Nullable KeyedVersions _elem41;
                for (int _i42 = 0; _i42 < _list40.size; ++_i42)
                {
                  _elem41 = new KeyedVersions();
                  _elem41.read(iprot);
                  struct.versions.add(_elem41);
                }
                iprot.readListEnd();
              }
              struct.setVersionsIsSet(true);
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

    public void write(org.apache.thrift.protocol.TProtocol oprot, SyncOfferMessage struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.header != null) {
        oprot.writeFieldBegin(HEADER_FIELD_DESC);
        struct.header.write(oprot);
        oprot.writeFieldEnd();
      }
      if (struct.store != null) {
        oprot.writeFieldBegin(STORE_FIELD_DESC);
        struct.store.write(oprot);
        oprot.writeFieldEnd();
      }
      if (struct.versions != null) {
        oprot.writeFieldBegin(VERSIONS_FIELD_DESC);
        {
          oprot.writeListBegin(new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, struct.versions.size()));
          for (KeyedVersions _iter43 : struct.versions)
          {
            _iter43.write(oprot);
          }
          oprot.writeListEnd();
        }
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class SyncOfferMessageTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public SyncOfferMessageTupleScheme getScheme() {
      return new SyncOfferMessageTupleScheme();
    }
  }

  private static class SyncOfferMessageTupleScheme extends org.apache.thrift.scheme.TupleScheme<SyncOfferMessage> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, SyncOfferMessage struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.header.write(oprot);
      struct.store.write(oprot);
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetVersions()) {
        optionals.set(0);
      }
      oprot.writeBitSet(optionals, 1);
      if (struct.isSetVersions()) {
        {
          oprot.writeI32(struct.versions.size());
          for (KeyedVersions _iter44 : struct.versions)
          {
            _iter44.write(oprot);
          }
        }
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, SyncOfferMessage struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      struct.header = new AsyncMessageHeader();
      struct.header.read(iprot);
      struct.setHeaderIsSet(true);
      struct.store = new Store();
      struct.store.read(iprot);
      struct.setStoreIsSet(true);
      java.util.BitSet incoming = iprot.readBitSet(1);
      if (incoming.get(0)) {
        {
          org.apache.thrift.protocol.TList _list45 = iprot.readListBegin(org.apache.thrift.protocol.TType.STRUCT);
          struct.versions = new java.util.ArrayList<KeyedVersions>(_list45.size);
          @org.apache.thrift.annotation.Nullable KeyedVersions _elem46;
          for (int _i47 = 0; _i47 < _list45.size; ++_i47)
          {
            _elem46 = new KeyedVersions();
            _elem46.read(iprot);
            struct.versions.add(_elem46);
          }
        }
        struct.setVersionsIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

