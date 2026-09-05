package com.khaled.move.navigation.foot.location

import android.os.Parcel
import android.os.Parcelable

data class Building(
    val id: String,
    val name: String,
    val address: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val type: String = "building"
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readDouble(),
        parcel.readDouble(),
        parcel.readString() ?: "building"
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(address)
        parcel.writeString(description)
        parcel.writeDouble(latitude)
        parcel.writeDouble(longitude)
        parcel.writeString(type)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Building> {
        override fun createFromParcel(parcel: Parcel): Building {
            return Building(parcel)
        }

        override fun newArray(size: Int): Array<Building?> {
            return arrayOfNulls(size)
        }
    }
}
