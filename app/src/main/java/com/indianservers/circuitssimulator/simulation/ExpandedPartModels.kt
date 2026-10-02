package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.PlacedComponent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Linearized branch current is g * voltage + ieq, positive from the first pin to the second. */
internal data class BranchModel(val conductance:Double,val ieq:Double) {
    fun current(voltage:Double)=conductance*voltage+ieq
}

internal fun zenerAt(part:PlacedComponent,voltage:Double):BranchModel {
    val vz=part.value("breakdownVoltage")
    val r=part.value("dynamicResistance")
    return when {
        voltage>=0.7 -> BranchModel(1.0/r,-0.7/r)
        voltage<=-vz -> BranchModel(1.0/r,vz/r)
        else -> BranchModel(1e-9,0.0)
    }
}

internal fun ledBranchAt(voltage:Double,forwardVoltage:Double,resistance:Double):BranchModel =
    if(voltage>forwardVoltage) BranchModel(1.0/resistance,-forwardVoltage/resistance)
    else BranchModel(1e-9,0.0)

internal fun stampBranch(matrix:Array<DoubleArray>,rhs:DoubleArray,a:Int,b:Int,model:BranchModel) {
    val g=model.conductance
    if(a>=0) { matrix[a][a]+=g;rhs[a]-=model.ieq }
    if(b>=0) { matrix[b][b]+=g;rhs[b]+=model.ieq }
    if(a>=0 && b>=0) { matrix[a][b]-=g;matrix[b][a]-=g }
}

/** i(out+) = gain * (V(control+) - V(control-)). */
internal fun stampVccs(matrix:Array<DoubleArray>,pins:IntArray,gain:Double) {
    val (cp,cm,op,om)=pins
    if(op>=0) {
        if(cp>=0) matrix[op][cp]+=gain
        if(cm>=0) matrix[op][cm]-=gain
    }
    if(om>=0) {
        if(cp>=0) matrix[om][cp]-=gain
        if(cm>=0) matrix[om][cm]+=gain
    }
}

internal fun stampVcvs(matrix:Array<DoubleArray>,pins:IntArray,row:Int,gain:Double) {
    val (cp,cm,op,om)=pins
    if(op>=0) { matrix[op][row]+=1.0;matrix[row][op]+=1.0 }
    if(om>=0) { matrix[om][row]-=1.0;matrix[row][om]-=1.0 }
    if(cp>=0) matrix[row][cp]-=gain
    if(cm>=0) matrix[row][cm]+=gain
}

internal fun stampCurrentControlled(matrix:Array<DoubleArray>,pins:IntArray,
    senseRow:Int,outputRow:Int?,gain:Double) {
    val (cp,cm,op,om)=pins
    if(cp>=0) { matrix[cp][senseRow]+=1.0;matrix[senseRow][cp]+=1.0 }
    if(cm>=0) { matrix[cm][senseRow]-=1.0;matrix[senseRow][cm]-=1.0 }
    if(outputRow==null) {
        if(op>=0) matrix[op][senseRow]+=gain
        if(om>=0) matrix[om][senseRow]-=gain
    } else {
        if(op>=0) { matrix[op][outputRow]+=1.0;matrix[outputRow][op]+=1.0 }
        if(om>=0) { matrix[om][outputRow]-=1.0;matrix[outputRow][om]-=1.0 }
        matrix[outputRow][senseRow]-=gain
    }
}

internal fun stampTransformer(matrix:Array<DoubleArray>,rhs:DoubleArray,pins:IntArray,
    part:PlacedComponent,dt:Double,priorPrimary:Double,priorSecondary:Double) {
    val lp=part.value("primaryInductance")
    val ratio=part.value("turnsRatio")
    val ls=lp*ratio*ratio
    val m=part.value("coupling")*sqrt(lp*ls)
    val determinant=max(lp*ls-m*m,1e-18)
    val g11=dt*ls/determinant;val g22=dt*lp/determinant;val g12=-dt*m/determinant
    val signs=intArrayOf(1,-1,1,-1)
    for(i in 0..3) {
        val row=pins[i]
        if(row<0) continue
        rhs[row]-=signs[i]*(if(i<2) priorPrimary else priorSecondary)
        for(j in 0..3) {
            val col=pins[j]
            if(col>=0) matrix[row][col]+=signs[i]*signs[j]*when {
                i<2 && j<2 -> g11
                i>=2 && j>=2 -> g22
                else -> g12
            }
        }
    }
}

internal fun transformerCurrents(part:PlacedComponent,dt:Double,primaryVoltage:Double,
    secondaryVoltage:Double,priorPrimary:Double,priorSecondary:Double):Pair<Double,Double> {
    val lp=part.value("primaryInductance")
    val ls=lp*part.value("turnsRatio")*part.value("turnsRatio")
    val m=part.value("coupling")*sqrt(lp*ls)
    val determinant=max(lp*ls-m*m,1e-18)
    return (priorPrimary+dt*(ls*primaryVoltage-m*secondaryVoltage)/determinant) to
        (priorSecondary+dt*(lp*secondaryVoltage-m*primaryVoltage)/determinant)
}

internal fun motorCompanion(part:PlacedComponent,dt:Double,priorCurrent:Double,omega:Double):BranchModel {
    val inductive=part.value("inductance")/dt
    val g=1.0/(part.value("resistance")+inductive)
    return BranchModel(g,g*(inductive*priorCurrent-part.value("motorConstant")*omega))
}

internal fun isExpandedMultiport(part:PlacedComponent)=part.kind in setOf(Kind.VCCS,Kind.VCVS,
    Kind.CCCS,Kind.CCVS,
    Kind.TRANSFORMER,Kind.RELAY,Kind.RGB_LED,Kind.SEVEN_SEGMENT,Kind.SPDT_SWITCH)
