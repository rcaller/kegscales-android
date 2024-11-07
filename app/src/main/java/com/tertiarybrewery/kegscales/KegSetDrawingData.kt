package com.tertiarybrewery.kegscales

class KegSetDrawingData(kegCount: Int, dw: Int, dh: Int) {
    var indent = 10
    val boxWidth = ((dw - (2*indent))/kegCount)
    val kegHeight = dh - 100
    val kegWidth = kegHeight/3
    val percentHeight = (kegHeight.toFloat()-25F)/100
    var kegs = arrayListOf<KegDrawingData>()
    init {
        for (kegNumber in 0..kegCount-1) {
            val keg = KegDrawingData()
            keg.number=kegNumber
            keg.x = indent + (boxWidth * kegNumber)
            keg.textX = indent + (boxWidth * kegNumber) + kegWidth + 25
            kegs.add(keg)
        }
    }

    fun coordinatesInKeg(x: Int, y: Int): Int? {
        for (keg in kegs) {
            if (x>(keg.x-50)
                && x < (keg.x+kegWidth+50)) {
                return keg.number
            }
        }
        return -1
    }

}
