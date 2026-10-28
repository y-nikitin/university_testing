package ua.course.delivery;
public record Parcel(long id,String code,int weightGrams,long declaredValue,boolean fragile) {}
