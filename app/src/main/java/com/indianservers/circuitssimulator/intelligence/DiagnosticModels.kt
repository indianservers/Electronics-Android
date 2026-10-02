package com.indianservers.circuitssimulator.intelligence

import com.indianservers.circuitssimulator.domain.Circuit
import com.indianservers.circuitssimulator.domain.TerminalRef
import com.indianservers.circuitssimulator.simulation.DcResult
import com.indianservers.circuitssimulator.simulation.MeterMode

enum class IssueSeverity { CRITICAL, ERROR, WARNING, SUGGESTION, INSIGHT }
enum class IssueCategory { CONNECTIVITY, POWER, POLARITY, COMPONENT_STRESS, MEASUREMENT,
    LOGIC, GROUNDING, BOARD_COMPATIBILITY, GPIO, ANALOG, DIGITAL, CONFIGURATION, SIMULATION, LEARNING }
enum class ExplanationMode { BEGINNER, STANDARD, ADVANCED }
enum class RatingSource { MANUFACTURER, GENERIC_MODEL, EDUCATIONAL_DEFAULT, CIRCUIT_SETTING }

data class DiagnosticEvidence(val label:String,val value:String,val source:RatingSource?=null)

/** Only changes with an unambiguous target are offered as actions. The UI previews them first. */
sealed interface SmartFix {
    val label:String
    val explanation:String
    data class RemoveWire(val wireId:String,override val label:String,override val explanation:String):SmartFix
    data class ReverseLed(val ledId:String,override val label:String,override val explanation:String):SmartFix
    data class AddGround(val sourceId:String,override val label:String,override val explanation:String):SmartFix
    data class SetValue(val componentId:String,val key:String,val value:Double,
                        override val label:String,override val explanation:String):SmartFix
    data class InsertLedResistor(val wireId:String,val ohms:Double,
                                 override val label:String,override val explanation:String):SmartFix
}

data class IntelligenceIssue(
    val id:String,val ruleId:String,val severity:IssueSeverity,val category:IssueCategory,
    val title:String,val summary:String,val explanation:String,
    val componentIds:Set<String> = emptySet(),val pins:Set<TerminalRef> = emptySet(),
    val wireIds:Set<String> = emptySet(),val evidence:List<DiagnosticEvidence> = emptyList(),
    val fixes:List<SmartFix> = emptyList(),val confidence:Float=1f,
    val modelNote:String?=null
)

data class CircuitInsight(val id:String,val title:String,val explanation:String,
                          val componentIds:Set<String> = emptySet(),val evidence:List<DiagnosticEvidence> = emptyList())

data class IntelligenceReport(val issues:List<IntelligenceIssue>,val insights:List<CircuitInsight>,
                              val summary:String,val durationMillis:Long) {
    val urgent get()=issues.count { it.severity==IssueSeverity.CRITICAL || it.severity==IssueSeverity.ERROR }
    val warnings get()=issues.count { it.severity==IssueSeverity.WARNING }
    val suggestions get()=issues.count { it.severity==IssueSeverity.SUGGESTION }
    val status:String get()=when {
        issues.any { it.severity==IssueSeverity.CRITICAL } -> "Critical issue"
        urgent>0 -> "Errors found"
        warnings>0 || suggestions>0 -> "Needs attention"
        else -> "No obvious problems found"
    }
}

data class IntelligenceInput(
    val circuit:Circuit,val result:DcResult?=null,val running:Boolean=false,
    val meterMode:MeterMode?=null,val meterPlus:TerminalRef?=null,
    val meterCommon:TerminalRef?=null,val meterCurrentWireId:String?=null,
    val scopeProbes:List<TerminalRef> = emptyList(),val mode:ExplanationMode=ExplanationMode.STANDARD,
    val firmware:FirmwareIntel?=null
)

data class IssueTransition(val issue:IntelligenceIssue,val resolved:Boolean,val atMillis:Long)
