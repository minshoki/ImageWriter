package com.minshoki.image_compress

class ImageCompression {
    internal val constraints: MutableList<ICompressionConstraint> = mutableListOf()

    fun constraint(constraint: ICompressionConstraint) {
        constraints.add(constraint)
    }
}