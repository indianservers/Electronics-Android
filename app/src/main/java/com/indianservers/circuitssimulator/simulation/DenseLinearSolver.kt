package com.indianservers.circuitssimulator.simulation

import kotlin.math.abs

/** Dense pivoted Gaussian elimination for small circuits; replaceable by sparse solver later. */
object DenseLinearSolver {
    fun solve(matrix:Array<DoubleArray>,vector:DoubleArray):DoubleArray? {
        val n=vector.size
        val a=Array(n) { matrix[it].copyOf() }
        val b=vector.copyOf()
        for(col in 0 until n) {
            val pivot=(col until n).maxBy { abs(a[it][col]) }
            if(abs(a[pivot][col])<1e-14) return null
            val row=a[col];a[col]=a[pivot];a[pivot]=row
            val value=b[col];b[col]=b[pivot];b[pivot]=value
            for(r in col+1 until n) {
                val factor=a[r][col]/a[col][col]
                for(c in col until n) a[r][c]-=factor*a[col][c]
                b[r]-=factor*b[col]
            }
        }
        val result=DoubleArray(n)
        for(r in n-1 downTo 0) {
            var remainder=b[r]
            for(c in r+1 until n) remainder-=a[r][c]*result[c]
            result[r]=remainder/a[r][r]
        }
        return result.takeIf { it.all(Double::isFinite) }
    }
}
