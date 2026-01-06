package com.chargebee.sdk.java.javanext.datatype;

public sealed interface FieldType
    permits StringType,
        IntegerType,
        LongType,
        DoubleType,
        NumberType,
        BooleanType,
        TimestampType,
        BigDecimalType,
        EnumType,
        ListType,
        ObjectType {

  String display();

  default String javaTypeName() {
    return display();
  }
}
