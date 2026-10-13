package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.simulation.DenseLinearSolver
import org.junit.Assert.*
import org.junit.Test

class DenseLinearSolverTest {
    @Test fun sparseConnectedSystemWithFloatingPinLeakageHasSmallResidual() {
        val size=100
        val matrix=Array(size) { DoubleArray(size) }
        // Forty connected circuit nodes plus sixty independent floating MCU pins.
        repeat(size) { matrix[it][it]=if(it<40) .02 else 1e-9 }
        repeat(39) { matrix[it][it+1]=-.003;matrix[it+1][it]=-.003 }
        val expected=DoubleArray(size) { (it%7-3).toDouble() }
        val rhs=DoubleArray(size) { row -> matrix[row].indices.sumOf { matrix[row][it]*expected[it] } }
        val solution=DenseLinearSolver.solve(matrix,rhs)!!
        solution.indices.forEach { assertEquals(expected[it],solution[it],1e-10) }
        matrix.indices.forEach { row ->
            assertEquals(rhs[row],solution.indices.sumOf { matrix[row][it]*solution[it] },1e-12)
        }
    }
    @Test fun zeroLeadingDiagonalStillPivotsForVoltageSourceConstraint() {
        val matrix=arrayOf(doubleArrayOf(0.0,1.0,0.0),doubleArrayOf(1.0,.01,-.01),doubleArrayOf(0.0,-.01,.02))
        val solution=DenseLinearSolver.solve(matrix,doubleArrayOf(5.0,.025,0.0))!!
        assertArrayEquals(doubleArrayOf(0.0,5.0,2.5),solution,1e-12)
    }
    @Test fun disconnectedSingularSystemIsRejected() {
        assertNull(DenseLinearSolver.solve(arrayOf(doubleArrayOf(1.0,0.0),doubleArrayOf(0.0,0.0)),doubleArrayOf(1.0,0.0)))
    }
}
